package scripts.progressiveCrafter;

import org.tribot.script.sdk.Bank;
import org.tribot.script.sdk.ChatScreen;
import org.tribot.script.sdk.Inventory;
import org.tribot.script.sdk.Log;
import org.tribot.script.sdk.MyPlayer;
import org.tribot.script.sdk.Skill;
import org.tribot.script.sdk.Waiting;
import org.tribot.script.sdk.Widgets;
import org.tribot.script.sdk.antiban.Antiban;
import org.tribot.script.sdk.query.Query;
import org.tribot.script.sdk.script.TribotScript;
import org.tribot.script.sdk.script.TribotScriptManifest;
import org.tribot.script.sdk.types.InventoryItem;
import org.tribot.script.sdk.types.Widget;
import org.tribot.script.sdk.util.ScriptSettings;

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
 *   1.1.0 (2026-05-14) - Multi-profile save/load. GUI gains Profile row at the top
 *                        (dropdown + Load / Save as... / Delete). Profiles namespaced
 *                        under "progressive_crafter_<name>" in ScriptSettings.
 *                        execute(args): if args matches a saved profile, load it and
 *                        skip dialog. Otherwise show dialog with "default" pre-loaded.
 *   1.0.0 (2026-05-14) - Initial modern rewrite of progressiveCrafter using current SDK.
 *                        State machine + widget-based make-X clicking (270.15-20).
 *                        Selective deposit (keep needle/thread/leather, deposit rest).
 *                        Stuck detection (5min, antiban-aware).
 *                        Swing settings dialog: auto-progress by level or specific item.
 *
 * KNOWN-FIX
 *   - Bank.withdrawAll(tool) would pull a whole stack and fill inventory.
 *     Fix: Bank.withdraw(NEEDLE, 1) + early return if not landed before bulk material.
 *   - Skill.getCurrentLevel() returns boosted level which mis-gates unlocks.
 *     Fix: use Skill.CRAFTING.getActualLevel() instead.
 *   - Modern make-X dialog has 270.14 as a labels container, items shifted to 270.15+.
 *     Fix: childForLevel() maps level -> 270.15..270.20 (gloves..chaps).
 *
 * OPEN
 *   - Coif (level 38+) not handled. childForLevel caps at 270.20 (chaps).
 *     Verify Coif's actual widget index with widget explorer when leveled.
 *   - No Escape recovery for make-X stuck state (fletcher has this, crafter does not).
 */
@TribotScriptManifest(
        name = "ProgressiveCrafter",
        author = "adamhackz (rewrite)",
        category = "Crafting",
        description = "Crafts leather items, progressing items as Crafting level increases."
)
public class Main implements TribotScript {

    private static final String NEEDLE  = "Needle";
    private static final String THREAD  = "Thread";
    private static final String LEATHER = "Leather";

    private static final String[] NEEDLE_A  = { NEEDLE };
    private static final String[] THREAD_A  = { THREAD };
    private static final String[] LEATHER_A = { LEATHER };

    private volatile boolean running = true;
    private long lastProgressMs = System.currentTimeMillis();
    // Long enough to survive TRiBot AI antiban breaks (which can last several minutes).
    private static final long STUCK_TIMEOUT_MS = 5 * 60_000;

    private static final String SETTINGS_PREFIX = "progressive_crafter_";
    private static final String DEFAULT_PROFILE = "default";

    private int childOverride = -1; // -1 = auto (use childForLevel)
    private String itemOverrideName = null;

    @Override
    public void execute(final String args) {
        if (args != null && !args.trim().isEmpty()) {
            String name = args.trim();
            Optional<CrafterSettings> loaded = ScriptSettings.getDefault()
                    .load(SETTINGS_PREFIX + name, CrafterSettings.class);
            if (loaded.isPresent()) {
                applySettings(loaded.get());
                Log.info("Loaded profile from args: '" + name + "'");
            } else {
                Log.warn("No profile named '" + name + "'; showing dialog.");
                if (!showSettingsDialog()) { Log.info("Cancelled. Exiting."); return; }
            }
        } else {
            if (!showSettingsDialog()) { Log.info("Cancelled. Exiting."); return; }
        }
        Log.info("ProgressiveCrafter started. item=" + (itemOverrideName == null ? "auto" : itemOverrideName));
        Antiban.setScriptAiAntibanEnabled(true);

        while (running) {
            try {
                tick();
            } catch (Exception e) {
                Log.error("Tick failed: " + e.getMessage());
            }
            Waiting.waitNormal(350, 120);
        }

        Log.info("ProgressiveCrafter stopping.");
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

        if (ChatScreen.isClickContinueOpen()) {
            ChatScreen.clickContinue();
            return;
        }

        boolean hasLeather = Inventory.contains(LEATHER_A);
        boolean hasNeedle  = Inventory.contains(NEEDLE_A);
        boolean hasThread  = Inventory.contains(THREAD_A);

        if (Bank.isOpen()) {
            bankFlow(hasLeather, hasNeedle, hasThread);
            return;
        }

        if (hasLeather && hasNeedle && hasThread) {
            craft();
            return;
        }

        Bank.ensureOpen();
        Waiting.waitUntil(3000, Bank::isOpen);
    }

