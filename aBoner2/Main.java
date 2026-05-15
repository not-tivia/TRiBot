package scripts.aBoner2;

import org.tribot.script.sdk.Bank;
import org.tribot.script.sdk.ChatScreen;
import org.tribot.script.sdk.Combat;
import org.tribot.script.sdk.Equipment;
import org.tribot.script.sdk.Inventory;
import org.tribot.script.sdk.Log;
import org.tribot.script.sdk.MyPlayer;
import org.tribot.script.sdk.Waiting;
import org.tribot.script.sdk.WorldHopper;
import org.tribot.script.sdk.Worlds;
import org.tribot.script.sdk.antiban.Antiban;
import org.tribot.script.sdk.query.Query;
import org.tribot.script.sdk.script.TribotScript;
import org.tribot.script.sdk.script.TribotScriptManifest;
import org.tribot.script.sdk.types.Area;
import org.tribot.script.sdk.types.EquipmentItem;
import org.tribot.script.sdk.types.GameObject;
import org.tribot.script.sdk.types.InventoryItem;
import org.tribot.script.sdk.types.World;
import org.tribot.script.sdk.types.WorldTile;
import org.tribot.script.sdk.util.ScriptSettings;
import org.tribot.script.sdk.walking.GlobalWalking;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
 *   1.2.0 (2026-05-14) - Multi-profile save/load. GUI gains a Profile row at the top
 *                        with dropdown + Load / Save as... / Delete buttons. Profiles
 *                        namespaced under "a_boner_<name>" in ScriptSettings.
 *                        execute(args): if args matches a saved profile, load it and
 *                        skip dialog. If not a profile, fall back to treating args as
 *                        a bone name override (preserves prior CLI behavior).
 *   1.1.0 (2026-05-14) - PKer panic-escape: if Combat.getAttackingPlayer() is present
 *                        while in wilderness, ring-tele to GE immediately. If
 *                        teleblocked, walk south toward GE (best effort).
 *                        Optional world-hop after each trip via Worlds.getRandomMembers
 *                        + WorldHopper.hop. Charge tracking confirmed to work as-is:
 *                        amulet vanishes when fully depleted (equip check fails ->
 *                        re-equip from bank); ring loses parens in name when at 0
 *                        charges (nameContains("Ring of wealth (") filters those out).
 *                        GUI extended with Safety section: panic-on-attack + hop-worlds
 *                        checkboxes, persisted via ScriptSettings.
 *   1.0.0 (2026-05-14) - Initial scoped-rewrite of aBoner using current SDK.
 *                        Loops GE <-> Chaos altar offering bones.
 *                        Burning amulet (Lava-Maze) out, Ring of wealth back.
 *
 * KNOWN-FIX
 *   - Original's onStart() had `args.equals("Dragon bones")` (no-op). Replaced
 *     with proper GUI for bone selection saved via ScriptSettings.
 *   - Original used DaxWalker with paid credentials. GlobalWalking now wraps
 *     this in the SDK without credentials.
 *   - Burning amulet fully depletes -> disappears from equipment -> our equip check
 *     fails -> acquireAndEquipAmulet pulls a fresh one from the bank. No special
 *     charge tracking needed.
 *   - Ring of wealth at 0 charges becomes "Ring of wealth" (no parens) -> our
 *     nameContains("Ring of wealth (") predicate excludes the depleted one -> we
 *     equip a fresh charged one from the bank.
 *
 * OPEN
 *   - Hardcoded wilderness path (10+ tiles) from the original is NOT used here;
 *     GlobalWalking handles it. If GlobalWalking takes a worse route than the
 *     hand-tuned path, swap to LocalWalking.walkPath(WILDY_PATH).
 *   - Wilderness teleport confirmation dialog (if any) is handled via
 *     ChatScreen.selectOption("Yes"). Verify the option text in-game.
 *   - Panic-escape uses the wilderness Combat API; brief windows after a PKer
 *     spawns but before they're "attacking" may not be detected. Consider
 *     supplementing with a nearby-player scan (Query.players().maxDistance(N)
 *     filtered by combat-level being within wilderness PVP range).
 */
@TribotScriptManifest(
        name = "aBoner",
        author = "adamhackz (rewrite)",
        category = "Prayer",
        description = "Offers bones at the Chaos altar. GE <-> altar loop via Burning amulet + Ring of wealth."
)
public class Main implements TribotScript {

    private static final Area GE_AREA = Area.fromRectangle(
            new WorldTile(3160, 3493, 0), new WorldTile(3169, 3486, 0));
    private static final Area ALTAR_AREA = Area.fromRectangle(
            new WorldTile(2949, 3822, 0), new WorldTile(2953, 3819, 0));
    private static final WorldTile GE_TILE = new WorldTile(3164, 3486, 0);
    private static final WorldTile ALTAR_TILE = new WorldTile(2948, 3820, 0);

