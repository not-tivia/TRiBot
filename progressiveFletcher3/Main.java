package scripts.progressiveFletcher;

import org.tribot.script.sdk.Bank;
import org.tribot.script.sdk.ChatScreen;
import org.tribot.script.sdk.Inventory;
import org.tribot.script.sdk.Log;
import org.tribot.script.sdk.MyPlayer;
import org.tribot.script.sdk.Skill;
import org.tribot.script.sdk.Waiting;
import org.tribot.script.sdk.Widgets;
import org.tribot.script.sdk.antiban.Antiban;
import org.tribot.script.sdk.input.Keyboard;
import org.tribot.script.sdk.query.Query;
import org.tribot.script.sdk.script.TribotScript;
import org.tribot.script.sdk.script.TribotScriptManifest;
import org.tribot.script.sdk.types.InventoryItem;
import org.tribot.script.sdk.types.Widget;
import org.tribot.script.sdk.util.ScriptSettings;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/*
 * CHANGELOG
 *   1.0.4 (2026-05-14) - Drop dynamic last-visible widget search entirely.
 *                        Hardcoded fletchChildForLevel: <5 -> 270.15 arrow shafts,
 *                        <10 -> 270.16 shortbow, else 270.17 longbow.
 *                        Specific-mode args still use widgetChildOverride which routes
 *                        to the same fixed indices via parseArgs.
 *   1.0.3 (2026-05-14) - Cap the last-visible item-slot search at 270.25 (superseded).
 *                        Widget 270 has many non-item children at indices well past
 *                        the item slots — script was finding e.g. 270.34 and clicking
 *                        it, which never started fletching.
 *   1.0.2 (2026-05-14) - craft() now waits for the make-X dialog to actually open
 *                        after useOn(). Without this, the next tick fired ~350ms later
 *                        re-issued useOn before the dialog had a chance to appear,
 *                        causing knife->log to fire twice in a row and (in some cases)
 *                        cancelling the in-progress dialog open so fletching never
 *                        started. The crafter already had this wait; the fletcher
 *                        was missing it.
 *   1.0.1 (2026-05-14) - Fletching make-X widget index mapping corrected:
 *                        270.15 = arrow shafts, 270.16 = shortbow, 270.17 = longbow.
 *                        Specific-mode "shortbow" was clicking 270.15 (arrow shafts) - now 270.16.
 *                        Specific-mode "longbow" now explicitly maps to 270.17 (was relying on
 *                        last-visible fallback). "arrows"/"arrow shafts" routes to 270.15.
 *   1.0.0 (2026-05-14) - Initial modern rewrite of progressiveFletcher2 using current SDK.
 *                        State machine + widget-based make-X (last-visible child of 270 from idx 15).
 *                        Four modes via args: progressive cut, progressive string,
 *                        specific cut ("yew longbow"), specific string ("string yew longbow").
 *                        Selective deposit. Bank.withdraw(tool, 1) to avoid stack overflow.
 *                        Stuck detection (5min, antiban-aware).
 *                        Escape-key recovery for make-X stuck with no clickable child.
 *                        Stringing-pin: chosen unstrung bow does not reshuffle mid-batch.
 *                        Swing settings dialog with JSON persistence via ScriptSettings.
 *
 * KNOWN-FIX
 *   - SDK methods take String[] not varargs. `Bank.depositAllExcept(NEEDLE, THREAD)`
 *     fails to compile. Wrap in arrays (FLAX_A, KNIFE_A, TOOLS etc.).
 *   - `Bank.depositAllExcept` does not exist in 1.0.71. Iterate inventory and use
 *     `Bank.depositAll(name)` for non-keep items (see depositNonKeepItems).
 *   - Level brackets must use next-tier SHORTBOW unlock levels (20, 35, 50, 65, 80).
 *     Longbow unlock levels (25, 40, 55, 70, 85) are wrong - would miss 5 levels of
 *     better-tier shortbow. SDK auto-upgrades shortbow->longbow via last-visible.
 *   - Make-X dialog open but no clickable item => loop forever.
 *     Fix: Keyboard.pressEscape() to bail out and retry next tick.
 *
 * OPEN
 *   - Item name "Bow string" may actually be "Bowstring" (no space) in current OSRS.
 *     Verify in-game and adjust STRING constant if needed.
 *   - No walking fallback if banker is out of range; Bank.ensureOpen() will loop.
 *   - Specific "string shortbow" / "string longbow" without a wood would currently
 *     produce material name "Shortbow logs" via parseArgs - edge case.
 */
