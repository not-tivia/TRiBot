package scripts.warriorsGuild;

import org.tribot.api.Clicking;
import org.tribot.api.DynamicClicking;
import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.input.Mouse;
import org.tribot.api.types.generic.Condition;
import org.tribot.api.util.abc.ABCUtil;
import org.tribot.api2007.*;
import org.tribot.api2007.ext.Filters;
import org.tribot.api2007.types.RSGroundItem;
import org.tribot.api2007.types.RSItem;
import org.tribot.api2007.types.RSObject;
import org.tribot.script.Script;
import org.tribot.script.ScriptManifest;
import scripts.warriorsGuild.webwalker_logic.local.walker_engine.interaction_handling.AccurateMouse;


@ScriptManifest(authors = { "adamhackz" }, category = "Combat", name = "afkTokens", description = "afks Tokens", version = (0.03) )

public class main extends Script {

    private State SCRIPT_STATE = getState();
    private ACamera aCamera = new ACamera(this);
    boolean doeat = false;
    int eat_at = this.abc.generateEatAtHP();
    private static ABCUtil abc = new ABCUtil();
    private boolean onStart() {
        return true;
    }


    public void run() {
        Mouse.setSpeed(General.random(83, 103));
        if (onStart()) {
            while (true) {
                loop();
                General.sleep(150,490);

            }
        }

    }

    private State getState() {

        RSGroundItem[] tokens = GroundItems.find("Warrior guild token");
        if (!aHelper.underAttack() && hasArmour() && !(tokens.length > 0) && !needtoEat()) {
            return State.ANIMATING;
        }
        if (aHelper.underAttack() && !needtoEat()){
            return State.ATTACKING;
        }
        if (!hasArmour() && !aHelper.underAttack() && !needtoEat()){
            return State.LOOTING;
        }
        if (tokens.length > 0 && !needtoEat()){
            return  State.LOOTING;
        }
        if (needtoEat()){
            return State.EATING;
        }

        else return State.IDLE;
    }

    public enum State {
    ATTACKING,
        LOOTING,
        ANIMATING,
        IDLE, EATING

    }

    private boolean needtoEat() {
        // General.sleep(General.randomSD(156, 463, 206, 92));

        int healing = 0;
        int maxhp = Skills.getActualLevel(Skills.SKILLS.HITPOINTS) - General.random(0, 4);
        doeat = false;
        int currenthp = Skills.getCurrentLevel(Skills.SKILLS.HITPOINTS);
        int newhp = (healing + currenthp);

        int newhp7 = (maxhp - currenthp);
        int newhp8 = newhp7 - healing - General.random(0, 5);
        if (newhp7 > 0 && newhp7 <= maxhp && healing <= newhp8 && currenthp <= this.abc.generateEatAtHP()) {
            return true;
        }
        else return false;
    }
    private int loop() {
        SCRIPT_STATE = getState();
        General.sleep(150, 250);
        println(SCRIPT_STATE);

        switch (SCRIPT_STATE) {

             case LOOTING:
                 General.sleep(General.randomSD(250, 1200, 725, 500));
                 pickupArmour();
                 pickupTokens();
                 break;

            case ANIMATING:
                General.sleep(General.randomSD(250, 1200, 725, 500));
            animateArmour();
                break;

            case ATTACKING:
                General.sleep(General.randomSD(250, 1200, 725, 500));
                Mouse.leaveGame();
                break;

            case IDLE:
                General.sleep(General.randomSD(250, 1200, 725, 500));
                //Mouse.leaveGame();
                break;

            case EATING:
                eatBest();
                break;
        }
            return 50;
        }

    private boolean hasArmour(){
        RSItem[] armour1 = Inventory.find("Mithril platebody");
        RSItem[] armour2 = Inventory.find("Mithril platelegs");
        RSItem[] armour3 = Inventory.find( "Mithril full helm");
        if (armour1.length > 0 && armour2.length >0 && armour3.length>0) {
            return true;
        }
        else return false;
    }

    private boolean animateArmour(){
        if (hasArmour()) {
            RSObject[] animate = Objects.find(10, 23955);
            if (animate.length > 0){
                General.sleep(General.randomSD(250, 1200, 725, 500));
                DynamicClicking.clickRSObject(animate[0], 1);
                int low = 1;
                int high = 3;
                int Result = General.random(low, high);
                if (Result > 2) {
                    aCamera.setCameraRotation(General.random( 80,192));
                }

                Timing.waitCondition(new Condition() {
                    @Override
                    public boolean active() {
                        General.sleep(General.randomSD(70, 210, 140, 70));
                        return (NPCs.find("Animated Mithril Armour").length > 0 );
                    }
                }, General.random(1000, 1200));


            }return true;
        }
        else return false;
    }