    private static final String AMULET_NAME = "Burning amulet(";
    private static final String RING_NAME = "Ring of wealth (";

    private static final String SETTINGS_PREFIX = "a_boner_";
    private static final String DEFAULT_PROFILE = "default";

    private volatile boolean running = true;
    private long lastProgressMs = System.currentTimeMillis();
    private static final long STUCK_TIMEOUT_MS = 5 * 60_000;

    private String boneName = "Dragon bones";
    private boolean hopBetweenTrips = false;
    private boolean panicOnAttack = true;
    private boolean shouldHopAfterReturn = false;
    private final String[] boneA() { return new String[]{ boneName }; }

    @Override
    public void execute(final String args) {
        if (args != null && !args.trim().isEmpty()) {
            String name = args.trim();
            Optional<BonerSettings> loaded = ScriptSettings.getDefault()
                    .load(SETTINGS_PREFIX + name, BonerSettings.class);
            if (loaded.isPresent()) {
                applySettings(loaded.get());
                Log.info("Loaded profile from args: '" + name + "'");
            } else {
                // Backwards-compatible: treat args as a bone name override.
                boneName = name;
                Log.info("Args '" + name + "' not a profile; using as bone name.");
            }
        } else {
            if (!showSettingsDialog()) { Log.info("Cancelled. Exiting."); return; }
        }

        Log.info("aBoner started. Bone=" + boneName);
        Antiban.setScriptAiAntibanEnabled(true);
        while (running) {
            try { tick(); } catch (Exception e) { Log.error("Tick failed: " + e.getMessage()); }
            Waiting.waitNormal(350, 120);
        }
        Log.info("aBoner stopping.");
    }

    private void tick() {
        if (MyPlayer.getAnimation() != -1 || Bank.isOpen() || ChatScreen.isOpen()) {
            lastProgressMs = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - lastProgressMs > STUCK_TIMEOUT_MS) {
            Log.error("No progress for " + (STUCK_TIMEOUT_MS / 1000) + "s. Stopping.");
            running = false;
            return;
        }

        // Dialog handling: confirm wilderness tele, click-continue, etc.
        if (ChatScreen.isOpen()) {
            if (ChatScreen.containsOption(new String[]{ "Yes" })) {
                ChatScreen.selectOption(new String[]{ "Yes" });
                return;
            }
            if (ChatScreen.isClickContinueOpen()) {
                ChatScreen.clickContinue();
                return;
            }
        }

        // PKer panic check: if a player is targeting us in the wilderness, bail out.
        if (panicOnAttack && Combat.isInWilderness() && Combat.getAttackingPlayer().isPresent()) {
            panicEscape();
            return;
        }

        boolean atGE = GE_AREA.containsMyPlayer();
        boolean atAltar = ALTAR_AREA.containsMyPlayer();
        boolean hasBones = Inventory.contains(boneA());
        boolean amuletOn = isAmuletEquipped();
        boolean ringOn = isRingEquipped();

        // World hop on return to GE (if enabled). Do this BEFORE any bank/withdraw
        // because hopping resets the bank state.
        if (atGE && shouldHopAfterReturn && hopBetweenTrips) {
            hopWorld();
            shouldHopAfterReturn = false;
            return;
        }

        // At altar: use bones, else tele back via ring.
        if (atAltar) {
            if (hasBones) { useBonesOnAltar(); return; }
            teleToGE();
            return;
        }

        // In wilderness but not at altar: walk toward altar (we just tele'd in,
        // or got bounced). If no bones / no ring, just try to tele out via ring.
        if (Combat.isInWilderness()) {
            if (!hasBones) {
                teleToGE();
                return;
            }
            Log.info("Walking to Chaos altar.");
            GlobalWalking.walkTo(ALTAR_TILE);
            return;
        }

        // Not in wilderness, not at GE: walk to GE.
        if (!atGE) {
            Log.info("Walking to GE.");
            GlobalWalking.walkTo(GE_TILE);
            return;
        }

        // At GE - ensure gear and bones, then tele.
        if (!amuletOn) { acquireAndEquipAmulet(); return; }
        if (!ringOn)   { acquireAndEquipRing(); return; }
        if (!hasBones) { acquireBones(); return; }

        if (Bank.isOpen()) { Bank.close(); Waiting.waitUntil(2000, () -> !Bank.isOpen()); return; }
        teleToAltar();
    }

    private boolean isAmuletEquipped() {
        return Equipment.contains(i -> i.getDefinition() != null
                && i.getDefinition().getName() != null
                && i.getDefinition().getName().contains(AMULET_NAME));
    }