@TribotScriptManifest(
        name = "ProgressiveFletcher",
        author = "adamhackz (rewrite)",
        category = "Fletching",
        description = "Fletches bows. Args: '' or 'progressive' for level-based log cutting; 'string' or 'string <wood> <type>' for stringing; '<wood> shortbow|longbow' for specific cutting."
)
public class Main implements TribotScript {

    private static final String KNIFE  = "Knife";
    private static final String STRING = "Bow string";

    private volatile boolean running = true;

    private String tool = KNIFE;
    private Supplier<String> material = () -> "Logs";
    private int widgetChildOverride = -1; // -1 = last visible
    private String pinnedUnstrung = null;
    private long lastProgressMs = System.currentTimeMillis();
    // Long enough to survive TRiBot AI antiban breaks (which can last several minutes).
    private static final long STUCK_TIMEOUT_MS = 5 * 60_000;

    @Override
    public void execute(final String args) {
        String effective = (args == null || args.trim().isEmpty()) ? showSettingsDialog() : args;
        if (effective == null) { Log.info("Cancelled. Exiting."); return; }
        parseArgs(effective);
        Log.info("ProgressiveFletcher started. args='" + effective + "' tool=" + tool + " widgetOverride=" + widgetChildOverride);
        Antiban.setScriptAiAntibanEnabled(true);
        while (running) {
            try { tick(); } catch (Exception e) { Log.error("Tick failed: " + e.getMessage()); }
            Waiting.waitNormal(350, 120);
        }
        Log.info("ProgressiveFletcher stopping.");
    }

    private static class FletcherSettings {
        public String mode = "progressive"; // progressive | string | cut | string_specific
        public String wood = "yew";
        public String bowType = "longbow";
    }

    private String showSettingsDialog() {
        final FletcherSettings cur = loadSettings();
        final String[] result = { null };
        try {
            SwingUtilities.invokeAndWait(() -> {
                JDialog dlg = new JDialog((Frame) null, "ProgressiveFletcher", true);
                dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

                JRadioButton rbProgCut = new JRadioButton("Progressive cutting (by Fletching level)");
                JRadioButton rbProgStr = new JRadioButton("Progressive stringing (highest unstrung in bank)");
                JRadioButton rbSpecCut = new JRadioButton("Specific cutting");
                JRadioButton rbSpecStr = new JRadioButton("Specific stringing");
                ButtonGroup modeGroup = new ButtonGroup();
                modeGroup.add(rbProgCut); modeGroup.add(rbProgStr);
                modeGroup.add(rbSpecCut); modeGroup.add(rbSpecStr);
                switch (cur.mode) {
                    case "string":          rbProgStr.setSelected(true); break;
                    case "cut":             rbSpecCut.setSelected(true); break;
                    case "string_specific": rbSpecStr.setSelected(true); break;
                    default:                rbProgCut.setSelected(true);
                }

                JComboBox<String> woodBox = new JComboBox<>(new String[]{
                        "Logs", "Oak", "Willow", "Maple", "Yew", "Magic" });
                woodBox.setSelectedItem(capitalize(cur.wood));

                JRadioButton rbShort = new JRadioButton("Shortbow");
                JRadioButton rbLong  = new JRadioButton("Longbow");
                ButtonGroup typeGroup = new ButtonGroup();
                typeGroup.add(rbShort); typeGroup.add(rbLong);
                if ("shortbow".equals(cur.bowType)) rbShort.setSelected(true); else rbLong.setSelected(true);

                Runnable updateEnabled = () -> {
                    boolean specific = rbSpecCut.isSelected() || rbSpecStr.isSelected();
                    woodBox.setEnabled(specific);
                    rbShort.setEnabled(specific); rbLong.setEnabled(specific);
                };
                rbProgCut.addActionListener(e -> updateEnabled.run());
                rbProgStr.addActionListener(e -> updateEnabled.run());
                rbSpecCut.addActionListener(e -> updateEnabled.run());
                rbSpecStr.addActionListener(e -> updateEnabled.run());
                updateEnabled.run();

                JPanel modePanel = new JPanel(new GridLayout(0, 1));
                modePanel.setBorder(BorderFactory.createTitledBorder("Mode"));
                modePanel.add(rbProgCut); modePanel.add(rbProgStr);
                modePanel.add(rbSpecCut); modePanel.add(rbSpecStr);

                JPanel detailPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                detailPanel.setBorder(BorderFactory.createTitledBorder("Specific mode"));
                detailPanel.add(new JLabel("Wood:"));
                detailPanel.add(woodBox);
                detailPanel.add(rbShort);
                detailPanel.add(rbLong);

                JButton ok = new JButton("Start");
                JButton cancel = new JButton("Cancel");
                ok.addActionListener(e -> {
                    if (rbProgCut.isSelected()) {
                        cur.mode = "progressive"; result[0] = "progressive";
                    } else if (rbProgStr.isSelected()) {
                        cur.mode = "string"; result[0] = "string";
                    } else {
                        String wood = ((String) woodBox.getSelectedItem()).toLowerCase();
                        String type = rbShort.isSelected() ? "shortbow" : "longbow";
                        cur.wood = wood; cur.bowType = type;
                        if (rbSpecCut.isSelected()) {
                            cur.mode = "cut";
                            result[0] = wood.equals("logs") ? type : wood + " " + type;
                        } else {
                            cur.mode = "string_specific";
                            result[0] = "string " + wood + " " + type;
                        }
                    }
                    saveSettings(cur);
                    dlg.dispose();
                });
                cancel.addActionListener(e -> dlg.dispose());

                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                buttonPanel.add(ok); buttonPanel.add(cancel);

                dlg.setLayout(new BorderLayout());
                dlg.add(modePanel, BorderLayout.NORTH);
                dlg.add(detailPanel, BorderLayout.CENTER);
                dlg.add(buttonPanel, BorderLayout.SOUTH);
                dlg.pack();
                dlg.setLocationRelativeTo(null);
                dlg.setVisible(true);
            });
        } catch (Exception e) {
            Log.error("Settings dialog failed: " + e.getMessage());
            return null;
        }
        return result[0];
    }

