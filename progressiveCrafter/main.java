package scripts.progressiveCrafter;

import org.tribot.api.Clicking;
import org.tribot.api.DynamicClicking;
import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.input.Keyboard;
import org.tribot.api.input.Mouse;
import org.tribot.api.types.generic.Condition;
import org.tribot.api.types.generic.Filter;
import org.tribot.api.util.abc.ABCUtil;
import org.tribot.api2007.*;
import org.tribot.api2007.ext.Filters;
import org.tribot.api2007.types.*;
import org.tribot.script.Script;
import org.tribot.script.ScriptManifest;
import org.tribot.script.interfaces.Arguments;
import org.tribot.script.interfaces.Painting;

import java.awt.*;
import java.net.URL;
import java.util.HashMap;

@ScriptManifest(authors = { "adamhackz" }, category = "Crafting", name = "ProgressiveCrafter", description = "Craft", version = (0.01) )


public class main extends Script implements Painting {



    private RSItem[] needle = Inventory.find("Needle");
    private RSItem[] thread = Inventory.find("Thread");

    private boolean continuerunning = true;
    private boolean currentlyPerformingAction = false;


    private static ABCUtil abc = new ABCUtil();
    private State SCRIPT_STATE = getState();



    private boolean onStart() {
            General.println("Script Started");
            General.println("Welcome back " + General.getTRiBotUsername());

        return true;
    }


    public void run() {
        if (onStart()) {
            while (true) {
                if (continuerunning) {
                    General.sleep(General.randomSD(250, 1200, 725, 500));
                    loop();
                }
            }
        }
    }


    public void onPaint(Graphics g) {
        sleep(50);
        g.drawString("State: " + SCRIPT_STATE, 5, 80);
        //g.drawString("Running for: " + Timing.msToString(runtime), 5, 70);
    }

