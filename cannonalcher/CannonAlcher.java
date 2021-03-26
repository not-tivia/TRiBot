package scripts.cannonalcher;

import org.tribot.api.Clicking;
import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.input.Keyboard;
import org.tribot.api.input.Mouse;
import org.tribot.api.util.abc.ABCUtil;
import org.tribot.api2007.*;
import org.tribot.api2007.ext.Filters;
import org.tribot.api2007.types.*;
import org.tribot.script.Script;
import org.tribot.script.ScriptManifest;
import org.tribot.script.interfaces.Arguments;
import org.tribot.script.interfaces.MessageListening07;
import org.tribot.script.interfaces.Painting;
import scripts.api.FluffeePaint.FluffeesPaint;
import scripts.api.FluffeePaint.PaintInfo;
import scripts.api.dax_api.api_lib.DaxWalker;
import scripts.api.dax_api.api_lib.models.DaxCredentials;
import scripts.api.dax_api.api_lib.models.DaxCredentialsProvider;
import scripts.api.dax_api.walker_engine.WalkingCondition;

import scripts.cannonalcher.gui.GUI;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.HashMap;

import static scripts.api.FluffeePaint.FluffeesPaint.PaintLocations.INVENTORY_AREA;
import static scripts.api.data.Constants.isOnStandardSpellbook;
import static scripts.api.items.ItemCheck.hasItem;
import static scripts.cannonalcher.actions.Magic.highAlch;
import static scripts.cannonalcher.actions.Magic.telegrabItem;
import static scripts.cannonalcher.data.Constants.startTime;
import static scripts.cannonalcher.data.Variables.*;

@ScriptManifest(authors = {"adamhackz"}, category = "Magic", name = "cannonAlcher", description = "A script that alchs, telegrabs, and uses your cannon", version = (0.01))


public class CannonAlcher extends Script implements Painting, PaintInfo, MessageListening07 {


    public final FluffeesPaint FLUFFEES_PAINT = new FluffeesPaint(this, INVENTORY_AREA, new Color[]{new Color(255, 251, 255)}, "Trebuchet MS", new Color[]{new Color(93, 156, 236, 127)},
            new Color[]{new Color(39, 95, 175)}, 1, false, 5, 3, 0);
    private final ABCUtil abcInstance = new ABCUtil();
    private final int eatAt = abcInstance.generateEatAtHP();

    private URL fxml;
    private GUI gui;
    private State scriptState;

    private int run_at = abcInstance.generateRunActivation();

    @Override
    public void onPaint(Graphics graphics) {
        FLUFFEES_PAINT.paint(graphics);
    }

    @Override
    public String[] getPaintInfo() {
        NumberFormat.getInstance();
        long runtime = (Timing.currentTimeMillis() - startTime) / 1000;
        RSObject[] cannon = Objects.findNearest(25, Filters.Objects.nameContains("cannon"));

        return new String[]{" Script state: " + scriptState, "Runtime: " + runtime, "Time " + NEXT_LOAD + "  " + (System.currentTimeMillis() - LAST_LOAD),

        };
    }


    private int getHpPercent() {
        return (Skills.getCurrentLevel(Skills.SKILLS.HITPOINTS) * 100) / Skills.getActualLevel(Skills.SKILLS.HITPOINTS);
    }

    private boolean needsToLoot() {
        if (!lootingEnabled || Inventory.isFull() || getHpPercent() <= eatAt) {
            return false;
        }
        //Might want to change this to loop through the lootlist and then
        // check if we can reach each one for occasions when more than one loot item is on the floor
        //Removed the reaching check so we can use this method for telegrabbing
        RSGroundItem[] lootlist = GroundItems.findNearest("Torstol seed", "Snapdragon seed", "Ranarr seed", "Long bone", "Curved bone");
        return lootlist.length > 0;
    }

    private boolean inArea() {
        if (Player.getRSPlayer() == null || Player.getPosition() == null) {
            return false;
        }
        return cannonArea.contains(Player.getRSPlayer().getPosition());
    }

    @Override
    public void serverMessageReceived(String arg0) {
        if (arg0.contains("Your cannon is out")) {
            reloadEnabled = true;
            General.println("We are out of cannonballs");
        }

        if (arg0.equals("That isn't your cannon!")) {
            General.println("Ending because we missclicked on someone elses cannon somehow");
            continueRunning = false;
        }

    }