    private FletcherSettings loadSettings() {
        try {
            return ScriptSettings.getDefault()
                    .load("progressive_fletcher", FletcherSettings.class)
                    .orElseGet(FletcherSettings::new);
        } catch (Exception e) {
            return new FletcherSettings();
        }
    }

    private void saveSettings(FletcherSettings s) {
        try { ScriptSettings.getDefault().save("progressive_fletcher", s); }
        catch (Exception e) { Log.warn("Failed to save settings: " + e.getMessage()); }
    }

    private void parseArgs(String args) {
        String a = (args == null ? "" : args.toLowerCase().trim());
        if (a.isEmpty() || a.equals("progressive")) {
            tool = KNIFE;
            material = () -> logForLevel(Skill.FLETCHING.getActualLevel());
        } else if (a.equals("string")) {
            tool = STRING;
            material = this::pickOrPinnedUnstrung;
        } else if (a.startsWith("string ")) {
            String[] p = a.substring(7).split("\\s+", 2);
            final String mat = capitalize(p[0]) + " " + p[1] + " (u)";
            tool = STRING;
            material = () -> mat;
        } else {
            String[] p = a.split("\\s+", 2);
            String matName;
            String typeWord;
            if (p[0].equals("shortbow") || p[0].equals("longbow")) {
                matName = "Logs";
                typeWord = p[0];
            } else {
                matName = capitalize(p[0]) + " logs";
                typeWord = (p.length > 1) ? p[1] : "";
            }
            final String log = matName;
            tool = KNIFE;
            material = () -> log;
            if (typeWord.contains("short")) widgetChildOverride = 16; // shortbow @ 270.16
            else if (typeWord.contains("long")) widgetChildOverride = 17; // longbow @ 270.17
            else if (typeWord.contains("arrow")) widgetChildOverride = 15; // arrow shafts @ 270.15
        }
    }

    private String pickOrPinnedUnstrung() {
        if (pinnedUnstrung != null) return pinnedUnstrung;
        pinnedUnstrung = pickUnstrungInBank();
        if (pinnedUnstrung != null) Log.info("Pinned unstrung: " + pinnedUnstrung);
        return pinnedUnstrung;
    }

    private void tick() {
        boolean makeXOpen = Widgets.get(new int[]{ 270, 14 }).map(Widget::isVisible).orElse(false);
        if (MyPlayer.getAnimation() != -1 || Bank.isOpen() || makeXOpen || ChatScreen.isOpen()) {
            lastProgressMs = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - lastProgressMs > STUCK_TIMEOUT_MS) {
            Log.error("No progress for " + (STUCK_TIMEOUT_MS / 1000) + "s. Stopping.");
            running = false;
            return;
        }

        if (ChatScreen.isClickContinueOpen()) { ChatScreen.clickContinue(); return; }

        final String mat = material.get();
        if (mat == null) { Log.warn("No material available. Stopping."); running = false; return; }
        final String[] toolA = { tool };
        final String[] matA  = { mat };

        if (Bank.isOpen()) {
            if (Inventory.contains(toolA) && Inventory.contains(matA)) {
                Bank.close();
                return;
            }
            if (!Inventory.contains(toolA) && !Bank.contains(toolA)) {
                Log.warn("Bank missing " + tool + ". Stopping."); running = false; return;
            }
            if (!Bank.contains(matA)) { Log.info("Out of " + mat + ". Stopping."); running = false; return; }
            depositNonKeepItems(mat);
            if (!Inventory.contains(toolA)) {
                Bank.withdraw(tool, 1);
                Waiting.waitUntil(1500, () -> Inventory.contains(toolA));
                if (!Inventory.contains(toolA)) return; // retry on next tick, do NOT fill inv with logs first
            }
            if (!Inventory.contains(matA)) {
                Bank.withdrawAll(mat);
                Waiting.waitUntil(2500, () -> Inventory.contains(matA));
            }
            return;
        }

        if (Inventory.contains(toolA) && Inventory.contains(matA)) { craft(mat, toolA, matA); return; }

        Bank.ensureOpen();
        Waiting.waitUntil(3000, Bank::isOpen);
    }