    private int loop() {
        SCRIPT_STATE = getState();
        General.sleep(General.random(50, 250));
        switch (SCRIPT_STATE) {

            case BANKING:
                if (!Banking.isBankScreenOpen()){
                    Banking.openBank();
                }

                break;
            case FLETCHING:


                if (continuouslyAnimating())
                    currentlyPerformingAction = true;
                    else
                    currentlyPerformingAction = false;
                if (!currentlyPerformingAction && !Interfaces.isInterfaceValid(270)){
                    combine();
                }


                if (Interfaces.isInterfaceValid(270)){

                    if (Skills.SKILLS.CRAFTING.getActualLevel() < 7) {
                        General.sleep(General.randomSD(250, 1200, 725, 500));
                        RSInterface i = Interfaces.get(270);
                        if (i != null) {
                            RSInterface c = i.getChild(14);
                            if (c != null) {
                                RSInterface gloves = Interfaces.get(270, 14);
                                General.sleep(General.randomSD(70, 210, 140, 70));
                                if (gloves != null) {
                                    gloves.click();

                                }
                            }

                        }
                    }
                    if (Skills.SKILLS.CRAFTING.getActualLevel() >= 7 && Skills.SKILLS.CRAFTING.getActualLevel() < 9){
                        General.sleep(General.randomSD(250, 1200, 725, 500));
                        RSInterface i = Interfaces.get(270);
                        if (i != null) {
                            RSInterface c = i.getChild(15);
                            if (c != null) {
                                RSInterface gloves = Interfaces.get(270, 15);
                                General.sleep(General.randomSD(70, 210, 140, 70));
                                if (gloves != null) {
                                    gloves.click();


                                }
                            }

                        }
                    }
                    if (Skills.SKILLS.CRAFTING.getActualLevel() >= 9 && Skills.SKILLS.CRAFTING.getActualLevel() < 11){
                        General.sleep(General.randomSD(250, 1200, 725, 500));
                        RSInterface i = Interfaces.get(270);
                        if (i != null) {
                            RSInterface c = i.getChild(16);
                            if (c != null) {
                                RSInterface gloves = Interfaces.get(270, 16);
                                General.sleep(General.randomSD(70, 210, 140, 70));
                                if (gloves != null) {
                                    gloves.click();


                                }
                            }

                        }
                    }
                    if (Skills.SKILLS.CRAFTING.getActualLevel() >= 11 && Skills.SKILLS.CRAFTING.getActualLevel() < 14){
                        General.sleep(General.randomSD(250, 1200, 725, 500));
                        RSInterface i = Interfaces.get(270);
                        if (i != null) {
                            RSInterface c = i.getChild(17);
                            if (c != null) {
                                RSInterface gloves = Interfaces.get(270, 17);
                                General.sleep(General.randomSD(70, 210, 140, 70));
                                if (gloves != null) {
                                    gloves.click();


                                }
                            }

                        }
                    }
                    if (Skills.SKILLS.CRAFTING.getActualLevel() >= 14 && Skills.SKILLS.CRAFTING.getActualLevel() < 18){
                        General.sleep(General.randomSD(250, 1200, 725, 500));
                        RSInterface i = Interfaces.get(270);
                        if (i != null) {
                            RSInterface c = i.getChild(18);
                            if (c != null) {
                                RSInterface gloves = Interfaces.get(270, 18);
                                General.sleep(General.randomSD(70, 210, 140, 70));
                                if (gloves != null) {
                                    gloves.click();


                                }
                            }

                        }
                    }
                    if (Skills.SKILLS.CRAFTING.getActualLevel() >= 18 && Skills.SKILLS.CRAFTING.getActualLevel() < 38){
                        General.sleep(General.randomSD(250, 1200, 725, 500));
                        RSInterface i = Interfaces.get(270);
                        if (i != null) {
                            RSInterface c = i.getChild(19);
                            if (c != null) {
                                RSInterface gloves = Interfaces.get(270, 19);
                                General.sleep(General.randomSD(70, 210, 140, 70));
                                if (gloves != null) {
                                    gloves.click();
                                }
                            }

                        }
                    }
                    if (Skills.SKILLS.CRAFTING.getActualLevel() >= 38) {
                        General.sleep(General.randomSD(250, 1200, 725, 500));
                        RSInterface i = Interfaces.get(270);
                        if (i != null) {
                            RSInterface c = i.getChild(20);
                            if (c != null) {
                                RSInterface gloves = Interfaces.get(270, 20);
                                General.sleep(General.randomSD(70, 210, 140, 70));
                                if (gloves != null) {
                                    gloves.click();
                                }
                            }
                        }

                    }
                }



                break;

            case WITHDRAW:
                if (Banking.isBankScreenOpen()) {
                    RSItem[] leathers = Banking.find("Leather");
                    if (leathers[0].getStack() < 1) {
                        continuerunning = false;
                    }
                    else continuerunning = true;
                    RSItem[] allitems = Inventory.find(Filters.Items.nameContains("Leather").combine(Filters.Items.nameNotEquals("Leather"), true));
                    RSItem[] coif = Inventory.find("Coif");
                    if (!hasleather() && allitems.length > 0 || coif.length > 0) {
                        General.sleep(600, 1200);
                         Banking.depositAllExcept(1733,1734);
                        General.sleep(600,1200);
                    }
                    if (!hasleather() && !(allitems.length > 0) && leathers.length > 0) {
                        General.sleep(600,1200);
                        if (Banking.withdraw(0, "Leather")) {
                            General.sleep(600,1200);
                            if (canCloseBank()){
                                closeBank();
                            }

                        }
                    }

                }
                break;

            case IDLING:
                int low = 0;
                int high = 10;
                int Result = General.random(low, high);
                if (Result <= 6) {
                    //do nothing
                }
                if (Result > 6 && Result <= 8){
                    General.sleep(General.randomSD(250, 1200, 725, 500));
                    abc.leaveGame();
                }
                if (Result > 8){
                    General.sleep(General.randomSD(250, 1200, 725, 500));
                    int low1 = 0;
                    int high1 = 10;
                    int Result1 = General.random(low, high);
                    if (Result1 > 3){
                        ExaminePlayer();
                    }
                    General.sleep(600,1200);
                    abc.moveMouse();
                }
                break;
        }


        return 50;
    }


