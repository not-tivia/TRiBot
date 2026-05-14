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
import org.tribot.script.sdk.walking.GlobalWalking;

import java.util.List;
import java.util.Optional;

/*
 * CHANGELOG
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

    private volatile boolean running = true;
    private long lastProgressMs = System.currentTimeMillis();
    private static final long STUCK_TIMEOUT_MS = 5 * 60_000;

    @Override
    public void execute(final String args) {
        Log.info("aBowStringer started.");
        Antiban.setScriptAiAntibanEnabled(true);
        while (running) {
            try { tick(); } catch (Exception e) { Log.error("Tick failed: " + e.getMessage()); }
            Waiting.waitNormal(350, 120);
        }
        Log.info("aBowStringer stopping.");
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

        boolean useFlax = Skill.CRAFTING.getActualLevel() >= 10;
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
            Optional<Widget> master = Widgets.get(new int[]{ 270 });
            if (!master.isPresent()) return;
            List<Widget> children = master.get().getChildren();
            if (children == null) return;
            for (int i = children.size() - 1; i >= 15; i--) {
                Widget c = children.get(i);
                if (c != null && c.isVisible()) {
                    Log.info("Clicking 270." + i + " (" + (useFlax ? BOW_STRING : BALL_OF_WOOL) + ")");
                    if (c.click()) Waiting.waitUntil(4000, () -> MyPlayer.getAnimation() != -1);
                    return;
                }
            }
            Log.warn("Make-X open but no clickable item. Pressing Escape.");
            Keyboard.pressEscape();
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
}