    private boolean needsToRepair() {
        RSObject[] brokenCannon = Objects.findNearest(25, Filters.Objects.nameEquals("Broken multicannon").and(Filters.Objects.inArea(cannonArea)));
        return brokenCannon.length > 0;
    }

    private boolean needsToReload() {
        return reloadEnabled;
    }


    public State getState() {
        if (inGame()) {
            if (cannonPlaced) {
                if (needsToLoot()) {
                    return State.LOOTING;
                } else {
                    if (!inArea()) {
                        return State.WALK_TO_AREA;
                    } else {
                        if (!needsToReload()) {
                            if (!needsToRepair()) {
                                if (alchingEnabled) {
                                    return State.ALCHING;
                                } else {
                                    return State.AFKING;
                                }

                            } else {
                                return State.REPAIR_CANNON;
                            }
                        } else {
                            return State.RELOAD_CANNON;
                        }
                    }
                }

            } else {
                return State.SETTING_UP_CANNON;
            }
        } else {
            return State.LOGGED_OUT;
        }
    }

    @Override
    public void run() {

            try {
                fxml = new URL("https://raw.githubusercontent.com/not-tivia/TRiBot/master/cannonalcher/gui/cannonalcher.fxml");
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            gui = new GUI(fxml);
            gui.show();
            while (gui.isOpen()) {
                sleep(150);
            }

        if (onStart()) {
            while (continueRunning) {
                loop();
            }
        }


    }

    private boolean inGame() {
        return Login.getLoginState() == Login.STATE.INGAME;
    }

    private boolean onStart() {
        DaxWalker.setCredentials(new DaxCredentialsProvider() {
            @Override
            public DaxCredentials getDaxCredentials() {
                return new DaxCredentials("sub_DPjXXzL5DeSiPf", "PUBLIC-KEY");
            }
        });
        //Allows running during a daxwalker call
        DaxWalker.setGlobalWalkingCondition(() -> {
            if (!Game.isRunOn() && Game.getRunEnergy() >= run_at) {
                if (Options.setRunOn(true)) {
                    Timing.waitCondition(() -> Options.isRunEnabled(), General.random(600, 1100));
                    run_at = abcInstance.generateRunActivation();
                    General.println("Enabled run. Next run at " + run_at);
                }
            }

            return WalkingCondition.State.CONTINUE_WALKER;
        });

        Mouse.setSpeed(General.random(95, 280));
        if (!inGame()) {
            Timing.waitCondition(() -> inGame(), General.random(2500, 4100));
        }


        return true;
    }

    private void cannonTimer() {
        if (System.currentTimeMillis() - LAST_LOAD >= NEXT_LOAD) {
            reloadEnabled = true;
        }
    }

    public int loop() {
        scriptState = getState();
        General.sleep(50);
        cannonTimer();
        levelUpInterface();
        performTimedActions();
        switch (scriptState) {

            case LOOTING:

                RSGroundItem[] lootlist = GroundItems.findNearest("Torstol seed", "Snapdragon seed", "Ranarr seed", "Long bone", "Curved bone");

                if (telegrabbingLoot) {
                    if (!hasItem("Nature rune") || !hasItem("Air rune") || Inventory.isFull()) {
                        telegrabbingLoot = false;
                    }
                    if (lootlist.length > 0 && lootlist[0] != null) {
                        if (lootlist[0].isClickable() && lootlist[0].isOnScreen()) {
                            int item = lootlist[0].getID();
                            telegrabItem(item);
                            RSItemDefinition def = lootlist[0].getDefinition();
                            if (def != null) {
                                General.println("Attempting to loot " + lootlist[0].getDefinition().getName());

                            }
                        }
                    }

                } else {
                    if (Player.getPosition().distanceTo(cannonTile) >= 25 && PathFinding.canReach(cannonTile, false)) {
                        General.println("We got a little too far from our cannon");
                        if (WebWalking.walkTo(cannonTile)) {
                            Timing.waitCondition(() -> !Player.isMoving(), General.random(2500, 4100));
                        }
                    } else {
                        if (lootlist.length > 0 && lootlist[0] != null) {
                            if (lootlist[0].isClickable() && lootlist[0].isOnScreen()) {
                                if (Clicking.click("Take", lootlist[0])) {
                                    Timing.waitCondition(() -> !Player.isMoving() || lootlist.length < 1, General.random(2500, 4100));
                                    RSItemDefinition def = lootlist[0].getDefinition();
                                    if (def != null) {
                                        General.println("Attempting to loot " + lootlist[0].getDefinition().getName());

                                    }
                                }
                            } else {
                                if (DaxWalker.walkTo(lootlist[0].getPosition())) {
                                    Timing.waitCondition(() -> !Player.isMoving(), General.random(2500, 4100));
                                    RSItemDefinition def = lootlist[0].getDefinition();
                                    if (def != null) {
                                        General.println("Attempting to loot " + lootlist[0].getDefinition().getName());

                                    }
                                }
                            }

                        }
                    }
                }
                break;

            case AFKING:
                //We don't do anything specific here since we run our antiban actions in the loop
                break;


            case ALCHING:
                if (!hasItem("Nature rune") || !isOnStandardSpellbook()) {
                    General.println("Disabling high alchemy.");
                    alchingEnabled = false;
                }
                RSItem[] alchable = Inventory.find(alchName);
                if (alchable.length > 0 && alchable[0] != null) {
                    int alchID = alchable[0].getID();
                    RSItemDefinition def = alchable[0].getDefinition();
                    if (def != null) {
                        if (!def.isNoted()) {
                            alchID = alchable[0].getID() + 1;
                            //Here we add +1 to the item ID so we alch the noted version
                        }
                    }
                    if (alchable[0].getIndex() == 11) {
                        highAlch(alchID);
                    } else {
                        //This will line up the item directly with high alchemy so we move the mouse less
                        dragItemToSlot(alchable[0], 3, 4);
                        Timing.waitCondition(() -> alchable[0].getIndex() == 11, General.random(3000, 5000));
                    }
                }
                break;

            case LOGGED_OUT:
                General.println("We are logged out.");
                break;

            case REPAIR_CANNON:
                //we set the range to max range in case we are looting and the cannon breaks while we're far away from it :laugh:
                clickingFailsafe();
                RSObject[] brokenCannon = Objects.findNearest(25, Filters.Objects.nameEquals("Broken multicannon").and(Filters.Objects.inArea(cannonArea)));
                if (brokenCannon.length > 0) {
                    General.println("Broken cannon detected");
                    if (Clicking.click("Repair", brokenCannon[0])) {
                        Timing.waitCondition(() -> brokenCannon.length < 1, General.random(3000, 5000));

                    }
                }

                break;

            case WALK_TO_AREA:

                if (cannonTile.isOnScreen() && cannonTile.isClickable()) {
                    General.println("Screen walking to cannon tile");
                    if (Walking.clickTileMS(cannonTile, "Walk here")) {
                        Timing.waitCondition(() -> Player.getPosition().equals(cannonTile), General.random(4000, 7500));
                    }
                } else {
                    General.println("Walking to cannon tile");
                    if (DaxWalker.walkTo(cannonArea.getRandomTile())) {
                        Timing.waitCondition(() -> !Player.isMoving(), General.random(2500, 4100));
                    }
                }
                break;


            case SETTING_UP_CANNON:
                if (cannonArea.contains(Player.getPosition())) {
                    //Cannon base is the first piece we put down and furnace is the last piece we put down
                    if (cannonTile == null) {
                        cannonTile = Player.getPosition();
                    } else {
                        if (Player.getPosition().equals(cannonTile)) {
                            if (cannonTile != null) {
                                RSItem[] cannonbase = Inventory.find("Cannon base");
                                if (cannonbase.length > 0) {
                                    if (Clicking.click("Set-up", cannonbase[0])) {
                                        Timing.waitCondition(() -> !hasItem("Cannon furnace"), General.random(8000, 10000));
                                    }
                                } else {
                                    if (!hasItem("Cannon furnace") && !hasItem("Cannon base")) {
                                        //the first and last pieces
                                        RSObject[] cannon = Objects.find(3, "Dwarf multicannon");
                                        if (cannon.length > 0 && cannon[0] != null) {
                                            cannonPlaced = true;
                                        }
                                    }

                                }
                            } else {
                                General.println("Our cannonTile is null: " + cannonTile);
                            }
                        } else {
                            if (cannonTile.isOnScreen() && cannonTile.isClickable()) {
                                General.println("Screen walking to cannon tile");
                                if (Walking.clickTileMS(cannonTile, "Walk here")) {
                                    Timing.waitCondition(() -> Player.getPosition().equals(cannonTile), General.random(4000, 7500));
                                }
                            } else {
                                General.println("Walking to cannon tile");
                                if (DaxWalker.walkTo(cannonArea.getRandomTile())) {
                                    Timing.waitCondition(() -> !Player.isMoving(), General.random(2500, 4100));
                                }
                            }
                        }

                    }
                } else {
                    if (DaxWalker.walkTo(cannonArea.getRandomTile())) {
                        Timing.waitCondition(() -> !Player.isMoving(), General.random(2500, 4100));
                    }

                }

                break;



            case RELOAD_CANNON:
                    clickingFailsafe();
                    RSObject[] cannon = Objects.findNearest(25, Filters.Objects.nameContains("cannon").and(Filters.Objects.inArea(cannonArea)));
                    if (cannon.length > 0 && cannon[0] != null) {
                        RSItem[] cannonballs = Inventory.find(cannonballName);
                        if (cannonballs.length > 0) {
                            if (Player.getPosition().distanceTo(cannon[0]) < 15 && cannon[0].isOnScreen() && cannon[0].isOnScreen()) {
                                RSObjectDefinition definitionCheck = cannon[0].getDefinition();
                                if (definitionCheck != null) {
                                    if (cannon[0].getDefinition().getActions()[0].contains("Fire")) {

                                        if (System.currentTimeMillis() - LAST_LOAD >= NEXT_LOAD) {
                                            if (Clicking.click("Fire", cannon[0])) {
                                                General.sleep(1500, 3000);
                                                reloadEnabled = false;
                                                LAST_LOAD = System.currentTimeMillis();
                                                NEXT_LOAD = General.randomSD(7000, 75000, 45000, 8000);
                                            }
                                        }
                                    }
                                }
                            } else {

                                cannon[0].adjustCameraTo();
                                Timing.waitCondition(() -> cannon[0].isClickable(), General.random(2500, 4100));
                            }
                        } else {
                            General.println("We ran out of balls to shove in our tinsy winsy wittle cannon");
                            if (Clicking.click("Pick-up", cannon)) {
                                Timing.waitCondition(() -> cannon == null, General.random(2500, 4100));
                                continueRunning = false;
                            }
                        }
                    }


                break;

        }
        return 50;
    }



    private boolean dragItemToSlot(RSItem item, int row, int col, int... dev) {
        int x;
        int y;
        RSInterfaceChild i = Interfaces.get(149, 0);
        if (i == null)
            return false;

        x = (int) i.getAbsoluteBounds().getX() + 42 * (col - 1) + 16 + General.randomSD(-16, 16, 0, dev.length == 0 ? 6 : dev[0]);
        y = (int) i.getAbsoluteBounds().getY() + 36 * (row - 1) + 16 + General.randomSD(-16, 16, 0, dev.length == 0 ? 6 : dev[0]);

        item.hover();
        Mouse.drag(Mouse.getPos(), new Point(x, y), 1);
        return item.getIndex() == row * 4 + col;
    }


    public enum State {
        AFKING, ALCHING, SETTING_UP_CANNON, LOOTING, RELOAD_CANNON, REPAIR_CANNON, RETURN_TO_CANNON, LOGGED_OUT, WALK_TO_AREA
    }

    private void clickingFailsafe(){
        if (Game.getUptext()!=null) {
            if (Game.isUptext("->")) {
                Mouse.click(1);
                General.sleep(150, 200);
            }
        }
    }

    private void levelUpInterface(){
        RSInterface iface = NPCChat.getClickContinueInterface();
        if (Interfaces.isInterfaceSubstantiated(iface)) {
            iface.click();
            Timing.waitCondition(() -> !Interfaces.isInterfaceSubstantiated(iface), General.random(2300, 3100));
            General.println("closed level up interface");
        }
    }

    public void performTimedActions() {


        if (abcInstance.shouldCheckXP()) {
            abcInstance.checkXP();
            General.sleep(General.randomSD(750, 1500, 1000, 150)); // sleep makes sure it checks xp longer.
        }

        if (abcInstance.shouldExamineEntity())
            abcInstance.examineEntity();

        if (abcInstance.shouldMoveMouse())
            abcInstance.moveMouse();

        if (abcInstance.shouldPickupMouse())
            abcInstance.pickupMouse();

        if (abcInstance.shouldRightClick())
            abcInstance.rightClick();

        if (abcInstance.shouldRotateCamera())
            abcInstance.rotateCamera();


        if (abcInstance.shouldLeaveGame())

            abcInstance.leaveGame();

    }
}
