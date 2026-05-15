package scripts.aBowStringer2;

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
import org.tribot.script.sdk.types.GameObject;
import org.tribot.script.sdk.types.Widget;
import org.tribot.script.sdk.types.WorldTile;
import org.tribot.script.sdk.util.ScriptSettings;
import org.tribot.script.sdk.walking.GlobalWalking;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/*
 * CHANGELOG
 *   1.1.0 (2026-05-14) - Multi-profile save/load + args support + Swing GUI.
 *                        Settings consist of a single field: materialMode ("auto" /
 *                        "flax" / "wool"). Profile system namespaced under
 *                        "a_bow_stringer_<name>". execute(args) tries args as profile
 *                        name, falls back to "flax" / "wool" / "auto" as mode override,
 *                        else shows dialog.
 *   1.0.2 (2026-05-14) - Drop dynamic last-visible widget search entirely.
 *                        Hardcoded: 270.15 = Ball of wool, 270.16 = Bow string.
 *                        Pick by level (>=10 -> bow string, else ball of wool).
 *   1.0.1 (2026-05-14) - Cap last-visible item-slot search at 270.25 (superseded by 1.0.2).
 *   1.0.0 (2026-05-14) - Initial modern rewrite of aBowStringer using current SDK.
 *                        F2P-testable (Lumbridge top-floor spinning wheel + bank).
 *                        Auto picks Flax (lvl 10+) or Wool (<10). GlobalWalking handles
 *                        bank<->wheel travel. Make-X selection via last-visible widget
 *                        child of 270 (>= idx 15). Selective deposit, Escape recovery,
 *                        5-min stuck detection.
 *
 * KNOWN-FIX
 *   - Legacy script used DaxWalker with paid credentials. Modern SDK ships
 *     GlobalWalking.walkToBank() / walkTo(WorldTile) which uses the same engine
 *     without credentials.
 *
 * OPEN
 *   - Item names "Flax", "Wool", "Bow string", "Ball of wool" need in-game verification;
 *     capitalization/spacing has shifted over the years in OSRS.
 *   - Hardcoded WHEEL_TILE assumes Lumbridge. To support other wheel locations, accept
 *     an args string and parse a tile, or detect wheel via Query.gameObjects()
 *     before falling back to walking to Lumbridge.
 */
@TribotScriptManifest(
        name = "aBowStringer",
        author = "adamhackz (rewrite)",
        category = "Crafting",
        description = "Spins flax into bow strings (lvl 10+) or wool into balls of wool at the nearest spinning wheel."
)
public class Main implements TribotScript {

    private static final String FLAX        = "Flax";
    private static final String WOOL        = "Wool";
    private static final String BOW_STRING  = "Bow string";
    private static final String BALL_OF_WOOL = "Ball of wool";

    private static final String[] FLAX_A          = { FLAX };
    private static final String[] WOOL_A          = { WOOL };
    private static final String[] BOW_STRING_A    = { BOW_STRING };
    private static final String[] BALL_OF_WOOL_A  = { BALL_OF_WOOL };
    private static final String[] SPIN_ACTION     = { "Spin" };

    // Lumbridge top-floor spinning wheel (default F2P-friendly location).
    private static final WorldTile WHEEL_TILE = new WorldTile(3209, 3213, 1);

    private static final String SETTINGS_PREFIX = "a_bow_stringer_";
    private static final String DEFAULT_PROFILE = "default";

    private volatile boolean running = true;
    private long lastProgressMs = System.currentTimeMillis();
    private static final long STUCK_TIMEOUT_MS = 5 * 60_000;

    // "auto" (level-based), "flax" (force bow strings), "wool" (force balls of wool).
    private String materialMode = "auto";

