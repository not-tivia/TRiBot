package scripts.cannonalcher;

import org.tribot.api.Clicking;
import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.input.Mouse;
import org.tribot.api.util.abc.ABCUtil;
import org.tribot.api2007.*;
import org.tribot.api2007.ext.Filters;
import org.tribot.api2007.types.*;
import org.tribot.script.Script;
import org.tribot.script.interfaces.Arguments;
import org.tribot.script.interfaces.Breaking;
import org.tribot.script.interfaces.MessageListening07;
import org.tribot.script.interfaces.Painting;
import scripts.api.FluffeePaint.FluffeesPaint;
import scripts.api.FluffeePaint.PaintInfo;
import scripts.api.dax_api.api_lib.DaxWalker;
import scripts.api.dax_api.api_lib.models.DaxCredentials;
import scripts.api.dax_api.api_lib.models.DaxCredentialsProvider;
import scripts.api.dax_api.walker_engine.WalkingCondition;

import scripts.cannonClicker.Data.Vars;
import scripts.cannonalcher.gui.GUI;


import java.awt.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.HashMap;


import static scripts.api.FluffeePaint.FluffeesPaint.PaintLocations.INVENTORY_AREA;
import static scripts.api.data.Constants.isOnStandardSpellbook;
import static scripts.api.items.ItemCheck.hasItem;
import static scripts.cannonalcher.actions.Magic.highAlch;
import static scripts.cannonalcher.data.Constants.startTime;
import static scripts.cannonalcher.data.Variables.*;


public class CannonAlcher extends Script implements Painting, PaintInfo, MessageListening07, Arguments {


    public final FluffeesPaint FLUFFEES_PAINT = new FluffeesPaint(this, INVENTORY_AREA, new Color[]{new Color(255, 251, 255)}, "Trebuchet MS", new Color[]{new Color(93, 156, 236, 127)},
            new Color[]{new Color(39, 95, 175)}, 1, false, 5, 3, 0);
    /*
    Gui stuff
     */
    private URL fxml;
    private GUI gui;

    private State scriptState;
    private String args;
    private final ABCUtil abcInstance = new ABCUtil();
    private int run_at = abcInstance.generateRunActivation();


    @Override
    public void onPaint(Graphics graphics) {
        FLUFFEES_PAINT.paint(graphics);
    }

    @Override
    public String[] getPaintInfo() {
        NumberFormat.getInstance();
        long runtime = (Timing.currentTimeMillis() - startTime) / 1000;
        return new String[]{" Script state: " + scriptState, "Runtime: " + runtime};
    }

    @Override
    public void passArguments(HashMap<String, String> hashMap) {
        General.println("Argument " + hashMap + "has been successfully passed");
        if (hashMap.containsKey("custom_input")) {
            args = hashMap.get("custom_input");
        } else if (hashMap.containsKey("autostart")) {
            args = hashMap.get("autostart");
        }
        for (String arg : args.split(",")) {
            if (!args.isEmpty()) {
                //General.println("We will need " + requiredInventorySpace + " free spaces." + "Tasks: " + tasks.toString());
            }
        }
    }


    private int getHpPercent() {
        return (Skills.getCurrentLevel(Skills.SKILLS.HITPOINTS) * 100) / Skills.getActualLevel(Skills.SKILLS.HITPOINTS);
    }

    private final int eatAt = abcInstance.generateEatAtHP();
    private boolean needsToLoot() {
        if (!lootingEnabled || Inventory.isFull() || getHpPercent() <= eatAt){
            return false;
        }
        //Might want to change this to loop through the lootlist and then
        // check if we can reach each one for occasions when more than one loot item is on the floor
        RSGroundItem[] lootlist = GroundItems.findNearest("Torstal seed", "Snapdragon seed", "Ranarr seed", "Long bone", "Curved bone");
        return lootlist.length > 0 && PathFinding.canReach(lootlist[0].getPosition(), false);
    }