    private void depositNonKeepItems(final String mat) {
        while (true) {
            Optional<InventoryItem> next = Query.inventory().stream()
                    .filter(item -> {
                        String n = item.getName();
                        return !tool.equals(n) && !mat.equals(n);
                    })
                    .findFirst();
            if (!next.isPresent()) return;
            final String name = next.get().getName();
            if (!Bank.depositAll(name)) return;
            final String[] nameA = { name };
            Waiting.waitUntil(1500, () -> !Inventory.contains(nameA));
        }
    }

    private void craft(final String mat, final String[] toolA, final String[] matA) {
        if (MyPlayer.getAnimation() != -1) { Waiting.waitNormal(900, 250); return; }

        Optional<Widget> labels = Widgets.get(new int[]{ 270, 14 });
        if (!labels.isPresent() || !labels.get().isVisible()) {
            Optional<InventoryItem> t = Query.inventory().nameEquals(toolA).findFirst();
            Optional<InventoryItem> m = Query.inventory().nameEquals(matA).findFirst();
            if (!t.isPresent() || !m.isPresent()) return;
            Log.info("Using " + tool + " on " + mat);
            if (t.get().useOn(m.get())) {
                // Wait for make-X to actually open before falling through to next tick;
                // otherwise we'd useOn again and clobber the dialog.
                Waiting.waitUntil(3000, () ->
                        Widgets.get(new int[]{ 270, 14 }).map(Widget::isVisible).orElse(false));
            }
            return;
        }

        // Fletching make-X is fixed: 270.15 arrow shafts, 270.16 shortbow, 270.17 longbow.
        // Specific-mode args set widgetChildOverride directly; progressive picks by level.
        int childIdx = (widgetChildOverride > 0) ? widgetChildOverride : fletchChildForLevel();
        Optional<Widget> target = Widgets.get(new int[]{ 270, childIdx });
        if (!target.isPresent() || !target.get().isVisible()) {
            Log.warn("Make-X widget 270." + childIdx + " not visible. Pressing Escape.");
            Keyboard.pressEscape();
            Waiting.waitNormal(400, 100);
            return;
        }
        Log.info("Clicking 270." + childIdx + " (" + mat + ")");
        if (target.get().click()) {
            Waiting.waitUntil(4000, () -> MyPlayer.getAnimation() != -1);
        }
    }

    private int fletchChildForLevel() {
        int level = Skill.FLETCHING.getActualLevel();
        if (level < 5)  return 15; // Arrow shafts
        if (level < 10) return 16; // Shortbow
        return 17;                 // Longbow
    }

    private String logForLevel(int level) {
        // Switch tiers as soon as the NEXT tier's shortbow unlocks. Within each
        // tier the make-X auto-upgrades shortbow -> longbow via last-visible.
        if (level < 20) return "Logs";        // oak shortbow at 20
        if (level < 35) return "Oak logs";    // willow shortbow at 35
        if (level < 50) return "Willow logs"; // maple shortbow at 50
        if (level < 65) return "Maple logs";  // yew shortbow at 65
        if (level < 80) return "Yew logs";    // magic shortbow at 80
        return "Magic logs";
    }

    private String pickUnstrungInBank() {
        String[] order = { "Magic longbow (u)", "Magic shortbow (u)", "Yew longbow (u)", "Yew shortbow (u)",
                "Maple longbow (u)", "Maple shortbow (u)", "Willow longbow (u)", "Willow shortbow (u)",
                "Oak longbow (u)", "Oak shortbow (u)", "Longbow (u)", "Shortbow (u)" };
        for (String s : order) if (Bank.contains(new String[]{ s })) return s;
        return null;
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