    @Override
    public void execute(final String args) {
        if (args != null && !args.trim().isEmpty()) {
            String name = args.trim();
            Optional<StringerSettings> loaded = ScriptSettings.getDefault()
                    .load(SETTINGS_PREFIX + name, StringerSettings.class);
            if (loaded.isPresent()) {
                materialMode = loaded.get().materialMode;
                Log.info("Loaded profile from args: '" + name + "'");
            } else if (name.equalsIgnoreCase("flax") || name.equalsIgnoreCase("wool") || name.equalsIgnoreCase("auto")) {
                materialMode = name.toLowerCase();
                Log.info("Args '" + name + "' used as material mode override.");
            } else {
                Log.warn("Args '" + name + "' not a profile or known mode; showing dialog.");
                if (!showSettingsDialog()) { Log.info("Cancelled. Exiting."); return; }
            }
        } else {
            if (!showSettingsDialog()) { Log.info("Cancelled. Exiting."); return; }
        }
        Log.info("aBowStringer started. mode=" + materialMode);
        Antiban.setScriptAiAntibanEnabled(true);
        while (running) {
            try { tick(); } catch (Exception e) { Log.error("Tick failed: " + e.getMessage()); }
            Waiting.waitNormal(350, 120);
        }
        Log.info("aBowStringer stopping.");
    }

    private boolean shouldUseFlax() {
        if ("flax".equals(materialMode)) return true;
        if ("wool".equals(materialMode)) return false;
        return Skill.CRAFTING.getActualLevel() >= 10;
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

        boolean useFlax = shouldUseFlax();
        final String   resource  = useFlax ? FLAX        : WOOL;
        final String[] resourceA = useFlax ? FLAX_A      : WOOL_A;
        final String[] productA  = useFlax ? BOW_STRING_A : BALL_OF_WOOL_A;

        if (Bank.isOpen()) {
            if (Inventory.contains(resourceA)) {
                Bank.close();
                Waiting.waitUntil(2000, () -> !Bank.isOpen());
                return;
            }
            if (!Bank.contains(resourceA)) {
                Log.info("Out of " + resource + " in bank. Stopping.");
                running = false;
                return;
            }
            if (Inventory.contains(productA)) {
                Bank.depositAll(productA[0]);
                Waiting.waitUntil(1500, () -> !Inventory.contains(productA));
            }
            Bank.withdrawAll(resource);
            Waiting.waitUntil(2500, () -> Inventory.contains(resourceA));
            return;
        }

        // Need to bank: have product but no resource left, or empty inventory.
        if (!Inventory.contains(resourceA)) {
            if (!Bank.ensureOpen()) {
                Log.info("Walking to bank.");
                GlobalWalking.walkToBank();
            }
            return;
        }

        // Have resource: spin.
        spin(useFlax);
    }

    private void spin(final boolean useFlax) {
        if (MyPlayer.getAnimation() != -1) { Waiting.waitNormal(900, 250); return; }

        // If make-X is already up, click the highest-tier visible item.
        Optional<Widget> labels = Widgets.get(new int[]{ 270, 14 });
        if (labels.isPresent() && labels.get().isVisible()) {
            // Spinning wheel make-X (assumed): 270.15 ball of wool, 270.16 bow string.
            int childIdx = useFlax ? 16 : 15;
            Optional<Widget> target = Widgets.get(new int[]{ 270, childIdx });
            if (!target.isPresent() || !target.get().isVisible()) {
                Log.warn("Make-X 270." + childIdx + " not visible. Pressing Escape.");
                Keyboard.pressEscape();
                return;
            }
            Log.info("Clicking 270." + childIdx + " (" + (useFlax ? BOW_STRING : BALL_OF_WOOL) + ")");
            if (target.get().click()) {
                Waiting.waitUntil(4000, () -> MyPlayer.getAnimation() != -1);
            }
            return;
        }

        // Need to interact with a wheel.
        Optional<GameObject> wheel = Query.gameObjects().actionEquals(SPIN_ACTION).findFirst();
        if (!wheel.isPresent()) {
            Log.info("No spinning wheel nearby — walking to Lumbridge.");
            GlobalWalking.walkTo(WHEEL_TILE);
            return;
        }
        if (!wheel.get().isVisible()) {
            wheel.get().adjustCameraTo();
            return;
        }
        Log.info("Spinning.");
        if (wheel.get().interact("Spin")) {
            Waiting.waitUntil(3000, () -> Widgets.get(new int[]{ 270, 14 }).map(Widget::isVisible).orElse(false));
        }
    }

