package scripts.gargoyleSlayer;

import org.tribot.api.Clicking;
import org.tribot.api.DynamicClicking;
import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.input.Keyboard;
import org.tribot.api.input.Mouse;
import org.tribot.api.interfaces.Clickable;
import org.tribot.api.types.generic.Condition;
import org.tribot.api.util.abc.ABCUtil;
import org.tribot.api2007.*;
import org.tribot.api2007.Objects;
import org.tribot.api2007.ext.Filters;
import org.tribot.api2007.types.*;
import org.tribot.script.Script;
import org.tribot.script.ScriptManifest;
import org.tribot.script.interfaces.Arguments;
import org.tribot.script.interfaces.MessageListening07;
import org.tribot.script.interfaces.Painting;
import scripts.gargoyleSlayer.a_helper.RunePouch;
import scripts.gargoyleSlayer.a_helper.other_api.util.Hovering;
import scripts.gargoyleSlayer.a_helper.other_api.util.Movement;
import scripts.gargoyleSlayer.dax_api.api_lib.DaxWalker;
import scripts.gargoyleSlayer.dax_api.api_lib.models.DaxCredentials;
import scripts.gargoyleSlayer.dax_api.api_lib.models.DaxCredentialsProvider;


import java.awt.*;
import java.util.*;


@ScriptManifest(authors = { "adamhackz" }, category = "aCustom", name = "Private Epic Slayer", description = "Insert monster name into arguments, supports all monsters, looting, cannon, ensouled heads, high alch, herb sack, gem bag, praying if no food in inventory ect", version = (1) )


public class main extends Script implements Arguments,MessageListening07,Painting {
    private boolean debug = true;

    private State scriptState = getState();
    private boolean continueRunning = true;

    private RSArea AreaCatacombsAnkou = new RSArea(new RSTile(1635, 9990, 0), new RSTile(1645, 9998, 0));
    private RSArea AreaStrongholdAnkou = new RSArea(new RSTile(2471, 9807, 0), new RSTile(2481, 9897, 0));
    private RSArea AreaStrongholdAberrantSpectre = new RSArea(new RSTile(2461, 9787, 0), new RSTile(2450, 9796, 0));
    private RSArea areaSeagulls = new RSArea(new RSTile(3022, 3209, 0), new RSTile(3036, 3195, 0));
    private RSArea areaFallyCows = new RSArea(new RSTile(3023, 3311, 0), new RSTile(3039, 3299, 0));
    private RSArea areaAdventureJon = new RSArea(new RSTile(3231, 3232, 0), new RSTile(3234, 3235, 0));
    private RSArea areaLumbridgeGoblins = new RSArea(new RSTile(3240, 3242, 0), new RSTile(3263, 3227, 0));

    private double alch_At = 0;

    //Prayer


    double gainedfromPrayer = 7 + Math.floor(Skills.SKILLS.PRAYER.getActualLevel() / 4);
    double lessthanmaxPrayer = Skills.SKILLS.PRAYER.getActualLevel() - gainedfromPrayer;
    double drinkAt = 5;


    //Cannonshit
    private boolean hascannonBalls = false;
    private boolean hasCannon = false;
    private boolean refillCannon = false;
    long time = System.currentTimeMillis();

    //Slayer
    private RSItem[] enchantedGem = Inventory.find("Enchanted gem");
    private RSItem[] slayerHelm = Inventory.find("Slayer helm");

    private boolean shouldCheckTask = false;


    double invyspaceMax = 28;
    private boolean potionFound = false;
    private int SETPOTION = 0;
    //private final String[] enemy = {  "Mutated Bloodveld", "Cyclops", "Abyssal demon", "Mammoth", "Fire giant", "Greater demon" };
    private boolean multi = false;


    private static final long START_TIME = System.currentTimeMillis();

    public final int EATING_ANIMATION = 829;
    private boolean reloadCannnonNow = false;
    //private ACamera aCamera = new ACamera(this);
    String args;

    private boolean foodFound = false;



    int helm = 0;
    int body = 0;
    int weapon = 0;
    int shield = 0;
    int legs = 0;
    private final String[] herbs = {"Grimy ranarr weed", "Grimy avantoe", "Grimy snapdragon", "Grimy torstol", "Grimy dwarf weed", "Grimy lantadyme", "Grimy cadantine", "Grimy kwuarm", "Grimy irit leaf", "Grimy toadflax"};
    private final String[] gems = {"Uncut ruby", "Uncut sapphire", "Uncut emerald", "Uncut diamond", "Uncut dragonstone"};
    private RSGroundItem[] expensive = GroundItems.findNearest("Abyssal whip");
    //private RSGroundItem[] heads = GroundItems.findNearest(Filters.GroundItems.nameContains("Ensouled"));
    // private final String heads[] = {GroundItems.getAll().contains("Ensouledled")};

    @Override
    public void passArguments(HashMap<String, String> hashMap) {
        if (hashMap.containsKey("custom_input")) {
            args = hashMap.get("custom_input");
        } else if (hashMap.containsKey("autostart")) {
            args = hashMap.get("autostart");
        }
    }

    public String main = "[Main] ";
    public String antiban = "[AnTiBaN] ";
    private boolean onstartCheck = false;

    private boolean alchDecision = false;
    private int foodID = 0;
    private boolean gemBagDecision = false;
    private boolean herbSackDecision = false;
    private boolean soulbearerDecision = false;
    private boolean guthansDecision = false;
    private boolean sgsDecision = false;

    private int helmID;
    private int bodyID;
    private int legID;
    private int weaponID;
    private int shieldID;

    private String fightType;
    private int CHANGE_TIME = General.random(480000, 2700000);
    private long LAST_CHANGE = System.currentTimeMillis();

    private boolean isUsingCannon = false;
    private boolean usingSupers = false;
    private boolean usingPpots = false;

    int amountKilled;
    int averageKillTime;

    private long killStartTime;
    private long killEndTime;
    private long timeToKill;

    private boolean needsToClaim = false;
    private boolean trainingMode = false;
    private boolean trainingAreaDecided = false;

    int attackLevel = Skills.getActualLevel(Skills.SKILLS.ATTACK);
    int strengthLevel = Skills.getActualLevel(Skills.SKILLS.STRENGTH);
    int defenseLevel = Skills.getActualLevel(Skills.SKILLS.DEFENCE);

    private int strenth1 = 0;
    private int strenth2 = 0;
    private int strenth3 = 0;
    private int strenth4 = 0;
    private int attack1 = 0;
    private int attack2 = 0;
    private int attack3 = 0;
    private int attack4 = 0;






    private void grabGear() {
        RSItem helm = Equipment.getItem(Equipment.SLOTS.HELMET);
        RSItem body = Equipment.getItem(Equipment.SLOTS.BODY);
        RSItem legs = Equipment.getItem(Equipment.SLOTS.LEGS);
        RSItem weapon = Equipment.getItem(Equipment.SLOTS.WEAPON);
        RSItem shield = Equipment.getItem(Equipment.SLOTS.SHIELD);

        if (Equipment.find(Equipment.SLOTS.HELMET).length > 0) {
            if (helmID == 0) {
                helmID = helm.getID();
            }
        }
        if (Equipment.find(Equipment.SLOTS.BODY).length > 0) {
            if (bodyID == 0) {
                bodyID = body.getID();
            }
        }
        if (Equipment.find(Equipment.SLOTS.LEGS).length > 0) {
            if (legID == 0) {
                legID = legs.getID();
            }
        }
        if (Equipment.find(Equipment.SLOTS.WEAPON).length > 0) {
            if (weaponID == 0) {
                weaponID = weapon.getID();
            }
        }
        if (Equipment.find(Equipment.SLOTS.SHIELD).length > 0) {
            if (shieldID == 0) {
                shieldID = shield.getID();
            }
        }

    }