    private boolean isRingEquipped() {
        return Equipment.contains(i -> i.getDefinition() != null
                && i.getDefinition().getName() != null
                && i.getDefinition().getName().contains(RING_NAME));
    }

    private void acquireAndEquipAmulet() {
        Optional<InventoryItem> inInv = Query.inventory().nameContains(new String[]{ AMULET_NAME }).findFirst();
        if (inInv.isPresent()) { Equipment.equip(inInv.get().getName()); return; }

        if (!Bank.isOpen()) { Bank.ensureOpen(); Waiting.waitUntil(3000, Bank::isOpen); return; }
        if (!Bank.contains(i -> i.getName() != null && i.getName().contains(AMULET_NAME))) {
            Log.warn("No " + AMULET_NAME + ") found in bank. Stopping.");
            running = false; return;
        }
        // Withdraw 1 of any charged variant.
        Optional<InventoryItem> withdrawn = Query.bank()
                .nameContains(new String[]{ AMULET_NAME }).findFirst()
                .flatMap(b -> { Bank.withdraw(b.getId(), 1); return Query.inventory().nameContains(new String[]{ AMULET_NAME }).findFirst(); });
        Waiting.waitUntil(1500, () -> Inventory.contains(i -> i.getName() != null && i.getName().contains(AMULET_NAME)));
    }

    private void acquireAndEquipRing() {
        Optional<InventoryItem> inInv = Query.inventory().nameContains(new String[]{ RING_NAME }).findFirst();
        if (inInv.isPresent()) { Equipment.equip(inInv.get().getName()); return; }

        if (!Bank.isOpen()) { Bank.ensureOpen(); Waiting.waitUntil(3000, Bank::isOpen); return; }
        if (!Bank.contains(i -> i.getName() != null && i.getName().contains(RING_NAME))) {
            Log.warn("No " + RING_NAME + ") found in bank. Stopping.");
            running = false; return;
        }
        Query.bank().nameContains(new String[]{ RING_NAME }).findFirst()
                .ifPresent(b -> Bank.withdraw(b.getId(), 1));
        Waiting.waitUntil(1500, () -> Inventory.contains(i -> i.getName() != null && i.getName().contains(RING_NAME)));
    }

    private void acquireBones() {
        if (!Bank.isOpen()) { Bank.ensureOpen(); Waiting.waitUntil(3000, Bank::isOpen); return; }
        if (!Bank.contains(boneA())) {
            Log.info("Out of " + boneName + ". Stopping.");
            running = false; return;
        }
        // Deposit anything that isn't bones (we keep the inventory clean for one trip).
        Optional<InventoryItem> junk = Query.inventory().stream()
                .filter(i -> !boneName.equals(i.getName()))
                .findFirst();
        if (junk.isPresent()) {
            Bank.depositAll(junk.get().getName());
            Waiting.waitNormal(450, 120);
            return;
        }
        Bank.withdrawAll(boneName);
        Waiting.waitUntil(2500, () -> Inventory.contains(boneA()));
    }

    private void teleToAltar() {
        Optional<EquipmentItem> amulet = Equipment.getAll().stream()
                .filter(i -> i.getDefinition() != null
                        && i.getDefinition().getName() != null
                        && i.getDefinition().getName().contains(AMULET_NAME))
                .findFirst();
        if (!amulet.isPresent()) return;
        Log.info("Teleporting via Burning amulet -> Lava-Maze.");
        if (amulet.get().click("Lava-Maze")) {
            Waiting.waitUntil(6000, Combat::isInWilderness);
        }
    }

    private void teleToGE() {
        Optional<EquipmentItem> ring = Equipment.getAll().stream()
                .filter(i -> i.getDefinition() != null
                        && i.getDefinition().getName() != null
                        && i.getDefinition().getName().contains(RING_NAME))
                .findFirst();
        if (!ring.isPresent()) {
            Log.warn("No ring to tele with — walking out (DANGEROUS).");
            GlobalWalking.walkTo(GE_TILE);
            return;
        }
        Log.info("Teleporting via Ring of wealth -> Grand Exchange.");
        if (ring.get().click("Grand Exchange")) {
            Waiting.waitUntil(6000, GE_AREA::containsMyPlayer);
            shouldHopAfterReturn = true; // arm world-hop check on next tick at GE
        }
    }

    private void panicEscape() {
        if (MyPlayer.getTeleblockState().isPresent()) {
            Log.warn("PKer attacking AND teleblocked. Walking out (likely death).");
            GlobalWalking.walkTo(GE_TILE);
            return;
        }
        Log.warn("PKer attacking. Panic-tele via ring.");
        teleToGE();
    }

