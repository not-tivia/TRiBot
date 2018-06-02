package scripts.warriorGuildv2;

import org.tribot.api.Clicking;
import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.types.generic.Condition;
import org.tribot.api2007.Interfaces;
import org.tribot.api2007.Inventory;
import org.tribot.api2007.types.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

public class OSShopping {

    private static final int MASTER_ID = 300;
    private static final int FRAME_CHILD_ID = 1;
    private static final int ITEMS_CHILD_ID = 2;

    public static boolean isShopOpen() {
        return Interfaces.get(300, 1) != null;
    }

    public static String getShopName() {
        RSInterfaceChild frame = Interfaces.get(MASTER_ID, FRAME_CHILD_ID);
        if (frame != null) {
            return frame.getText();
        }
        return "";
    }


    public static boolean close() {
        RSInterfaceChild close = Interfaces.get(300, 1);
        if (close != null) {
            RSInterfaceComponent closeButton = close.getChild(11);
            return closeButton != null && closeButton.click("Close") &&
                    Timing.waitCondition(new Condition() {
                @Override
                public boolean active() {
                    return !isShopOpen();
                }
            }, 2000);
        }
        return false;
    }


    public static boolean inStock(int Master, int child, int component, int... ItemID){
        //String detectactions = Interfaces.get(Master,child,component).getComponentName().trim();
        int detectactions = Interfaces.get(Master,child,component).getComponentItem();
        General.println( detectactions );
        if (ItemID[0] == detectactions && ItemID.length > 0){
            return true;
        }
        else {
            return false;
        }
    }

    //Minimum in shop means the shop must have atleast that much of the item before attempting to purchase
    //Amount to buy should be "Buy 1", "Buy 5", "Buy 10", or "Buy 50"
    public static boolean buy(int Master, int child, int component, int MinimumInShop, String... Amounttobuy) {
        String[] detectactions = Interfaces.get( Master, child, component).getActions();
        int avaliable = Interfaces.get(Master, child, component).getComponentStack();
        RSInterface buyactions = Interfaces.get(Master, child, component);
        if (Interfaces.isInterfaceValid(Master) && detectactions.length > 0 && avaliable > MinimumInShop  ) {
            General.sleep( 500,1200 );
        }
        return Clicking.click(Amounttobuy, buyactions);
    }

}