    public boolean hasGemBag() {
        return Inventory.find("Gem bag").length > 0;
    }

    @Override
    public void run() {
        while (continueRunning){
            loop();
        }

    }



    public static boolean reloadNow() {
        return Timing.waitCondition(new Condition() {
            @Override
            public boolean active() {
                return Inventory.getCount("Leather") > 0;
            }
        }, General.random(2000, 3000));
    }

    public void reloadCannon() {
        RSObject[] cannon = Objects.findNearest(10, "Dwarf multicannon");
        RSObject[] brokecannon = Objects.findNearest(10, "Broken multicannon");
        RSItem[] cannonballs = Inventory.find("Cannonball");
        if (brokecannon.length > 0 && cannonballs.length > 0) {
            General.sleep(800, 6000);
            Clicking.click("Repair", brokecannon);
        }
        if (cannonballs.length > 0 && cannon.length > 0) {
            int randomNum = General.random(10, 40);
            if (System.currentTimeMillis() >= (time + randomNum * 1000)) { //multiply by 1000 to get milliseconds
                General.println("Time passed");
                time = System.currentTimeMillis();
                refillCannon = true;
            } else {
                refillCannon = false;
                General.println("Time not passed");
            }
            Clicking.click("Fire", cannon);
            //  reloadCannnonNow = false;
            //}

        }
    }

    @Override
    public void clanMessageReceived(String arg0, String arg1) {
        // TODO Auto-generated method stub
    }

    @Override
    public void personalMessageReceived(String arg0, String arg1) {

    }

    @Override
    public void tradeRequestReceived(String arg0) {
        // TODO Auto-generated method stub
    }

    @Override
    public void duelRequestReceived(String arg0, String arg1) {
        // TODO Auto-generated method stub
    }

    @Override
    public void playerMessageReceived(String arg0, String arg1) {
        // TODO Auto-generated method stub
    }

    @Override
    public void serverMessageReceived(String arg0) {
        // TODO Auto-generated method stub
        if (arg0.contains("Speak to Adventurer Jon ")) {
             needsToClaim = true;
        }
        if (arg0.equals("A superior foe has appeared...")) {
            Toolkit.getDefaultToolkit().beep();
            General.sleep(General.randomSD(250, 1200, 725, 500));
            Toolkit.getDefaultToolkit().beep();
            General.sleep(General.randomSD(250, 1200, 725, 500));
            Toolkit.getDefaultToolkit().beep();
            General.sleep(General.randomSD(250, 1200, 725, 500));
            Toolkit.getDefaultToolkit().beep();
            General.sleep(General.randomSD(250, 1200, 725, 500));
            Toolkit.getDefaultToolkit().beep();
            General.sleep(General.randomSD(250, 1200, 725, 500));
        }
        if (arg0.equals("Your cannon has ran out of cannonballs")) {
            refillCannon = true;
        }
        //if (arg0.contains("You've completed")) {
       //     shouldCheckTask = true;
        //}
    }