    private void bankFlow(boolean hasLeather, boolean hasNeedle, boolean hasThread) {
        if (hasLeather && hasNeedle && hasThread) {
            Bank.close();
            Waiting.waitUntil(2000, () -> !Bank.isOpen());
            return;
        }
        if (!Bank.contains(LEATHER_A)) {
            Log.info("No leather left in bank. Stopping.");
            running = false;
            return;
        }
        if (!hasNeedle && !Bank.contains(NEEDLE_A)) {
            Log.warn("Bank missing needle. Stopping.");
            running = false;
            return;
        }
        if (!hasThread && !Bank.contains(THREAD_A)) {
            Log.warn("Bank missing thread. Stopping.");
            running = false;
            return;
        }

        depositCraftedItems();

        if (!hasNeedle) {
            Bank.withdraw(NEEDLE, 1);
            Waiting.waitUntil(1500, () -> Inventory.contains(NEEDLE_A));
            if (!Inventory.contains(NEEDLE_A)) return;
        }
        if (!hasThread) {
            Bank.withdraw(THREAD, 1);
            Waiting.waitUntil(1500, () -> Inventory.contains(THREAD_A));
            if (!Inventory.contains(THREAD_A)) return;
        }
        if (!hasLeather) {
            Bank.withdrawAll(LEATHER);
            Waiting.waitUntil(2500, () -> Inventory.contains(LEATHER_A));
        }
    }

    private void depositCraftedItems() {
        while (true) {
            Optional<InventoryItem> next = Query.inventory().stream()
                    .filter(item -> {
                        String n = item.getName();
                        return !NEEDLE.equals(n) && !THREAD.equals(n) && !LEATHER.equals(n);
                    })
                    .findFirst();
            if (!next.isPresent()) return;

            final String name = next.get().getName();
            if (!Bank.depositAll(name)) return;
            final String[] nameA = { name };
            Waiting.waitUntil(1500, () -> !Inventory.contains(nameA));
        }
    }

    private static final int MAKE_X_WIDGET = 270;
    private static final int MAKE_X_LABELS_CHILD = 14;

    private void craft() {
        if (MyPlayer.getAnimation() != -1) {
            Waiting.waitNormal(900, 250);
            return;
        }

        Optional<Widget> labels = Widgets.get(new int[]{ MAKE_X_WIDGET, MAKE_X_LABELS_CHILD });

        if (!labels.isPresent() || !labels.get().isVisible()) {
            Optional<InventoryItem> needle  = Query.inventory().nameEquals(NEEDLE_A).findFirst();
            Optional<InventoryItem> leather = Query.inventory().nameEquals(LEATHER_A).findFirst();
            if (!needle.isPresent() || !leather.isPresent()) return;

            if (needle.get().useOn(leather.get())) {
                Waiting.waitUntil(3000, () -> {
                    Optional<Widget> l = Widgets.get(new int[]{ MAKE_X_WIDGET, MAKE_X_LABELS_CHILD });
                    return l.isPresent() && l.get().isVisible();
                });
            }
            return;
        }

        int level = Skill.CRAFTING.getActualLevel();
        int childIdx  = (childOverride > 0) ? childOverride : childForLevel(level);
        String itemName = (itemOverrideName != null) ? itemOverrideName : itemForLevel(level);

        Optional<Widget> target = Widgets.get(new int[]{ MAKE_X_WIDGET, childIdx });
        if (!target.isPresent() || !target.get().isVisible()) {
            Log.warn("Item widget 270." + childIdx + " (" + itemName + ") not visible. Level=" + level);
            return;
        }

        Log.info("Clicking 270." + childIdx + " -> " + itemName + " (crafting level " + level + ")");
        if (target.get().click()) {
            Waiting.waitUntil(4000, () -> MyPlayer.getAnimation() != -1);
        } else {
            Log.warn("Widget click failed on 270." + childIdx);
        }
    }

    private int childForLevel(int level) {
        if (level < 7)  return 15; // Leather gloves
        if (level < 9)  return 16; // Leather boots
        if (level < 11) return 17; // Leather cowl
        if (level < 14) return 18; // Leather vambraces
        if (level < 18) return 19; // Leather body
        return 20;                 // Leather chaps (level 18+)
    }

