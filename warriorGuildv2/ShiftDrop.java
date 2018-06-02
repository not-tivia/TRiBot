package scripts.warriorGuildv2;

import org.tribot.api.General;
import org.tribot.api.input.Keyboard;
import org.tribot.api2007.Inventory;
import org.tribot.api2007.Inventory.DROPPING_PATTERN;
import org.tribot.api2007.types.RSItem;

import java.awt.event.KeyEvent;
import java.util.ArrayList;

/**
 * Shift dropping
 *
 * @author volcom3d
 *
 */
public class ShiftDrop {
    public static void shiftDrop(DROPPING_PATTERN pattern, int... ids) {
        RSItem[] itemsToDrop;
        ArrayList<RSItem> itemsToDropOrdered = new ArrayList<RSItem>();
        if (ids.length == 0) {
            itemsToDrop = Inventory.getAll();
        } else {
            itemsToDrop = Inventory.find(ids);
        }

        // Ordering items.
        switch (pattern) {
            case LEFT_TO_RIGHT:
                for (RSItem item : itemsToDrop) {
                    itemsToDropOrdered.add(item);
                }
                break;
            case TOP_TO_BOTTOM:
                for (int i = 0; i < 4; i++) {
                    for (RSItem item : itemsToDrop) {
                        if (item.getIndex() % 4 == i) {
                            itemsToDropOrdered.add(item);
                        }
                    }
                }
                break;
            case ZIGZAG:
                for (int i = 0; i < 4; i++) {
                    for (RSItem item : itemsToDrop) {
                        if (item.getIndex() >= (0 + 8 * i) && item.getIndex() <= (3 + 8 * i)) {
                            itemsToDropOrdered.add(item);
                        }
                    }
                    for (int j = itemsToDrop.length - 1; j >= 0; j--) {
                        if (itemsToDrop[j].getIndex() >= (4 + 8 * i) && itemsToDrop[j].getIndex() <= (7 + 8 * i)) {
                            itemsToDropOrdered.add(itemsToDrop[j]);
                        }
                    }
                }
                break;
            default:
                break;
        }

        // Drop items.
        if (Inventory.open()) {
            Keyboard.sendPress(KeyEvent.CHAR_UNDEFINED, KeyEvent.VK_SHIFT);
            for (RSItem it : itemsToDropOrdered) {
                it.click(it.getDefinition().getName());
                General.sleep(General.randomSD(10, 1000, 50, 1));
            }
            Keyboard.sendRelease(KeyEvent.CHAR_UNDEFINED, KeyEvent.VK_SHIFT);
        }
    }

    public static void shiftDrop(int... ids) {
        int rand = General.random(1, 3);
        switch (rand) {
            case 1:
                shiftDrop( DROPPING_PATTERN.LEFT_TO_RIGHT, ids);
                break;
            case 2:
                shiftDrop( DROPPING_PATTERN.TOP_TO_BOTTOM, ids);
                break;
            case 3:
                shiftDrop( DROPPING_PATTERN.ZIGZAG, ids);
                break;
        }
    }

    public static void shiftDropAll() {
        shiftDrop();
    }


}