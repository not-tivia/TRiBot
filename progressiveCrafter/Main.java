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

import java.util.Optional;

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

    @Override
    public void execute(final String args) {
        Log.info("ProgressiveCrafter started.");
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
            Bank.withdrawAll(NEEDLE);
            Waiting.waitUntil(1500, () -> Inventory.contains(NEEDLE_A));
        }
        if (!hasThread) {
            Bank.withdrawAll(THREAD);
            Waiting.waitUntil(1500, () -> Inventory.contains(THREAD_A));
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
        int childIdx = childForLevel(level);
        String itemName = itemForLevel(level);

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
}