    private void closeInterface() {
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


    private void checkSlayerMonsters() {
        if (shouldCheckTask) {
            if (slayerHelm.length > 0) {
                Clicking.click("Check", slayerHelm[0]);
                // Timing condition check if has slayer task

            }
            if (enchantedGem.length > 0) {
                Clicking.click("Check", enchantedGem[0]);
                serverMessageReceived("You're assigned to kill");
            }
        }


    }

    private void daxStart() {
        DaxWalker.setCredentials(new DaxCredentialsProvider() {
            @Override
            public DaxCredentials getDaxCredentials() {
                return new DaxCredentials("sub_DPjXXzL5DeSiPf", "PUBLIC-KEY");
            }
        });

    }


    private int loop() {
        scriptState = getState();
        General.sleep(50);
        Mouse.setSpeed(General.random(95, 260));
        daxStart();
        runCheck();
        switch (scriptState) {

            case WALK_TO_NPC:


                if (trainingMode ){
                    if (attackLevel <= 15 || strengthLevel <= 20 || defenseLevel <= 10){
                        if (DaxWalker.walkTo(areaSeagulls.getRandomTile())){
                            Timing.waitCondition(() -> !Player.isMoving(), General.random(600, 1100));

                        }
                    } else {
                       if (DaxWalker.walkTo(areaFallyCows.getRandomTile())){
                           Timing.waitCondition(() -> !Player.isMoving(), General.random(600, 1100));

                       }
                    }
                }

                break;

            case CLAIM:
                if (!areaAdventureJon.contains(Player.getPosition())){
                    if (NPCChat.getClickContinueInterface()!=null){
                        Keyboard.typeSend(" ");
                    }
                    DaxWalker.walkTo(areaAdventureJon.getRandomTile());
                } else {
                    RSNPC[] jon = NPCs.find("Adventurer Jon");
                    if (jon.length > 0 && jon[0]!=null){
                        RSInterface claimOpen = Interfaces.get(193, 2);
                        if (!Interfaces.isInterfaceSubstantiated(claimOpen)){
                            if (Clicking.click("Claim", jon[0])) {
                                Timing.waitCondition(() -> Interfaces.isInterfaceSubstantiated(claimOpen), General.random(2000, 3000));
                                int attackLevel = Skills.getActualLevel(Skills.SKILLS.ATTACK);
                                int defenseLevel = Skills.getActualLevel(Skills.SKILLS.DEFENCE);

                                if (defenseLevel == 20){
                                    RSItem[] armour20 = Inventory.find("Mithril chainbody");
                                    if (armour20.length > 0){
                                        Clicking.click("Wear", armour20[0]);
                                        General.sleep(1200, 2000);
                                    }
                                }
                                if (defenseLevel == 10){
                                    RSItem[] armour10 = Inventory.find("Steel chainbody");
                                    if (armour10.length > 0){
                                        Clicking.click("Wear", armour10[0]);
                                        General.sleep(1200, 2000);
                                    }
                                }
                                if (defenseLevel == 5){
                                    RSItem[] armour5 = Inventory.find("Steel med helm");
                                    if (armour5.length > 0){
                                        Clicking.click("Wear", armour5[0]);
                                        General.sleep(1200, 2000);
                                    }
                                }
                                if (attackLevel == 20){
                                    RSItem[] weapon20 = Inventory.find("Mithril longsword");
                                    if (weapon20.length > 0){
                                        Clicking.click("Wield", weapon20[0]);
                                        General.sleep(1200, 2000);
                                    }

                                }
                                if (attackLevel == 10){
                                    RSItem[] weapon10 = Inventory.find("Black longsword");
                                    if (weapon10.length > 0){
                                        Clicking.click("Wield", weapon10[0]);
                                        General.sleep(1200, 2000);
                                    }
                                }
                                if (attackLevel == 5){
                                    RSItem[] weapon5 = Inventory.find("Steel longsword");
                                    if (weapon5.length > 0){
                                        Clicking.click("Wield", weapon5[0]);
                                        General.sleep(1200, 2000);
                                    }
                                }
                                needsToClaim = false;

                            }
                        }

                    }
                }

                break;



            case CURRENTLY_FIGHTING:
                if (NPCChat.getClickContinueInterface()!=null){
                    Keyboard.typeSend(" ");
                }
                performTimedActions();
                checkStats();
                int attackLevel = Skills.getActualLevel(Skills.SKILLS.ATTACK);
                int strengthLevel = Skills.getActualLevel(Skills.SKILLS.STRENGTH);
                int defenseLevel = Skills.getActualLevel(Skills.SKILLS.DEFENCE);
                if (Equipment.find(Equipment.SLOTS.WEAPON).length < 1){
                    RSItem[] sword = Inventory.find("Bronze sword");
                    if (sword.length > 0){
                        Clicking.click("Wield", sword[0]);
                        General.sleep(600,1200);
                    }
                }
                if (Equipment.find(Equipment.SLOTS.SHIELD).length < 1){
                    RSItem[] shield = Inventory.find("Wooden shield");
                    if (shield.length > 0){
                        Clicking.click("Wield", shield[0]);
                        General.sleep(600,1200);
                    }
                }
                if (attackLevel >= 40 && strengthLevel >= 40 && defenseLevel >= 40){
                    trainingMode = false;
                } else {
                    trainingMode = true;
                }

                RSCharacter ch = Player.getRSPlayer().getInteractingCharacter();
                if (ch != null){
                    if (ch.getHealthPercent() < .01) {

                        amountKilled = amountKilled + 1;
                        killEndTime = System.currentTimeMillis();
                        timeToKill = killEndTime - killStartTime;
                        if (timeToKill > 120000) {
                            timeToKill = 0;
                        }
                        General.println(timeToKill);
                        General.sleep(600);

                        killStartTime = 0;
                        killEndTime = 0;

                    } else {

                    }
                }

                // Either sleep, move mouse of screen, or do these thigns
                if (isUsingCannon){

                } else {

                }
                if (foodFound){
                    //ABC eat
                    eat();

                    //Eat to full

                }
                if (usingPpots){
                    potionHandler();
                }
                if (usingSupers){
                    potionHandler();
                }
                if (gemBagDecision){

                }
                if (soulbearerDecision){

                }
                if (herbSackDecision){

                }
                break;

            case ONSTART:

                if (Skills.getActualLevel(Skills.SKILLS.HITPOINTS) < 1){
                    General.sleep(10000);
                } else {
                    General.println("Welcome back " + General.getTRiBotUsername());

                    if (!onstartCheck) {
                        Camera.setRotationMethod(Camera.ROTATION_METHOD.DEFAULT);
                        if (isOnStandardSpellbook() && hasAlchRunes()) {
                            alchDecision = true;
                        }

                        RSItem[] food = Inventory.find(Filters.Items.actionsEquals("Eat"));
                        if (food.length > 0 && food[0] != null) {
                            foodChoice();
                        } else {
                            General.println("No useable food found");
                        }

                        if (hasGemBag()) {
                            gemBagDecision = true;
                        }
                        if (herbBag()) {
                            herbSackDecision = true;
                        }
                        RSItem[] soulbearer = Inventory.find("Soul bearer");
                        if (soulbearer.length > 0) {
                            soulbearerDecision = true;
                        }
                        RSItem[] sgs = Inventory.find("Saradomin godsword");
                        RSItem[] guthans = Inventory.find(Filters.Items.nameContains("Guthans"));
                        if (sgs.length > 0) {
                            sgsDecision = true;

                        }
                        if (guthans.length > 0) {
                            guthansDecision = true;

                        }
                        RSItem[] supers = Inventory.find(Filters.Items.nameContains("Super"));
                        RSItem[] ppots = Inventory.find(Filters.Items.nameContains("Prayer potion").or(Filters.Items.nameContains("Super restore")));
                        if (supers.length > 0) {
                            usingSupers = true;
                        }
                        if (ppots.length > 0) {
                            usingPpots = true;
                        }

                        if (hasCannonParts()) {
                            isUsingCannon = true;
                        }
                        //cannon check
                        General.println(main + "Taking a few moments to cache item prices and gear set up...");
                        grabGear();
                        strenth1 = General.random(5,10);
                        strenth2 = General.random(15,20);
                        strenth3 = General.random(23,30);
                        strenth4 = General.random(36,42);
                        attack1 = General.random(5,10);
                        attack2 = General.random(15,20);
                        attack3 = General.random(23,30);
                        attack4 = General.random(34,40);
                        General.sleep(7500, 10000);
                        onstartCheck = true;

                    }

                }

                break;


            case ATTACK:

                attack();



                break;

            case EAT:


                break;

            case IDLE:

                break;

            case ALCH:

                break;

            case DRINKPPOT:

                break;
        }


        return 50;
    }

    @Override
    public void onPaint(Graphics g) {
        long runtime = System.currentTimeMillis() - START_TIME;
        g.setColor(Color.white);
        g.drawString("ahKiller", 5, 50);
        g.drawString("Running for: " + Timing.msToString(runtime), 5, 70);
        g.drawString("State: " + scriptState, 5, 90);
        g.drawString("Hunting " + args + " | " + args + " Amount killed:" + amountKilled, 5, 110);
        g.drawString("Average kill time" + timeToKill, 5, 130);
        g.drawString("Alch percentage" + alch_At, 5, 150);
        g.drawString("Drink next ppot: " + drinkAt, 5, 170);
        g.drawString("Food:  " +  foodID + " at " + eat_at + "%" , 5,200);
        g.drawString("Trainer mode:  " +  trainingMode, 5,220);


        if (debug){
            g.drawString("Helm " + helmID + " Body " + bodyID + " Legs " + legID + " Weapon " + weaponID + " shield " + shieldID, 5, 280);
        }

    }

    public enum State {

        EAT,
        ATTACK,
        IDLE,
        ALCH,
        DRINKPPOT, ONSTART, CURRENTLY_FIGHTING,PICKUP_CANNON, FETCH_SUPPLIES,WALK_TO_NPC,PLACE_CANNON, CLAIM

    }


    private void alcher() {
        if (canAlch()) {
            if (args.equals("Gargoyle")) {
                alchItems("Granite maul", "Rune full helm", "Rune platelegs", "Rune battleaxe");
            }
        }
    }

    private void attack() {
        //sleep(500, 1200);
        if (NPCChat.getClickContinueInterface()!=null){
            Keyboard.typeSend(" ");
        }

        if (trainingMode){
            if (areaSeagulls.contains(Player.getPosition())){
                RSNPC target = findNextTarget("Seagull");
                if (target != null) {
                    General.println(main +"Next target is: " + target.getDefinition().getName());
                    attackNPC(target);
                    killStartTime = System.currentTimeMillis();
                }
            } else {
                RSNPC target = findNextTarget("Cow");
                if (target != null) {
                    General.println(main +"Next target is: " + target.getDefinition().getName());
                    attackNPC(target);
                    killStartTime = System.currentTimeMillis();
                }
            }


        } else {
            if (args.isEmpty()) {
                General.println(main + "We have completed training mode and have no argument set.");
                continueRunning = false;
            } else {
                RSNPC target = findNextTarget(args);
                if (target != null) {
                    General.println(main + "Next target is: " + args);
                    attackNPC(target);
                    killStartTime = System.currentTimeMillis();
                }
            }
        }



    }


    private boolean hasTaskSupplies() {

        if (args.isEmpty()){
            return true;
        } else {
            if (args.equals("Gargoyle")) {
                RSItem[] slayerGear = Inventory.find("Rock hammer", "Rock thrownhammer");
                if (slayerGear.length > 0) {
                    return true;
                } else {
                    return false;
                }
            }
            if (args.equals("Dust devil")) {
                RSItem[] slayerGear = Equipment.find("Face mask", "Slayer helmet", "Slayer helmet(i)");
                if (slayerGear.length > 0) {
                    return true;
                } else {
                    return false;
                }
            }
            if (args.equals("Banshees")) {
                RSItem[] slayerGear = Equipment.find("Earmuff");
                if (slayerGear.length > 0) {
                    return true;
                } else {
                    return false;
                }
            }
            if (args.equals("Rock slug")) {
                RSItem[] slayerGear = Inventory.find("Bag of salt");
                RSItem[] slayerGear2 = Equipment.find("Brine saber");
                if (slayerGear.length > 0 || slayerGear2.length > 0) {
                    return true;
                } else {
                    return false;
                }
            }
            if (args.equals("Desert lizard")) {
                RSItem[] slayerGear = Inventory.find("Ice cooler");
                if (slayerGear.length > 0) {
                    return true;
                } else {
                    return false;
                }
            }
            if (args.equals("Basilisk") || args.equals("Cockatrice")) {
                RSItem[] slayerGear2 = Equipment.find("Mirror shield");
                if (slayerGear2.length > 0) {
                    return true;
                } else {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isInArea() {
        int attackLevel = Skills.getActualLevel(Skills.SKILLS.ATTACK);
        int strengthLevel = Skills.getActualLevel(Skills.SKILLS.STRENGTH);
        int defenseLevel = Skills.getActualLevel(Skills.SKILLS.DEFENCE);
        if (attackLevel >= 40 && strengthLevel >= 40 && defenseLevel >= 40){
            trainingMode = false;
        } else {
            trainingMode = true;
        }

        if (trainingMode ){
            if (attackLevel <= 15 || strengthLevel <= 20 || defenseLevel <= 10){
                return areaSeagulls.contains(Player.getPosition());
            } else {
                return areaFallyCows.contains(Player.getPosition());
            }
        }

        return true;
    }



    private boolean isCannonSetUp(){
        return false;
    }

    private boolean hasCannonParts(){
        RSItem[] cannon = Inventory.find("Cannon furnace");
        return cannon.length > 0;
    }


    private State getState() {

        if (onstartCheck) {
            if (hasTaskSupplies()) {
                if (!needsToClaim) {
                    if (isInArea()) {
                        if (isUsingCannon) {
                            if (isCannonSetUp()) {
                                //if cannon is set up then load cannon
                                if (isFighting()) {
                                    return State.CURRENTLY_FIGHTING;
                                } else {
                                    return State.ATTACK;
                                    //decide wether to afk, pick new monster, or loot
                                }
                            } else {
                                if (hasCannonParts()) {
                                    return State.PLACE_CANNON;
                                }
                            }
                        } else {
                            //attack monsters
                            if (isFighting()) {
                                return State.CURRENTLY_FIGHTING;
                            } else {
                                return State.ATTACK;
                            }
                        }
                    } else {
                        return State.WALK_TO_NPC;
                    }
                } else {
                    return State.CLAIM;
                }
            } else {
                return State.FETCH_SUPPLIES;
            }
            if (shouldCheckTask && isUsingCannon && isCannonSetUp()) {
                return State.PICKUP_CANNON;
            }
            return State.IDLE;

        } else {
            return State.ONSTART;
        }
    }


    public void survive() {
        int low = 20;
        int high = 40;
        int Result = General.random( low, high );

        int maxhp = General.random( Skills.SKILLS.PRAYER.getActualLevel() - 15, Skills.SKILLS.PRAYER.getActualLevel() - 30 );


        RSItem[] potion = Inventory.find( "Prayer potion(1)", "Prayer potion(2)", "Prayer potion(3)", "Prayer potion(4)", "Super restore(1)", "Super restore(2)", "Super restore(3)", "Super restore(4)" );
        RSItem[] food = Inventory.find( foodID );
        if (Skills.SKILLS.PRAYER.getActualLevel() > 43) {

            if (Prayer.getPrayerPoints() >= 15) {
                if (!foodFound) {
                    if (!Prayer.isPrayerEnabled( Prayer.PRAYERS.PROTECT_FROM_MELEE )) {
                        Prayer.enable( Prayer.PRAYERS.PROTECT_FROM_MELEE );
                        General.println( "Turning on prayer" );
                    }
                }
            }
            if ((food.length < 1) && Skills.getCurrentLevel( Skills.SKILLS.HITPOINTS ) <= Result) {
                if (!Prayer.isPrayerEnabled( Prayer.PRAYERS.PROTECT_FROM_MELEE )) {
                    Prayer.enable( Prayer.PRAYERS.PROTECT_FROM_MELEE );
                    General.println( "Turning on prayer" );
                }
            }

            if (potion.length > 0) {
                if (Prayer.isPrayerEnabled( Prayer.PRAYERS.PROTECT_FROM_MELEE ) || Prayer.isPrayerEnabled( Prayer.PRAYERS.PROTECT_FROM_MAGIC ) || Prayer.isPrayerEnabled( Prayer.PRAYERS.PROTECT_FROM_MISSILES )) {
                    if (Skills.getCurrentLevel( Skills.SKILLS.PRAYER ) <= drinkAt) {
                        drinkPPOT();
                        General.println( "Drinking ppot" );
                    }
                }
            }
        }
        if (foodFound && !Prayer.isPrayerEnabled( Prayer.PRAYERS.PROTECT_FROM_MELEE )) {
            if (Skills.getCurrentLevel( Skills.SKILLS.HITPOINTS ) <= 5) {
                eatFood();
                sleep( 300, 500 );
                for (int i = 0; i < 10 && Player.getAnimation() == EATING_ANIMATION; i++) {
                    sleep( 90, 110 );
                }
                // }
            }
        }
        if (Skills.getCurrentLevel( Skills.SKILLS.PRAYER ) < 10 && (food.length < 1) && Skills.getCurrentLevel( Skills.SKILLS.HITPOINTS ) <= Result) {
            hasTP();
            General.sleep( 2500,5000 );
        }
    }



    private static Point getCenterPoint(RSItem i) {
        Rectangle r = i.getArea();
        if (r != null) {
            Point rPoint = r.getLocation();
            return new Point(rPoint.x + r.width / 2, rPoint.y + r.height / 2);
        }
        return null;
    }

    private static Comparator<RSItem> closestToFarthest = (o1, o2) -> {
        Point botMousePoint = new Point(Mouse.getPos());
        Point p1 = getCenterPoint(o1);
        Point p2 = getCenterPoint(o2);
        if (p1 != null && p2 != null) {
            return Integer.compare((int) botMousePoint.distance(p1), (int) botMousePoint.distance(p2)) > 0 ? 1 : -1;
        }
        return -1;
    };

    public static RSItem[] findNearestToMouse(String... names) {
        RSItem[] items = Inventory.find(names);
        Arrays.sort(items, closestToFarthest);
        return items;
    }

    public static RSItem[] findNearestToMouseBank(String... names) {
        RSItem[] banks = Banking.find(names );
        Arrays.sort(banks, closestToFarthest);
        return banks;
    }






    private boolean shouldAlch(){
        return Inventory.getAll().length/28 >= alch_At;

    }


        public RSItem alchItems(String... Names) {
            if (GameTab.getOpen() == GameTab.TABS.MAGIC) {
                RSItem[] inv = Inventory.find( Filters.Items.nameEquals() );
                for (RSItem i : inv) {
                    Magic.selectSpell( "High Level alchemy" );
                    Timing.waitCondition( new Condition() {
                        @Override
                        public boolean active() {
                            General.sleep( General.randomSD( 70, 210, 140, 70 ) );
                            return Game.isUptext( "->" );
                        }
                    }, General.random( 1000, 1200 ) );
                    i.click( "Cast ->" );
                }
                alch_At = General.randomDouble( 0.82, 0.97 );
            } else {
                if (GameTab.getOpen() != GameTab.TABS.MAGIC) {
                    GameTab.open( GameTab.TABS.MAGIC );
                }
            }
            return null;
        }

        private boolean canAlch(){
            return isOnStandardSpellbook() && hasAlchRunes() && shouldAlch();
        }

    public static void drinkPotionz1(){
        //RSItem[] potion = Inventory.find("Combat potion(1)", "Combat potion(2)","Combat potion(3)", "Combat potion(4)");
        RSItem[] potion = Inventory.find("Super strength(1)", "Super strength(2)","Super strength(3)", "Super strength(4)");
        if (potion.length > 0){
            Clicking.click("Drink", potion);
            General.sleep(500, 900);
            System.out.println("Super Strength Potion Drank");
        }
    }
    public static void drinkPotionz2(){
        //RSItem[] potion = Inventory.find("Combat potion(1)", "Combat potion(2)","Combat potion(3)", "Combat potion(4)");
        RSItem[] potion = Inventory.find("Super attack(1)", "Super attack(2)","Super attack(3)", "Super attack(4)");
        if (potion.length > 0){
            Clicking.click("Drink", potion);
            General.sleep(500, 900);
            System.out.println("Super Attack Potion Drank");
        }
    }

    public void drinkPPOT(){
        //RSItem[] potion = Inventory.find("Combat potion(1)", "Combat potion(2)","Combat potion(3)", "Combat potion(4)");
        RSItem[] potion = Inventory.find("Prayer potion(1)", "Prayer potion(2)","Prayer potion(3)", "Prayer potion(4)", "Super restore(1)", "Super restore(2)", "Super restore(3)", "Super restore(4)");
        if (potion.length > 0){
            Clicking.click("Drink", potion);
            General.sleep(500, 900);
            double drinkAt = General.randomDouble( lessthanmaxPrayer * 0.3, lessthanmaxPrayer * 0.8 );

            General.println("Prayer points restored from prayer potion.");
        }
    }


    public void potionHandler(){


    }




    public boolean gemBag(){
        return Inventory.find("Gem bag").length > 0;
    }
    public boolean herbBag(){
        return Inventory.find("Herb sack").length > 0;
    }

    public boolean usegemBag(){
        RSItem[] gems = Inventory.find("Uncut ruby", "Uncut sapphire", "Uncut emerald","Uncut diamond", "Uncut dragonstone");
        RSItem[] gembag = (Inventory.find("Gem bag"));

        if (gemBag() && (gems.length > 0 && gems[0] != null && !gems[0].getDefinition().isNoted())){
            Clicking.click("Fill", gembag);
            General.println("Filling Gem bag");
            Timing.waitCondition(new Condition() {
                @Override
                public boolean active() {
                    General.sleep(100);
                    return gems.length < 1;
                }
            }, General.random(2500, 5800));



            return true;
        }
        else return false;
    }

    public boolean useSoulBag(){
         RSGroundItem[] heads = GroundItems.findNearest(Filters.GroundItems.nameContains("Ensouled"));
        if (heads.length > 0 && heads[0] != null){
            General.sleep(General.randomSD(250, 1200, 725, 500));
            DynamicClicking.clickRSGroundItem(heads[0], "Take ");
            General.println("Collecting  heads");

            return true;
        }
        else return false;
    }


    public boolean useherbBag(){
        RSItem[] herbs = Inventory.find("Grimy ranarr weed", "Grimy avantoe", "Grimy snapdragon", "Grimy torstol","Grimy dwarf weed", "Grimy lantadyme", "Grimy cadantine", "Grimy kwuarm", "Grimy irit leaf", "Grimy toadflax"  );
        RSItem[] herbbag = (Inventory.find("Herb sack"));

        if (herbBag() && (herbs.length > 0 && herbs[0] != null)){
            General.sleep(General.randomSD(250, 1200, 725, 500));
            Clicking.click("Fill", herbbag);
            General.println("Filling Herb bag");

            return true;
        }
        else return false;
    }




    private void fightTypeTimer(){
        if (System.currentTimeMillis() - LAST_CHANGE >= CHANGE_TIME) {

            int random = General.random(1,3);
            if (random == 1){
                fightType.equals("aggressive");
            }
            if (random == 2){
                fightType.equals("afk");
            }
            if (random == 3){
                fightType.equals("undecided");
            }

            General.println("Changing current combat style to  " + fightType);

            LAST_CHANGE = System.currentTimeMillis();
            CHANGE_TIME = General.random(480000,2700000);
            // 8 minutes -> 45 mins'

        } 
    }









    private boolean useGuthans(){

        RSItem[] helm = Inventory.find(Filters.Items.nameContains("Guthan's helm"));
        RSItem[] platebody = Inventory.find(Filters.Items.nameContains("Guthan's platebody"));
        RSItem[] chainskirt = Inventory.find(Filters.Items.nameContains("Guthan's chainskirt"));
        RSItem[] warspear = Inventory.find(Filters.Items.nameContains("Guthan's warspear"));
        if (helm.length > 0 && platebody.length > 0 && chainskirt.length > 0 && warspear.length > 0){
            General.sleep(General.randomSD(70, 210, 140, 70));
            Clicking.click("Fill", helm);
            General.sleep(General.randomSD(70, 210, 140, 70));
            Clicking.click("Fill", platebody);
            General.sleep(General.randomSD(70, 210, 140, 70));
            Clicking.click("Fill", chainskirt);
            General.sleep(General.randomSD(70, 210, 140, 70));
            Clicking.click("Fill", warspear);
            General.sleep(General.randomSD(70, 210, 140, 70));
            General.sleep(30, 80);
            return true;

        }
        else return false;
    }

    private boolean hasTP(){
        RSItem[] tp = Inventory.find(Filters.Items.nameContains("Teleport"));
        if (GameTab.getOpen() != GameTab.TABS.INVENTORY) {
            GameTab.open(GameTab.TABS.INVENTORY);
        }
        if (tp.length > 0 && tp != null) {
                Clicking.click("Break", tp);
                General.sleep(General.randomSD(250, 1200, 725, 500));
                General.println("Emergency teleport..");
                //return Player.getAnimation() == 829;

        } return true;

    }
    private void loot() {
        useSoulBag();
        RSGroundItem[] grounditems = GroundItems.findNearest("Mithril bar", "Gold ore", "Gold bar","Dark totem base","Rune med helm", "Uncut ruby", "Uncut sapphire", "Uncut emerald","Uncut diamond", "Uncut dragonstone", "Mysterious emblem","Abyssal whip","Rune defender", "Adamant platelegs", "Rune full helm", "Adamant boots", "Rune 2h sword", "Rune battleaxe", "Rune platelegs", "Granite maul", "Mystic robe top", "Chaos rune", "Death rune", "Steel bar", "Runite ore", "Brittle key", "Clue scroll(hard)", "Loop half of key", "Chaos rune", "Rune chainbody", "Grimy ranarr weed", "Grimy avantoe", "Grimy snapdragon", "Grimy torstol", "Grimy dwarf weed", "Grimy lantadyme", "Grimy cadantine", "Grimy kwuarm", "Grimy irit leaf", "Grimy toadflax", "Blood rune", "Black full helm", "Rune dagger", "Dragon defender", "Bronze defender", "Iron defender","Steel defender","Black defender","Mithril defender", "Adamant defender" , "Adamant 2h sword", "Prayer potion(4)", "Earth battlestaff", "Wyvern bones", "Fire rune");




        RSGroundItem[] grounditemsnoted = GroundItems.findNearest("Pure essence", "Death rune", "Chaos rune", "Gold ore", "Steel bar", "Fire rune", "Mithril bar", "Gold bar", "Blood rune", "Soul rune", "Iron ore", "Magic logs", "Unpowered orbs", "Rune arrows", "Adament bolts", "Law runes", "Death runes", "Black knife");
        if (grounditemsnoted.length > 0 && grounditemsnoted[0] != null){
            if (grounditemsnoted[0].getDefinition().isNoted() || grounditemsnoted[0].getDefinition().isStackable()) {
                if (Inventory.getCount(grounditemsnoted[0].getID()) < 1 && Inventory.isFull()) {
                    eatFood();
                    sleep(300, 500);
                    for (int i = 0; i < 10 && Player.getAnimation() == EATING_ANIMATION; i++) {
                        sleep(90, 110);
                    }

                }
                if (Inventory.getCount(grounditemsnoted[0].getID()) > 0) {
                    if (grounditemsnoted[0].isOnScreen() && grounditemsnoted[0].isClickable()) {
                        if (Player.getPosition().distanceTo(grounditemsnoted[0]) < 5){
                            int low = 1;
                            int high = 3;
                            int Result = General.random(low, high);
                            if (Result >= 2) {
                                General.sleep(General.randomSD(250, 1200, 725, 500));
                                DynamicClicking.clickRSGroundItem(grounditemsnoted[0], "Take ");
                                General.println("Looting stackable " + grounditemsnoted[0]);

                                General.sleep(General.randomSD(250, 1200, 725, 500));
                            } else {
                                if (Result<=2){
                                    //aCamera.turnToTile(grounditemsnoted[0]);
                                    General.sleep(General.randomSD(0, 140, 70, 70));
                                }
                                General.sleep(General.randomSD(250, 1200, 725, 500));

                                DynamicClicking.clickRSGroundItem(grounditemsnoted[0], 1);
                                General.sleep(General.randomSD(250, 1200, 725, 500));
                                General.println("Looting stackable " + grounditemsnoted[0]);
                            }
                        } else {
                            int low = 0;
                            int high = 4;
                            int Result = General.random(low, high);
                            if (Result > 2) {
                                General.sleep(General.randomSD(250, 1200, 725, 500));
                                DynamicClicking.clickRSGroundItem(grounditemsnoted[0], "Take ");
                                General.println("Looting stackable " + grounditemsnoted[0]);
                                General.sleep(General.randomSD(250, 1200, 725, 500));
                            } else {
                                General.sleep(General.randomSD(250, 1200, 725, 500));

                                //aCamera.turnToTile(grounditemsnoted[0]);
                                General.sleep(General.randomSD(0, 140, 70, 70));
                            }
                            General.sleep(General.randomSD(250, 1200, 725, 500));

                            DynamicClicking.clickRSGroundItem(grounditemsnoted[0], 1);
                            General.println("Looting stackable " + grounditemsnoted[0]);
                            General.sleep(General.randomSD(250, 1200, 725, 500));
                        }

                    }

                }

            }
        }




    if (grounditems.length > 0 && grounditems[0] != null) {
        if (Inventory.isFull()) {
            eatFood();
            sleep(300, 500);
            for (int i = 0; i < 10 && Player.getAnimation() == EATING_ANIMATION; i++) {
                sleep(90, 110);
            }
        }
        if (grounditems[0].isOnScreen() && grounditems[0].isClickable()) {
            if (Player.getPosition().distanceTo(grounditems[0]) < 5){
                int low = 1;
                int high = 3;
                int Result = General.random(low, high);
                if (Result >= 2) {
                    General.sleep(General.randomSD(250, 1200, 725, 500));
                    DynamicClicking.clickRSGroundItem(grounditems[0], "Take ");
                    General.println("Looting " + grounditems[0]);

                    General.sleep(General.randomSD(250, 1200, 725, 500));
                } else {
                    if (Result<=2){
                       // aCamera.turnToTile(grounditems[0]);
                        General.sleep(General.randomSD(0, 140, 70, 70));
                    }
                    DynamicClicking.clickRSGroundItem(grounditems[0], 1);
                    General.sleep(General.randomSD(250, 1200, 725, 500));
                    General.println("Looting " + grounditems[0]);
                }
            } else {
                int low = 0;
                int high = 4;
                int Result = General.random(low, high);
                if (Result > 2) {
                    General.sleep(General.randomSD(250, 1200, 725, 500));
                    DynamicClicking.clickRSGroundItem(grounditems[0], "Take ");
                    General.println("Looting " + grounditems[0]);
                    General.sleep(General.randomSD(250, 1200, 725, 500));
                } else {
                    //aCamera.turnToTile(grounditems[0]);
                    General.sleep(General.randomSD(0, 140, 70, 70));
                }
                DynamicClicking.clickRSGroundItem(grounditems[0], 1);
                General.println("Looting " + grounditems[0]);
                General.sleep(General.randomSD(250, 1200, 725, 500));
                }

            }

        }
    }



    private boolean foodChoice(){
        RSItem[] food1 = Inventory.find(329);
        if (food1.length > 0) {
            foodID = 329;
            foodFound = true;
            General.println("Salmon detected as default food for EATING/BANKING");
        }

        RSItem[] food2 = Inventory.find(333);
        if (food2.length > 0) {
            foodID = 333;
            foodFound = true;
            General.println("Trout detected as default food for EATING/BANKING");
        }

        RSItem[] food3 = Inventory.find(361);
        if (food3.length > 0) {
            foodID = 361;
            foodFound = true;
            General.println("Tuna detected as default food for EATING/BANKING");
        }

        RSItem[] food4 = Inventory.find(379);
        if (food4.length > 0) {
            foodID = 379;
            foodFound = true;
            General.println("Lobster detected as default food for EATING/BANKING");
        }

        RSItem[] food5 = Inventory.find(373);
        if (food5.length > 0) {
            foodID = 373;
            foodFound = true;
            General.println("Swordfish detected as default food for EATING/BANKING");
        }

        RSItem[] food6 = Inventory.find(7946);
        if (food6.length > 0) {
            foodID = 7946;
            foodFound = true;
            General.println("Monkfish detected as default food for EATING/BANKING");
        }

        RSItem[] food7 = Inventory.find(385);
        if (food7.length > 0) {
            foodID = 385;
            foodFound = true;
            General.println("Shark detected as default food for EATING/BANKING");
        }
        else if (!foodFound){
            General.println("No food found. Please report to scripter");
        }
        return true;
    }

    private boolean potionChoice(){
        RSItem[] potion1 = Inventory.find("Combat potion(4)");
        if (potion1.length > 0) {
            SETPOTION = 9739;
            potionFound = true;
            General.println("Combat potion detected as default potion");
        }

        RSItem[] potion2 = Inventory.find("Super strength(4)");
        if (potion2.length > 0) {
            SETPOTION = 2440;
            potionFound = true;
            General.println("Super Strength/Attack detected as default potion");
        }
        RSItem[] potion3 = Inventory.find("Ranging potion(4)");
        if (potion3.length > 0) {
            SETPOTION = 2440;
            potionFound = true;
            General.println("Ranging potion detected as default potion");
        }
        return true;
    }



    public RSNPC findNextTarget(String targetName) {
        if (targetName != null) {
            RSNPC[] targets = NPCs.find(targetName);
            RSTile myLocation = new RSTile(Player.getPosition().getX(),Player.getPosition().getY(),Player.getPosition().getPlane());
                RSNPC[] sortedTargets = NPCs.sortByDistance(Player.getPosition(), targets);
                if (sortedTargets.length > 0 && sortedTargets[0]!= null) {
                    for (int i = 0; i < sortedTargets.length; i++) {
                        if (!sortedTargets[i].isInCombat() && !Combat.isUnderAttack() && sortedTargets[i].isValid() && Movement.canReach(sortedTargets[i]) && sortedTargets[i].getInteractingCharacter() == null && sortedTargets[i].getInteractingCharacter()==null && PathFinding.canReach(sortedTargets[i].getPosition(), false)) {
                            return sortedTargets[i];
                        }
                    }
                    // all nearby targets are in combat, try again soon.
                    return null;
                } else {
                    return null;
                }
        } else {
            return null;
        }
    }






    private boolean hasAlchRunes(){
        return RunePouch.getQuantity( "Fire rune" ) >= 3 && RunePouch.getQuantity( "Nature rune" ) >= 1 || hasInvyAlchRunes() ;
    }

    private boolean hasInvyAlchRunes(){
        return invyfireRunes() && invynatureRunes();
    }

    private boolean invynatureRunes(){
        return Inventory.find( "Nature rune" ).length > 0 && Inventory.find( "Nature rune" )!= null && Inventory.find( "Nature rune" )[0].getStack() >= 1;
    }

    private boolean invyfireRunes(){
        return Inventory.find( "Fire rune" ).length > 0 && Inventory.find( "Fire rune" )!= null && Inventory.find( "Nature rune" )[0].getStack() >= 3;
    }




    public static boolean hasFood() {
        RSItem[] edible = Inventory.find(Filters.Items.actionsContains("Eat"));
        return edible[0]!= null && edible.length > 0;
    }

    /*
         * pass this method with an array of NPC IDs and whether or not you want to
         * attack other player's monsters will return the closest NPC corresponding to
         * the IDs if multi true, and closest NPC not in combat if multi false.
         **/

    //private static RSItem houseTab(String... Name){
    //    RSItem[] housetab = Inventory.find("Teleport to house");
    //   //RSObject[] portal = Objects.find(Filters.Objects.nameContains(""))
    //    if (housetab.length < 1){
    //        return null;
    //   }

    //     return

    //}





    public static int	SPELLBOOK_SETTING_VAR = 4070,
            STANDARD_SPELLBOOK_VALUE = 0,
            ANCIENT_SPELLBOOK_VALUE = 1,
            LUNAR_SPELLBOOK_VALUE = 2,
            ARCEUUS_SPELLBOOK_VALUE = 3;


    public static boolean isOnStandardSpellbook(){
        return RSVarBit.get(SPELLBOOK_SETTING_VAR).getValue() == STANDARD_SPELLBOOK_VALUE;
    }
    public static boolean isOnArceuusSpellbook(){
        return RSVarBit.get(SPELLBOOK_SETTING_VAR).getValue() == ARCEUUS_SPELLBOOK_VALUE;
    }
    public static boolean isOnAncientSpellbook(){
        return RSVarBit.get(SPELLBOOK_SETTING_VAR).getValue() == ARCEUUS_SPELLBOOK_VALUE;
    }
    public static boolean isOnLunarSpellbook() {
        return RSVarBit.get(SPELLBOOK_SETTING_VAR).getValue() == LUNAR_SPELLBOOK_VALUE;
    }

    // this is an instanced class you can use throughout the whole script.
    private ABCUtil abcInstance = new ABCUtil();

    // vars
    private boolean shouldHover, shouldOpenMenu = false;

    // This method will set some booleans for if you should hover, or right click hover the next target. Call it when you want to ask "Should I hover my next target?"
    public void setHoverAndMenuOpenBooleans() {
        this.shouldHover = abcInstance.shouldHover();
        this.shouldOpenMenu= abcInstance.shouldOpenMenu();
    }

    // this will be called when you want to hover the next target. Always call this Anytime you might want to hover the next target. The above method will determine if you // do or not
    public void executeHoverOrMenuOpen(RSObject target) {
        if (Mouse.isInBounds() && this.shouldHover) {
            Clicking.hover(target);
            if (this.shouldOpenMenu)
                if (!ChooseOption.isOpen())
                    DynamicClicking.clickRSObject(target, 3);
        }
    }

    double d = timeToKill;
    long sleeptime = (long) d; //129

    // As an extra, when your player just sits still, you want to be calling this:
    public void performTimedActions() {

        if (abcInstance.shouldCheckTabs())
            abcInstance.checkTabs();

        if (abcInstance.shouldCheckXP()) {
            abcInstance.checkXP();
            General.sleep(General.randomSD(750, 1500, 1000, 150)); // sleep makes sure it checks xp longer.
        }

        if (abcInstance.shouldExamineEntity()){
            General.sleep(sleeptime);
            General.println("Slept for "+ sleeptime);
        }


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


    private static boolean doAttack(final RSNPC npc) {
        Clickable menuNode = Hovering.getHoveringItem();

        if (Hovering.isHovering() && Hovering.getShouldOpenMenu() && menuNode != null)  // if we still had the menu open for this NPC as a 'hover' NPC.
            return Clicking.click(menuNode);
        else
            return Clicking.click("Attack", npc);
    }

    /**
     * Returns a condition that returns true when we are in combat with the given npc.
     * @param npc
     * @return
     */





    public static void navigateToNPC(final RSNPC npc){
        if (!npc.isOnScreen()){
            npc.adjustCameraTo();
            if (Timing.waitCondition(() -> npc.isOnScreen()&& npc.isClickable(), General.random(2000, 3000))){

            } else {
                PathFinding.aStarWalk(npc.getPosition());
                Timing.waitCondition(() -> npc.isOnScreen()&& npc.isClickable(), General.random(2000, 3000));
            }
        }

    }
    /**
     * Attacks an NPC
     * @param npc
     * @return true if in combat with npc, false otherwise.
     */
    public static void attackNPC(final RSNPC npc) {

        if (npc == null)
            return;

        if (npc.isInCombat() && !Combat.isUnderAttack())
            return;

        if (!npc.isOnScreen())
            navigateToNPC(npc);
            //Camera.turnToTile(npc);

        if (!npc.isInCombat() && !Combat.isUnderAttack() && npc.isValid() && Movement.canReach(npc) && npc.getInteractingCharacter() == null) {
            if (doAttack(npc)) {
                if (Timing.waitCondition(() -> npc.isInCombat() || npc.isInteractingWithMe() || Combat.isUnderAttack(), General.random(2800, 4500))) {
                    if (Combat.isUnderAttack() && !npc.isInteractingWithMe()) { // A different npc is attacking.. lets attack that one instead.
                        RSCharacter[] characters = Combat.getAttackingEntities();
                        if (characters.length > 0 && characters[0] != null) {
                            if (characters[0].isInteractingWithMe() && characters[0].isInCombat()) {
                                attackNPC(npc);
                                return;
                            }
                        }
                    }
                    // Determine if we have won the resource or not, and act accordingly.

                }
            }
        }
    }

    private boolean isFighting(){
        RSCharacter ch = Player.getRSPlayer().getInteractingCharacter();
        if (ch != null && ch.getPosition().distanceTo(Player.getPosition()) <= 4 && ch.isInCombat()){
            if (Timing.waitCondition(() -> ch != null && ch.getPosition().distanceTo(Player.getPosition()) <= 4 && ch.isInCombat(), General.random(2000, 3000))){
                return true;
            }
        }
        return false;

    }

    private int eat_at = abcInstance.generateEatAtHP();

    private void eat(){
        if (getHpPercent() <= eat_at ) {

            eatFood();
            sleep(300, 500);
            for (int i = 0; i < 10 && Player.getAnimation() == EATING_ANIMATION; i++) {
                sleep(90, 110);
            }
            eat_at = abcInstance.generateEatAtHP();

        }
    }

    public void eatFood(){

        if (GameTab.getOpen() != GameTab.TABS.INVENTORY) {
            GameTab.open(GameTab.TABS.INVENTORY);
        }

        RSItem[] food = Inventory.find(Filters.Items.actionsContains("Eat"));
        if (food.length > 0) {
            food[0].click("Eat");
            General.sleep(500, 1200);
            General.println("Ate food..");

        }


    }

    public static int getHpPercent(){
        return (Skills.getCurrentLevel(Skills.SKILLS.HITPOINTS) * 100) / Skills.getActualLevel(Skills.SKILLS.HITPOINTS);
    }



    private int run_at = abcInstance.generateRunActivation();

    private void runCheck(){
        if (!Game.isRunOn() && Game.getRunEnergy() >= run_at) {
            if (Options.setRunOn(true)){
                Timing.waitCondition(() -> Options.isRunEnabled(), General.random(600, 1100));
                run_at = abcInstance.generateRunActivation();
            }
        }
    }

    private void checkStats(){
        if (Skills.getActualLevel(Skills.SKILLS.STRENGTH) < 10) {
            if (Combat.getSelectedStyleIndex() != 1) {
                General.println("Setting attack Style to Strength");
                Combat.selectIndex(1);
            }
        }
        //if strenth  is less than 15-20 and more than 10 and attack is less than 10
        if (Skills.getActualLevel(Skills.SKILLS.STRENGTH) >= 10  && Skills.getActualLevel(Skills.SKILLS.ATTACK) < 10) {
            if (Combat.getSelectedStyleIndex() != 0) {
                General.println("Setting attack Style to Attack");
                Combat.selectIndex(0);
            }

        }

        //if (Skills.getActualLevel(Skills.SKILLS.ATTACK) >= 10 && Skills.getActualLevel(Skills.SKILLS.STRENGTH) >= 10 && Skills.getActualLevel(Skills.SKILLS.DEFENCE) >= 10){
        //    continueRunning = false;
       // }



                //if strength is less than 30 and greater than 20 and attack is less than 20 then
                if (Skills.getActualLevel(Skills.SKILLS.STRENGTH) < strenth3 && Skills.getActualLevel(Skills.SKILLS.STRENGTH) > strenth2 && Skills.getActualLevel(Skills.SKILLS.ATTACK) < attack2) {
                    if (Combat.getSelectedStyleIndex() != 0) {
                        General.println("Setting attack Style to Attack");
                        Combat.selectIndex(0);
                    }

                }

                // attack is less than 30 and attack is greater than 20 and strength is less than 30
                if (Skills.getActualLevel(Skills.SKILLS.ATTACK) < attack3 && Skills.getActualLevel(Skills.SKILLS.ATTACK) > attack2 && Skills.getActualLevel(Skills.SKILLS.STRENGTH) < strenth3) {
                    if (Skills.getActualLevel(Skills.SKILLS.STRENGTH) >= 20 && Skills.getActualLevel(Skills.SKILLS.ATTACK) >= 20 && Skills.getActualLevel(Skills.SKILLS.DEFENCE) <= 10){
                        if (Combat.getSelectedStyleIndex() != 3) {
                            General.println("Setting attack Style to Defence");
                            Combat.selectIndex(3);
                        }
                    } else {
                        if (Combat.getSelectedStyleIndex() != 1) {
                            General.println("Setting attack Style to Strength");
                            Combat.selectIndex(1);
                        }
                    }


                }
                // adsadadasdasdsadattack is less than 30 and attack is greater than 20 and strength is less than 30
                if (Skills.getActualLevel(Skills.SKILLS.STRENGTH) < strenth4 && Skills.getActualLevel(Skills.SKILLS.STRENGTH) > strenth3 && Skills.getActualLevel(Skills.SKILLS.ATTACK) < attack3) {
                    if (Combat.getSelectedStyleIndex() != 0) {
                        General.println("Setting attack Style to Attack");
                        Combat.selectIndex(0);
                    }
                }
                //dadsddasdasdasada attack is less than 30 and attack is greater than 20 and strength is less than 30
                if (Skills.getActualLevel(Skills.SKILLS.ATTACK) >= attack4 && Skills.getActualLevel(Skills.SKILLS.STRENGTH) < strenth4) {
                    if (Combat.getSelectedStyleIndex() != 1) {
                        General.println("Setting attack Style to Strength");
                        Combat.selectIndex(1);
                    }
                }
                if (Skills.getActualLevel(Skills.SKILLS.STRENGTH) >= 40 && Skills.getActualLevel(Skills.SKILLS.ATTACK) < attack4) {
                    if (Combat.getSelectedStyleIndex() != 0) {
                        General.println("Setting attack Style to Attack");
                        Combat.selectIndex(0);
                    }
                }

                if (Skills.getActualLevel(Skills.SKILLS.STRENGTH) >= 40 && Skills.getActualLevel(Skills.SKILLS.ATTACK) >= 40) {
                    if (Combat.getSelectedStyleIndex() != 3) {
                        General.println("Setting attack Style to Defence");
                        Combat.selectIndex(3);
                    }
                }



        if (Skills.getActualLevel(Skills.SKILLS.ATTACK) == 40 && Skills.getActualLevel(Skills.SKILLS.STRENGTH) == 40 && Skills.getActualLevel(Skills.SKILLS.DEFENCE) == General.random(30, 40)) {
                   continueRunning = false;
                }
    }





}