    private String itemForLevel(int level) {
        if (level < 7)  return "Leather gloves";
        if (level < 9)  return "Leather boots";
        if (level < 11) return "Leather cowl";
        if (level < 14) return "Leather vambraces";
        if (level < 18) return "Leather body";
        return "Leather chaps";
    }

    public static class CrafterSettings {
        public String item = "auto"; // auto | gloves | boots | cowl | vambraces | body | chaps
    }

    private static final String[] ITEM_KEYS  = { "auto", "gloves", "boots", "cowl", "vambraces", "body", "chaps" };
    private static final String[] ITEM_LABELS = { "Auto (progress by level)",
            "Leather gloves", "Leather boots", "Leather cowl",
            "Leather vambraces", "Leather body", "Leather chaps" };
    private static final int[] ITEM_CHILD = { -1, 15, 16, 17, 18, 19, 20 };

    private String currentItemKey = "auto";

    private void applySettings(CrafterSettings s) {
        currentItemKey = s.item;
        int idx = 0;
        for (int i = 0; i < ITEM_KEYS.length; i++) if (ITEM_KEYS[i].equals(s.item)) { idx = i; break; }
        if (idx > 0) {
            childOverride = ITEM_CHILD[idx];
            itemOverrideName = ITEM_LABELS[idx];
        } else {
            childOverride = -1;
            itemOverrideName = null;
        }
    }

    private CrafterSettings collectSettings() {
        CrafterSettings s = new CrafterSettings();
        s.item = currentItemKey;
        return s;
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

    private CrafterSettings loadProfile(String name) {
        try {
            return ScriptSettings.getDefault()
                    .load(SETTINGS_PREFIX + name, CrafterSettings.class)
                    .orElseGet(CrafterSettings::new);
        } catch (Exception e) { return new CrafterSettings(); }
    }

    private void saveProfile(String name, CrafterSettings s) {
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
        final CrafterSettings initial = loadProfile(DEFAULT_PROFILE);
        applySettings(initial);
        final boolean[] ok = { false };
        try {
            SwingUtilities.invokeAndWait(() -> {
                JDialog dlg = new JDialog((Frame) null, "ProgressiveCrafter", true);
                dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

                JComboBox<String> itemBox = new JComboBox<>(ITEM_LABELS);
                JComboBox<String> profileBox = new JComboBox<>(getProfileNames().toArray(new String[0]));

                Runnable populate = () -> {
                    int idx = 0;
                    for (int i = 0; i < ITEM_KEYS.length; i++) if (ITEM_KEYS[i].equals(currentItemKey)) { idx = i; break; }
                    itemBox.setSelectedIndex(idx);
                };
                Runnable collect = () -> {
                    currentItemKey = ITEM_KEYS[itemBox.getSelectedIndex()];
                };
                populate.run();

                JButton loadBtn = new JButton("Load");
                JButton saveAsBtn = new JButton("Save as...");
                JButton deleteBtn = new JButton("Delete");
                loadBtn.addActionListener(e -> {
                    String name = (String) profileBox.getSelectedItem();
                    if (name == null || name.isEmpty()) return;
                    applySettings(loadProfile(name));
                    populate.run();
                    Log.info("Loaded profile: " + name);
                });
                saveAsBtn.addActionListener(e -> {
                    String name = JOptionPane.showInputDialog(dlg, "Profile name:", "Save Profile", JOptionPane.QUESTION_MESSAGE);
                    if (name == null || name.trim().isEmpty()) return;
                    name = name.trim();
                    collect.run();
                    saveProfile(name, collectSettings());
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

                JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                panel.setBorder(BorderFactory.createTitledBorder("Item to craft"));
                panel.add(new JLabel("Item:"));
                panel.add(itemBox);

                JButton start = new JButton("Start");
                JButton cancel = new JButton("Cancel");
                start.addActionListener(e -> {
                    collect.run();
                    applySettings(collectSettings());
                    saveProfile(DEFAULT_PROFILE, collectSettings());
                    ok[0] = true;
                    dlg.dispose();
                });
                cancel.addActionListener(e -> dlg.dispose());

                JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                buttons.add(start); buttons.add(cancel);

                dlg.setLayout(new BorderLayout());
                dlg.add(profileRow, BorderLayout.NORTH);
                dlg.add(panel, BorderLayout.CENTER);
                dlg.add(buttons, BorderLayout.SOUTH);
                dlg.pack();
                dlg.setLocationRelativeTo(null);
                dlg.setVisible(true);
            });
        } catch (Exception e) {
            Log.error("Settings dialog failed: " + e.getMessage());
            return false;
        }
        return ok[0];
    }
}
