package scripts.aBoner2;

import org.tribot.script.sdk.Bank;
import org.tribot.script.sdk.ChatScreen;
import org.tribot.script.sdk.Combat;
import org.tribot.script.sdk.Equipment;
import org.tribot.script.sdk.Inventory;
import org.tribot.script.sdk.Log;
import org.tribot.script.sdk.MyPlayer;
import org.tribot.script.sdk.Waiting;
import org.tribot.script.sdk.antiban.Antiban;
import org.tribot.script.sdk.query.Query;
import org.tribot.script.sdk.script.TribotScript;
import org.tribot.script.sdk.script.TribotScriptManifest;
import org.tribot.script.sdk.types.Area;
import org.tribot.script.sdk.types.EquipmentItem;
import org.tribot.script.sdk.types.GameObject;
import org.tribot.script.sdk.types.InventoryItem;
import org.tribot.script.sdk.types.WorldTile;
import org.tribot.script.sdk.util.ScriptSettings;
import org.tribot.script.sdk.walking.GlobalWalking;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.Optional;

/*
 * CHANGELOG
 *   1.0.0 (2026-05-14) - Initial scoped-rewrite of aBoner using current SDK.
 *                        Loops GE <-> Chaos altar offering bones.
 *                        Burning amulet (Lava-Maze) out, Ring of wealth back.
 *                        SCOPE: does NOT handle PKer attacks, death recovery,
 *                        amulet/ring depletion replacement, world hopping,
 *                        or any of the original's WINE/LOCATOR/FANATIC suicide
 *                        modes. Assumes: player starts at GE with bones, charged
 *                        Burning amulet, and charged Ring of wealth in bank or
 *                        already equipped.
 *
 * KNOWN-FIX
 *   - Original's onStart() had `args.equals("Dragon bones")` (no-op). Replaced
 *     with proper GUI for bone selection saved via ScriptSettings.
 *   - Original used DaxWalker with paid credentials. GlobalWalking now wraps
 *     this in the SDK without credentials.
 *
 * OPEN
 *   - No anti-PK / escape logic. If a PKer attacks mid-walk you will likely die.
 *     Run with no risk in inventory beyond a single trip's bones.
 *   - No charge-tracking on Burning amulet or Ring of wealth. Once a worn item
 *     fully depletes, the equip check fails and the script stops. Bank in a
 *     fresh charged one before resuming.
 *   - Hardcoded wilderness path (10+ tiles) from the original is NOT used here;
 *     GlobalWalking handles it. If GlobalWalking takes a worse route than the
 *     hand-tuned path, swap to LocalWalking.walkPath(WILDY_PATH).
 *   - Wilderness teleport confirmation dialog (if any) is handled via
 *     ChatScreen.selectOption("Yes"). Verify the option text in-game.
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

    private volatile boolean running = true;
    private long lastProgressMs = System.currentTimeMillis();
    private static final long STUCK_TIMEOUT_MS = 5 * 60_000;

    private String boneName = "Dragon bones";
    private final String[] boneA() { return new String[]{ boneName }; }

    @Override
    public void execute(final String args) {
        if (args != null && !args.trim().isEmpty()) boneName = args.trim();
        else if (!showSettingsDialog()) { Log.info("Cancelled. Exiting."); return; }

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

        boolean atGE = GE_AREA.containsMyPlayer();
        boolean atAltar = ALTAR_AREA.containsMyPlayer();
        boolean hasBones = Inventory.contains(boneA());
        boolean amuletOn = isAmuletEquipped();
        boolean ringOn = isRingEquipped();

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

    private static class BonerSettings { public String boneName = "Dragon bones"; }

    private boolean showSettingsDialog() {
        final BonerSettings cur = loadSettings();
        boneName = cur.boneName;
        final boolean[] ok = { false };
        try {
            SwingUtilities.invokeAndWait(() -> {
                JDialog dlg = new JDialog((Frame) null, "aBoner", true);
                dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                JComboBox<String> boneBox = new JComboBox<>(new String[]{
                        "Dragon bones", "Big bones", "Superior dragon bones" });
                boneBox.setSelectedItem(cur.boneName);

                JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
                p.setBorder(BorderFactory.createTitledBorder("Bones to offer"));
                p.add(new JLabel("Bone:"));
                p.add(boneBox);

                JButton start = new JButton("Start");
                JButton cancel = new JButton("Cancel");
                start.addActionListener(e -> {
                    cur.boneName = (String) boneBox.getSelectedItem();
                    boneName = cur.boneName;
                    saveSettings(cur);
                    ok[0] = true; dlg.dispose();
                });
                cancel.addActionListener(e -> dlg.dispose());
                JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                btns.add(start); btns.add(cancel);

                dlg.setLayout(new BorderLayout());
                dlg.add(p, BorderLayout.CENTER);
                dlg.add(btns, BorderLayout.SOUTH);
                dlg.pack(); dlg.setLocationRelativeTo(null); dlg.setVisible(true);
            });
        } catch (Exception e) { Log.error("Settings dialog failed: " + e.getMessage()); return false; }
        return ok[0];
    }

    private BonerSettings loadSettings() {
        try {
            return ScriptSettings.getDefault()
                    .load("a_boner", BonerSettings.class)
                    .orElseGet(BonerSettings::new);
        } catch (Exception e) { return new BonerSettings(); }
    }

    private void saveSettings(BonerSettings s) {
        try { ScriptSettings.getDefault().save("a_boner", s); }
        catch (Exception e) { Log.warn("Failed to save settings: " + e.getMessage()); }
    }
}
