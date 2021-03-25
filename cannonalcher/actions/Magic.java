package scripts.cannonalcher.actions;

import org.tribot.api.Clicking;
import org.tribot.api.DynamicClicking;
import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.input.Mouse;
import org.tribot.api2007.*;
import org.tribot.api2007.types.RSGroundItem;
import org.tribot.api2007.types.RSInterfaceChild;
import org.tribot.api2007.types.RSItem;
import scripts.adamapi.RunePouch;

import java.awt.*;

import static scripts.api.items.ItemCheck.hasItem;
import static scripts.api.items.ItemCheck.hasItemStack;
import static scripts.cannonalcher.data.Variables.alchingEnabled;

public class Magic {
    public static void highAlch(int itemID){
         /*
        Animation check if we are alching
        Check if uptext is "->" to check we are are hovering a spell over an item
        if it isnt, then check if the magic tab is open
        if it isnt, then check if we are hovering over the spell
        if we are then click
        if we are not then select spell
        if spell is selected then confirm the correct spell is selected and click our item
         */
        RSItem[] item = Inventory.find(itemID);
        if (item.length > 0) {
            if (Player.getAnimation() < 1) {
                if (!Game.isUptext("->")) {
                    if (!GameTab.TABS.MAGIC.isOpen()) {
                        GameTab.open(GameTab.TABS.MAGIC);
                        Timing.waitCondition(() -> !GameTab.open(GameTab.TABS.MAGIC), General.random(900,1300));
                    } else {
                        if (Game.isUptext("Cast High Level Alchemy")) {
                            Mouse.click(1);
                            General.sleep(450, 900);
                        } else {
                            if (Game.getUptext().contains("Cast High Level Alchemy ->")) {
                                Mouse.click(1);
                                General.sleep(450, 900);
                            } else {
                                org.tribot.api2007.Magic.selectSpell("High Level Alchemy");
                                Timing.waitCondition(() -> Game.isUptext("->"), General.random(500, 900));
                            }
                        }
                    }

                } else {
                    if (org.tribot.api2007.Magic.getSelectedSpellName().equals("High Level Alchemy")) {
                        Clicking.click(item[0]);
                        General.sleep(300, 900);
                    }
                }
            } else {
                General.println("Casting high alch");
            }
        } else {
            General.println("We cannot find our alchin item");
        }
    }


    public static void telegrabItem(int itemID){
        RSGroundItem[] item = GroundItems.find(itemID);
        if (item.length > 0) {
            if (Player.getAnimation() < 1) {
                if (!Game.isUptext("->")) {
                    if (!GameTab.TABS.MAGIC.isOpen()) {
                        GameTab.open(GameTab.TABS.MAGIC);
                        Timing.waitCondition(() -> !GameTab.open(GameTab.TABS.MAGIC), General.random(900,1300));
                    } else {
                        if (Game.isUptext("Telekinetic Grab")) {
                            Mouse.click(1);
                            General.sleep(600, 1600);
                        } else {
                            if (Game.getUptext().contains("Cast Telekinetic Grab ->")) {
                                Mouse.click(1);
                                General.sleep(600, 1600);
                            } else {
                                org.tribot.api2007.Magic.selectSpell("Telekinetic Grab");
                                Timing.waitCondition(() -> Game.isUptext("->"), General.random(500, 900));
                            }
                        }
                    }

                } else {
                    if (org.tribot.api2007.Magic.getSelectedSpellName().equals("Telekinetic Grab")) {
                        DynamicClicking.clickRSGroundItem(item[0],"Cast");
                        General.sleep(3500, 8500);
                    }
                }
            } else {
                General.println("Casting telegrab");
            }
        } else {
            General.println("We cannot find our telegrab item");
        }
    }


}
