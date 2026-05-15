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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/*
 * CHANGELOG
 *   1.1.0 (2026-05-14) - Multi-profile save/load. GUI gains Profile row at the top
 *                        (dropdown + Load / Save as... / Delete). Profiles namespaced
 *                        under "progressive_fletcher_<name>" in ScriptSettings.
 *                        execute(args): tries args as profile name first; falls back
 *                        to existing parseArgs format ("progressive", "yew longbow",
 *                        "string yew longbow", etc.) if not a saved profile.
 *   1.0.5 (2026-05-14) - fletchChildForLevel now tracks per-tier shortbow/longbow
 *                        unlocks. Previously it only checked base unlocks (5/10)
 *                        and assumed longbow at any level >=10, which meant at e.g.
 *                        level 65 (yew shortbow unlock, yew longbow at 70) the
 *                        script would click 270.17 trying to make a yew longbow it
 *                        didn't have the level for. Now: shortbow within [tier_short,
 *                        tier_long), longbow at [tier_long, next_tier_short).
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

    private static final String SETTINGS_PREFIX = "progressive_fletcher_";
    private static final String DEFAULT_PROFILE = "default";

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
        String effective = null;
        if (args != null && !args.trim().isEmpty()) {
            String name = args.trim();
            Optional<FletcherSettings> loaded = ScriptSettings.getDefault()
                    .load(SETTINGS_PREFIX + name, FletcherSettings.class);
            if (loaded.isPresent()) {
                effective = argsFromSettings(loaded.get());
                Log.info("Loaded profile from args: '" + name + "' -> " + effective);
            } else {
                // Fall back: treat args as legacy fletcher arg string ("yew longbow" etc.)
                effective = args;
                Log.info("Args '" + name + "' not a profile; using as fletcher arg string.");
            }
        } else {
            effective = showSettingsDialog();
        }
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

    public static class FletcherSettings {
        public String mode = "progressive"; // progressive | string | cut | string_specific
        public String wood = "yew";
        public String bowType = "longbow";
    }

    private String argsFromSettings(FletcherSettings s) {
        switch (s.mode) {
            case "string": return "string";
            case "cut":    return s.wood.equals("logs") ? s.bowType : s.wood + " " + s.bowType;
            case "string_specific": return "string " + s.wood + " " + s.bowType;
            default:       return "progressive";
        }
    }

    // ---------- Profile helpers ----------

    private List<String> getProfileNames() {
        try {
            return ScriptSettings.getDefault().getSaveNames().stream()
                    .filter(n -> n.startsWith(SETTINGS_PREFIX))
                    .map(n -> n.substring(SETTINGS_PREFIX.length()))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private FletcherSettings loadProfile(String name) {
        try {
            return ScriptSettings.getDefault()
                    .load(SETTINGS_PREFIX + name, FletcherSettings.class)
                    .orElseGet(FletcherSettings::new);
        } catch (Exception e) { return new FletcherSettings(); }
    }

    private void saveProfile(String name, FletcherSettings s) {
        try { ScriptSettings.getDefault().save(SETTINGS_PREFIX + name, s); }
        catch (Exception e) { Log.warn("Save '" + name + "' failed: " + e.getMessage()); }
    }

    private void deleteProfile(String name) {
        try { ScriptSettings.getDefault().delete(SETTINGS_PREFIX + name); }
        catch (Exception e) { Log.warn("Delete '" + name + "' failed: " + e.getMessage()); }
    }

    private void refreshProfileBox(JComboBox<String> box) {
        Object selected = box.getSelectedItem();
        box.removeAllItems();
        for (String n : getProfileNames()) box.addItem(n);
        if (selected != null) box.setSelectedItem(selected);
    }

    private String showSettingsDialog() {
        final FletcherSettings cur = loadProfile(DEFAULT_PROFILE);
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

                JComboBox<String> woodBox = new JComboBox<>(new String[]{
                        "Logs", "Oak", "Willow", "Maple", "Yew", "Magic" });
                JRadioButton rbShort = new JRadioButton("Shortbow");
                JRadioButton rbLong  = new JRadioButton("Longbow");
                ButtonGroup typeGroup = new ButtonGroup();
                typeGroup.add(rbShort); typeGroup.add(rbLong);

                Runnable populate = () -> {
                    switch (cur.mode) {
                        case "string":          rbProgStr.setSelected(true); break;
                        case "cut":             rbSpecCut.setSelected(true); break;
                        case "string_specific": rbSpecStr.setSelected(true); break;
                        default:                rbProgCut.setSelected(true);
                    }
                    woodBox.setSelectedItem(capitalize(cur.wood));
                    if ("shortbow".equals(cur.bowType)) rbShort.setSelected(true); else rbLong.setSelected(true);
                };
                Runnable collect = () -> {
                    if (rbProgCut.isSelected())      cur.mode = "progressive";
                    else if (rbProgStr.isSelected()) cur.mode = "string";
                    else if (rbSpecCut.isSelected()) cur.mode = "cut";
                    else if (rbSpecStr.isSelected()) cur.mode = "string_specific";
                    cur.wood = ((String) woodBox.getSelectedItem()).toLowerCase();
                    cur.bowType = rbShort.isSelected() ? "shortbow" : "longbow";
                };
                populate.run();

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

                JComboBox<String> profileBox = new JComboBox<>(getProfileNames().toArray(new String[0]));
                JButton loadBtn = new JButton("Load");
                JButton saveAsBtn = new JButton("Save as...");
                JButton deleteBtn = new JButton("Delete");
                loadBtn.addActionListener(e -> {
                    String name = (String) profileBox.getSelectedItem();
                    if (name == null || name.isEmpty()) return;
                    FletcherSettings loaded = loadProfile(name);
                    cur.mode = loaded.mode; cur.wood = loaded.wood; cur.bowType = loaded.bowType;
                    populate.run();
                    updateEnabled.run();
                    Log.info("Loaded profile: " + name);
                });
                saveAsBtn.addActionListener(e -> {
                    String name = JOptionPane.showInputDialog(dlg, "Profile name:", "Save Profile", JOptionPane.QUESTION_MESSAGE);
                    if (name == null || name.trim().isEmpty()) return;
                    name = name.trim();
                    collect.run();
                    saveProfile(name, cur);
                    refreshProfileBox(profileBox);
                    profileBox.setSelectedItem(name);
                });
                deleteBtn.addActionListener(e -> {
                    String name = (String) profileBox.getSelectedItem();
                    if (name == null || name.isEmpty()) return;
                    int c = JOptionPane.showConfirmDialog(dlg, "Delete profile '" + name + "'?",
                            "Confirm Delete", JOptionPane.YES_NO_OPTION);
                    if (c == JOptionPane.YES_OPTION) {
                        deleteProfile(name);
                        refreshProfileBox(profileBox);
                    }
                });
                JPanel profileRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
                profileRow.setBorder(BorderFactory.createTitledBorder("Profile"));
                profileRow.add(new JLabel("Saved:"));
                profileRow.add(profileBox);
                profileRow.add(loadBtn);
                profileRow.add(saveAsBtn);
                profileRow.add(deleteBtn);

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

                JPanel center = new JPanel();
                center.setLayout(new javax.swing.BoxLayout(center, javax.swing.BoxLayout.Y_AXIS));
                center.add(modePanel);
                center.add(detailPanel);

                JButton ok = new JButton("Start");
                JButton cancel = new JButton("Cancel");
                ok.addActionListener(e -> {
                    collect.run();
                    saveProfile(DEFAULT_PROFILE, cur);
                    result[0] = argsFromSettings(cur);
                    dlg.dispose();
                });
                cancel.addActionListener(e -> dlg.dispose());

                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                buttonPanel.add(ok); buttonPanel.add(cancel);

                dlg.setLayout(new BorderLayout());
                dlg.add(profileRow, BorderLayout.NORTH);
                dlg.add(center, BorderLayout.CENTER);
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
        // Each wood tier has its own shortbow/longbow pair (shortbow first, longbow +5):
        //   Logs    shortbow 5,  longbow 10
        //   Oak     shortbow 20, longbow 25
        //   Willow  shortbow 35, longbow 40
        //   Maple   shortbow 50, longbow 55
        //   Yew     shortbow 65, longbow 70
        //   Magic   shortbow 80, longbow 85
        // logForLevel switches log type at the next tier's shortbow unlock,
        // so within a tier we just check if longbow is unlocked yet.
        if (level < 5)  return 15;                                        // arrow shafts
        if (level < 10) return 16;                                        // logs shortbow
        if (level < 20) return 17;                                        // logs longbow
        if (level < 25) return 16;                                        // oak shortbow
        if (level < 35) return 17;                                        // oak longbow
        if (level < 40) return 16;                                        // willow shortbow
        if (level < 50) return 17;                                        // willow longbow
        if (level < 55) return 16;                                        // maple shortbow
        if (level < 65) return 17;                                        // maple longbow
        if (level < 70) return 16;                                        // yew shortbow
        if (level < 80) return 17;                                        // yew longbow
        if (level < 85) return 16;                                        // magic shortbow
        return 17;                                                        // magic longbow
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