    private boolean pickupArmour(){
        RSGroundItem[] armourset = GroundItems.find("Mithril platebody", "Mithril platelegs", "Mithril full helm");
        if (armourset.length > 0){
            General.sleep(General.randomSD(70, 210, 140, 70));
            if (!Game.getUptext().contains("Mithril")){
                AccurateMouse.click(armourset[0], "Take");
                //DynamicClicking.clickRSGroundItem(armourset[0], 1);
                General.sleep(30, 80);
            }

            if (Game.getUptext().contains("Mithril")){
                General.sleep(General.randomSD(70, 210, 140, 70));
                Mouse.click(1);
                General.sleep(General.randomSD(70, 210, 140, 70));
                Mouse.click(1);
            }
            return true;

        }
        else return false;
    }
    private boolean pickupTokens(){
        RSGroundItem[] tokens = GroundItems.find("Warrior guild token");
        if (tokens.length > 0 && hasArmour()){
            DynamicClicking.clickRSGroundItem(tokens[0], 1);
            return true;

        }
        else return false;
    }

    private void eatBest() {
        int healing = 0;
        int maxhp = Skills.getActualLevel(Skills.SKILLS.HITPOINTS) - General.random(0, 4);
        doeat = false;
        int currenthp = Skills.getCurrentLevel(Skills.SKILLS.HITPOINTS);
        int newhp = (healing + currenthp);

        int newhp7 = (maxhp - currenthp);
        int newhp8 = newhp7 - healing - General.random(0, 5);
        RSItem[] wine = Inventory.find("Jug of wine");
        if (wine.length > 0 && wine != null) {
            if (wine[0].getDefinition().getName().equals("Jug of wine")) {
                healing = 11;
                General.println(wine[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (newhp7 > 0) {
                if (newhp7 <= maxhp) {
                    General.println("you need to heal " + newhp7 + " to be at max health");
                    if (healing <= newhp8) {
                        General.println("You will heal for " + newhp8);
                        General.sleep(General.randomSD(156, 463, 206, 92));
                        General.println("You will eat at " + this.abc.generateEatAtHP());
                        if (currenthp <= this.abc.generateEatAtHP()) {
                            if (GameTab.getOpen() != GameTab.TABS.INVENTORY) {
                                GameTab.open(GameTab.TABS.INVENTORY);
                            }
                            wine[0].click("Drink");
                            General.sleep(General.randomSD(156, 463, 206, 92));
                            General.println("Ate food..");
                            for (int i = 0; i < 10 && Player.getAnimation() == 829; i++) {
                                sleep(90, 110);
                                this.eat_at = this.abc.generateEatAtHP();
                            }
                        }
                    }
                }
            }
        }

        RSItem[] food = Inventory.find(Filters.Items.actionsContains("Eat"));
        if (food.length > 0 && food != null) {
            //General.println("Food was found");
            if (food[0].getDefinition().getName().equals("Shrimp")) {
                healing = 3;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Cooked chicken")) {
                healing = 3;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Cooked meat")) {
                healing = 3;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Sardine")) {
                healing = 4;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Bread")) {
                healing = 5;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Herring")) {
                healing = 20;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Trout")) {
                healing = 7;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Tuna")) {
                healing = 10;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().contains("cake")) {
                healing = 4;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints per a slice.");
            }
            if (food[0].getDefinition().getName().equals("Lobster")) {
                healing = 12;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Bass")) {
                healing = 13;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Swordfish")) {
                healing = 14;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Potato with butter")) {
                healing = 14;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Potato with cheese")) {
                healing = 16;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Monkfish")) {
                healing = 16;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Cooked karambwan")) {
                healing = 18;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Ugthanki kebab")) {
                healing = 19;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Sea turtle")) {
                healing = 21;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Shark")) {
                healing = 20;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().contains("Pineapple pizza")) {
                healing = 11;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints per a slice.");
            }
            if (food[0].getDefinition().getName().equals("Manta ray")) {
                healing = 22;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Tuna potato")) {
                healing = 22;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Anglerfish")) {
                healing = 22;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (newhp7 > 0) {
                if (newhp7 <= maxhp) {
                    General.println("you need to heal " + newhp7 + " to be at max health");
                    if (healing <= newhp8) {
                        General.println("You will heal for " + newhp8);
                        General.sleep(General.randomSD(156, 463, 206, 92));
                        General.println("You will eat at " + this.abc.generateEatAtHP());
                        if (currenthp <= this.abc.generateEatAtHP()) {
                            if (GameTab.getOpen() != GameTab.TABS.INVENTORY) {
                                GameTab.open(GameTab.TABS.INVENTORY);
                            }
                            food[0].click("Eat");
                            General.sleep(General.randomSD(156, 463, 206, 92));
                            General.println("Ate food..");
                            for (int i = 0; i < 10 && Player.getAnimation() == 829; i++) {
                                sleep(90, 110);
                                this.eat_at = this.abc.generateEatAtHP();
                            }
                        }
                    }
                }
            }
        }
    }

}