    private boolean inArea(){
        if (cannonArea==null || cannonTile==null || Player.getRSPlayer()==null || Player.getPosition()==null){
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

    private boolean needsToRepair(){
        RSObject[] brokenCannon = Objects.findNearest(25, Filters.Objects.nameEquals("Broken multicannon").and(Filters.Objects.tileEquals(cannonTile)));
        return brokenCannon.length > 0;
    }

    private boolean needsToReload(){
        return reloadEnabled;
    }







    public State getState() {
        if (inGame()){
            if (cannonPlaced){
                if (needsToLoot()){
                    return State.LOOTING;
                } else {
                    if (!inArea()){
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
        if (args.isEmpty()) {
            try {
                fxml = new URL("https://raw.githubusercontent.com/not-tivia/TRiBot/master/cluehuntercollector/gui/Cluehunter.fxml");
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            gui = new GUI(fxml);
            gui.show();
            while (gui.isOpen()) {
                sleep(150);
            }
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

    private void cannonTimer(){
        if (System.currentTimeMillis() - LAST_LOAD >= NEXT_LOAD) {
            reloadEnabled = true;
        }
    }
    public int loop() {
        scriptState = getState();
        General.sleep(50);
        cannonTimer();


        switch (scriptState) {

            case LOOTING:
                RSGroundItem[] lootlist = GroundItems.findNearest("Torstal seed", "Snapdragon seed", "Ranarr seed", "Long bone", "Curved bone");
                if (Player.getPosition().distanceTo(Vars.get().CANNON_TILE) >= 25 && PathFinding.canReach(cannonTile, false)) {
                    General.println("We got a little too far from our cannon");
                    if (WebWalking.walkTo(cannonTile)) {
                        Timing.waitCondition(() -> !Player.isMoving(), General.random(2500, 4100));
                    }
                } else {
                    if (lootlist.length > 0 && lootlist[0] != null) {
                        if (lootlist[0].isClickable() && lootlist[0].isOnScreen()) {
                            if (Clicking.click("Take", lootlist[0])) {
                                Timing.waitCondition(() -> !Player.isMoving() || lootlist.length < 1, General.random(2500, 4100));
                                General.println("Attempting to loot " + lootlist[0].getDefinition().getName());
                            }
                        } else {
                            if (DaxWalker.walkTo(lootlist[0].getPosition())) {
                                Timing.waitCondition(() -> !Player.isMoving(), General.random(2500, 4100));
                                General.println("walking to loot " + lootlist[0].getDefinition().getName());
                            }
                        }

                    }
                }
                break;

            case AFKING:
                //We don't do anything specific here since we run our antiban actions in the loop
                break;

            case ENDING:



                break;

            case ALCHING:
                if (!hasItem("Nature rune") || !isOnStandardSpellbook()){
                    General.println("Disabling high alchemy.");
                    alchingEnabled = false;
                }
                RSItem[] alchable = Inventory.find(alchName);
                if (alchable.length > 0 && alchable[0]!=null){
                    //Here we add +1 to the item ID so we alch the noted version
                    int alchID = alchable[0].getID() + 1;
                    if (alchable[0].getIndex() == 11){
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

                RSObject[] brokenCannon = Objects.findNearest(25, Filters.Objects.nameEquals("Broken multicannon").and(Filters.Objects.tileEquals(cannonTile)));
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
                        Timing.waitCondition(() -> Player.getPosition().equals(cannonTile) || !Player.isMoving(), General.random(2500, 4100));
                    }
                } else {
                    General.println("Walking to cannon tile");
                    if (DaxWalker.walkTo(cannonArea.getRandomTile())) {
                        Timing.waitCondition(() -> !Player.isMoving(), General.random(2500, 4100));
                    }
                }
                break;


            case SETTING_UP_CANNON:
                if (cannonArea.contains(Player.getPosition())){
                    //Cannon base is the first piece we put down and furnace is the last piece we put down
                    if (cannonTile==null){
                        cannonTile = Player.getPosition();
                    } else {
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
                    }
                } else {
                    if (DaxWalker.walkTo(cannonArea.getRandomTile())) {
                        Timing.waitCondition(() -> !Player.isMoving(), General.random(2500, 4100));
                    }

                }

                break;

            case DISMISS_INTERFACES:
                break;


            case RELOAD_CANNON:
                //Check if the cannonball count goes down, if it does the load was successful
                LAST_LOAD = System.currentTimeMillis();
                NEXT_LOAD = General.randomSD(7000, 45000,32000,3000);
                break;

        }
        return 50;
    }






    private int openInventorySpots() {
        return 28 - Inventory.getAll().length;
    }



    private boolean dragItemToSlot(RSItem item, int row, int col, int... dev){
        int x; int y;
        RSInterfaceChild i = Interfaces.get(149,0);
        if (i == null)
            return false;

        x = (int)i.getAbsoluteBounds().getX()+42*(col-1)+16+General.randomSD(-16, 16, 0, dev.length == 0 ? 6 : dev[0]);
        y = (int)i.getAbsoluteBounds().getY()+36*(row-1)+16+General.randomSD(-16, 16, 0, dev.length == 0 ? 6 : dev[0]);

        item.hover();
        Mouse.drag(Mouse.getPos(),new Point(x, y),1);
        return item.getIndex() == row*4+col;
    }



    public enum State {
        AFKING, ALCHING, SETTING_UP_CANNON, LOOTING, RELOAD_CANNON,REPAIR_CANNON,RETURN_TO_CANNON,ENDING,DISMISS_INTERFACES,LOGGED_OUT,WALK_TO_AREA
    }
}