    // ---------- Settings + Profiles ----------

    public static class StringerSettings {
        public String materialMode = "auto"; // auto | flax | wool
    }

    private List<String> getProfileNames() {
        try {
            return ScriptSettings.getDefault().getSaveNames().stream()
                    .filter(n -> n.startsWith(SETTINGS_PREFIX))
                    .map(n -> n.substring(SETTINGS_PREFIX.length()))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private StringerSettings loadProfile(String name) {
        try {
            return ScriptSettings.getDefault()
                    .load(SETTINGS_PREFIX + name, StringerSettings.class)
                    .orElseGet(StringerSettings::new);
        } catch (Exception e) { return new StringerSettings(); }
    }

    private void saveProfile(String name, StringerSettings s) {
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

    private boolean showSettingsDialog() {
        final StringerSettings initial = loadProfile(DEFAULT_PROFILE);
        materialMode = initial.materialMode;
        final boolean[] ok = { false };
        try {
            SwingUtilities.invokeAndWait(() -> {
                JDialog dlg = new JDialog((Frame) null, "aBowStringer", true);
                dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

                JComboBox<String> modeBox = new JComboBox<>(new String[]{ "auto", "flax", "wool" });
                modeBox.setSelectedItem(materialMode);

                JComboBox<String> profileBox = new JComboBox<>(getProfileNames().toArray(new String[0]));
                JButton loadBtn = new JButton("Load");
                JButton saveAsBtn = new JButton("Save as...");
                JButton deleteBtn = new JButton("Delete");
                loadBtn.addActionListener(e -> {
                    String name = (String) profileBox.getSelectedItem();
                    if (name == null || name.isEmpty()) return;
                    materialMode = loadProfile(name).materialMode;
                    modeBox.setSelectedItem(materialMode);
                    Log.info("Loaded profile: " + name);
                });
                saveAsBtn.addActionListener(e -> {
                    String name = JOptionPane.showInputDialog(dlg, "Profile name:", "Save Profile", JOptionPane.QUESTION_MESSAGE);
                    if (name == null || name.trim().isEmpty()) return;
                    name = name.trim();
                    StringerSettings s = new StringerSettings();
                    s.materialMode = (String) modeBox.getSelectedItem();
                    saveProfile(name, s);
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

                JPanel modeP = new JPanel(new FlowLayout(FlowLayout.LEFT));
                modeP.setBorder(BorderFactory.createTitledBorder("Material"));
                modeP.add(new JLabel("Mode:"));
                modeP.add(modeBox);

                JButton start = new JButton("Start");
                JButton cancel = new JButton("Cancel");
                start.addActionListener(e -> {
                    materialMode = (String) modeBox.getSelectedItem();
                    StringerSettings s = new StringerSettings();
                    s.materialMode = materialMode;
                    saveProfile(DEFAULT_PROFILE, s);
                    ok[0] = true; dlg.dispose();
                });
                cancel.addActionListener(e -> dlg.dispose());
                JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                btns.add(start); btns.add(cancel);

                dlg.setLayout(new BorderLayout());
                dlg.add(profileRow, BorderLayout.NORTH);
                dlg.add(modeP, BorderLayout.CENTER);
                dlg.add(btns, BorderLayout.SOUTH);
                dlg.pack(); dlg.setLocationRelativeTo(null); dlg.setVisible(true);
            });
        } catch (Exception e) { Log.error("Dialog failed: " + e.getMessage()); return false; }
        return ok[0];
    }
}