    public enum State {
        BANKING,
        IDLING,
        FLETCHING, WITHDRAW
    }

    private State getState() {

        if (!isBankOpen() && !hasleather()) {
            return State.BANKING;
        }
        if (needle.length > 0 && thread.length > 0 && hasleather() && !isBankOpen()) {
            return State.FLETCHING;
        }
        if (isBankOpen() && !hasleather()){
            return State.WITHDRAW;
        }
        if (continuouslyAnimating()){
            return State.IDLING;
        }
        else return State.IDLING;
    }


    private boolean hasleather(){
        return Inventory.getCount("Leather") > 0;
    }


    private boolean isBankOpen(){
        if(Banking.isBankScreenOpen()){
            System.out.println("bank is open");
            return true;
        }
        else return false;
    }

    private boolean closeBank(){
        // General.sleep(General.randomSD(70, 210, 140, 70));
        if(Banking.isBankScreenOpen()){
            System.out.println("bank is closing");
            return Banking.close();
        }
        else return false;
    }

    public boolean openBank() {
        General.sleep(General.randomSD(250, 1200, 725, 500));
        if(!Banking.openBankBanker()){
            Banking.openBankBooth();
            System.out.println("opening bank");
        }
        return Banking.isBankScreenOpen();
    }

    private void closeInterface(){
        if (Interfaces.isInterfaceValid(233)) {
            General.sleep(General.randomSD(70, 210, 140, 70));
            RSInterface i = Interfaces.get(233);
            if (i != null) {
                RSInterface c = i.getChild(2);
                if (c != null) {
                    int low = 0;
                    int high = 10;
                    int Result = General.random(low, high);
                    if (Result >= 7) {
                        General.sleep(General.randomSD(70, 210, 140, 70));
                        Keyboard.typeSend(" ");
                        General.sleep(General.randomSD(250, 1200, 725, 500));
                    }
                    if (Result <= 6) {
                        RSInterface clicktocontinue = Interfaces.get(233, 2);
                        General.sleep(General.randomSD(70, 210, 140, 70));
                        if (clicktocontinue != null) {
                            General.sleep(General.randomSD(70, 210, 140, 70));
                            clicktocontinue.click();
                        }
                    }
                }
            }
        }
    }


    private boolean combine() {
        RSItem[] needle = Inventory.find("Needle");
        RSItem[] leatherz = Inventory.find("Leather");
        if (hasleather()){
            return needle[0].click() && leatherz[0].click();
        }
       return true;
    }

    public static boolean continuouslyAnimating() {
        return Timing.waitCondition(new Condition() {
            @Override
            public boolean active() {
                return Player.getAnimation() != -1;
            }
        }, General.random(2000, 3000));

    }

    public static boolean canCloseBank() {
        return Timing.waitCondition(new Condition() {
            @Override
            public boolean active() {
                return Inventory.getCount("Leather") > 0;
            }
        }, General.random(2000, 3000));

    }

    private static void ExaminePlayer(){
        RSPlayer[] players = Players.getAll(new Filter<RSPlayer>() {
            @Override
            public boolean accept(RSPlayer rsPlayer) {
                if (rsPlayer != null && rsPlayer.getName() != null &&rsPlayer.getModel() != null)
                    return rsPlayer.isOnScreen();
                return false;
            }
        });
        if (players != null && players.length > 0){
            Camera.turnToTile(players[0]);
            if (players[0].isClickable()){
                Clicking.hover(players[0]);
                Mouse.click(3);
                General.sleep(120, 1300);
                abc.moveMouse();
            }
        }

    }

}