    private void hopWorld() {
        Optional<World> target = Worlds.getRandomMembers();
        if (!target.isPresent()) { Log.warn("No members world available to hop."); return; }
        int num = target.get().getWorldNumber();
        if (num == WorldHopper.getCurrentWorld()) return;
        Log.info("Hopping to world " + num);
        if (WorldHopper.hop(num)) {
            Waiting.waitNormal(3500, 1000);
        }
    }

    private void useBonesOnAltar() {
        if (MyPlayer.getAnimation() != -1) { Waiting.waitNormal(900, 250); return; }
        Optional<GameObject> altar = Query.gameObjects().nameEquals(new String[]{ "Chaos altar" }).findFirst();
        Optional<InventoryItem> bone = Query.inventory().nameEquals(boneA()).findFirst();
        if (!altar.isPresent() || !bone.isPresent()) return;
        if (!altar.get().isVisible()) { altar.get().adjustCameraTo(); return; }
        Log.info("Using " + boneName + " on Chaos altar.");
        if (bone.get().useOn(altar.get())) {
            Waiting.waitUntil(4000, () -> MyPlayer.getAnimation() != -1);
        }
    }

    public static class BonerSettings {
        public String boneName = "Dragon bones";
        public boolean hopBetweenTrips = false;
        public boolean panicOnAttack = true;
    }

    private void applySettings(BonerSettings s) {
        boneName = s.boneName;
        hopBetweenTrips = s.hopBetweenTrips;
        panicOnAttack = s.panicOnAttack;
    }

    private BonerSettings collectSettings() {
        BonerSettings s = new BonerSettings();
        s.boneName = boneName;
        s.hopBetweenTrips = hopBetweenTrips;
        s.panicOnAttack = panicOnAttack;
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

    private BonerSettings loadProfile(String name) {
        try {
            return ScriptSettings.getDefault()
                    .load(SETTINGS_PREFIX + name, BonerSettings.class)
                    .orElseGet(BonerSettings::new);
        } catch (Exception e) { return new BonerSettings(); }
    }

    private void saveProfile(String name, BonerSettings s) {
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
        final BonerSettings initial = loadProfile(DEFAULT_PROFILE);
        applySettings(initial);
        final boolean[] ok = { false };
        try {
            SwingUtilities.invokeAndWait(() -> {
                JDialog dlg = new JDialog((Frame) null, "aBoner", true);
                dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

                JComboBox<String> boneBox = new JComboBox<>(new String[]{
                        "Dragon bones", "Big bones", "Superior dragon bones" });
                JCheckBox cbHop = new JCheckBox("Hop worlds between trips");
                JCheckBox cbPanic = new JCheckBox("Panic-tele via ring on PKer attack");
                JComboBox<String> profileBox = new JComboBox<>(getProfileNames().toArray(new String[0]));

                Runnable populate = () -> {
                    boneBox.setSelectedItem(boneName);
                    cbHop.setSelected(hopBetweenTrips);
                    cbPanic.setSelected(panicOnAttack);
                };
                Runnable collect = () -> {
                    boneName = (String) boneBox.getSelectedItem();
                    hopBetweenTrips = cbHop.isSelected();
                    panicOnAttack = cbPanic.isSelected();
                };
                populate.run();

                // --- Profile row ---
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

                JPanel boneP = new JPanel(new FlowLayout(FlowLayout.LEFT));
                boneP.setBorder(BorderFactory.createTitledBorder("Bones"));
                boneP.add(new JLabel("Type:"));
                boneP.add(boneBox);

                JPanel safetyP = new JPanel();
                safetyP.setLayout(new BoxLayout(safetyP, BoxLayout.Y_AXIS));
                safetyP.setBorder(BorderFactory.createTitledBorder("Safety"));
                safetyP.add(cbPanic);
                safetyP.add(cbHop);

                JPanel center = new JPanel();
                center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
                center.add(boneP);
                center.add(safetyP);

                JButton start = new JButton("Start");
                JButton cancel = new JButton("Cancel");
                start.addActionListener(e -> {
                    collect.run();
                    saveProfile(DEFAULT_PROFILE, collectSettings());
                    ok[0] = true; dlg.dispose();
                });
                cancel.addActionListener(e -> dlg.dispose());
                JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                btns.add(start); btns.add(cancel);

                dlg.setLayout(new BorderLayout());
                dlg.add(profileRow, BorderLayout.NORTH);
                dlg.add(center, BorderLayout.CENTER);
                dlg.add(btns, BorderLayout.SOUTH);
                dlg.pack(); dlg.setLocationRelativeTo(null); dlg.setVisible(true);
            });
        } catch (Exception e) { Log.error("Settings dialog failed: " + e.getMessage()); return false; }
        return ok[0];
    }
}
