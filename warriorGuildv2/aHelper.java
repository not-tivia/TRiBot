package scripts.warriorGuildv2;

import org.tribot.api.DynamicClicking;
import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.input.Keyboard;
import org.tribot.api.input.Mouse;
import org.tribot.api.interfaces.Positionable;
import org.tribot.api.types.generic.Condition;
import org.tribot.api.types.generic.Filter;
import org.tribot.api.util.abc.ABCUtil;
import org.tribot.api2007.*;
import org.tribot.api2007.ext.Filters;
import org.tribot.api2007.types.*;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

public class aHelper {
 private boolean hasNatures = false;
 private  boolean hasFires = false;
 private boolean continueRunning = true;

 private boolean click = false;
    private String highalch = Magic.getSelectedSpellName();
    private String ALCH = "High Level alchemy";

    private static ABCUtil abc = new ABCUtil();
    public static int eatAtHP = Combat.getMaxHP() / 2;

    public static boolean underAttack() {
        if (Player.getRSPlayer().isInCombat()) {
            return true;
        } else {
            if (!Player.getRSPlayer().isInCombat());
        }
        return false;

    }

    private void castAlch() {
        if (canAlch()) {

            if (Magic.isSpellSelected() && (!highalch.equals(ALCH))) {   // has runes and spell is selected thats not high alch
                if (!Game.getUptext().contains("High Level Alchemy")) {     // and the uptext isnt High alch
                    General.println("Something went wrong trying to Cast High Alch -- > Report to adamhackz"); //then print debug
                } else {
                    General.sleep(General.randomSD(70, 210, 140, 70));
                    Magic.selectSpell(ALCH);                               // select high alch
                    if (Game.getUptext().contains("High Level Alchemy")) {
                        General.sleep(General.randomSD(70, 210, 140, 70));
                        Mouse.click(1);
                        //General.sleep(General.randomSD(70, 210, 140, 70));
                        //Mouse.click(1);
                        General.sleep(General.randomSD(500, 700, 600, 100));
                        if (Player.getAnimation() > -1) {
                            General.println("Cast Successful");

                            //int newalchcounter = alchcounter +1;
                            //General.println(newalchcounter);
                        }
                    }

                    // if alch successful then alchcounter goes up
                }
            } else {
                General.sleep(General.randomSD(70, 210, 140, 70));
                Magic.selectSpell(ALCH);
                if (Game.getUptext().contains("High Level Alchemy")) {
                    General.sleep(General.randomSD(70, 210, 140, 70));
                    Mouse.click(1);
                    //General.sleep(General.randomSD(70, 210, 140, 70));
                    //Mouse.click(1);
                    General.sleep(General.randomSD(500, 700, 600, 100));
                    if (Player.getAnimation() > -1) {
                        General.println("Cast Successful");

                    }
                }
            }
        } else {
            continueRunning = false;
        }
    }

    private static boolean canAlch(){
        if (Skills.getActualLevel(Skills.SKILLS.MAGIC) >= 55) {
            RSItem[] natures = Inventory.find("Nature rune");
            RSItem[] fires = Inventory.find("Fire rune");
            RSItem[] fires2 = Equipment.find("Staff of fire");
            RSItem[] fires3 = Equipment.find("Fire battlestaff");
            RSItem[] fires4 = Equipment.find("Tomb of fire");
            RSItem[] fires5 = Equipment.find("Smoke battlestaff");
            if (natures.length > 0 && (fires.length > 0 && fires[0].getStack() > 3 || fires2.length > 0 || fires3.length > 0 || fires4.length > 0 || fires5.length > 0)) {

                return canAlch();
            }

            return true;
        }
        else return false;
    }


    public static boolean hasFood() {
        Filter<RSItem> food = Filters.Items.actionsContains("Eat");
        RSItem[] edible = Inventory.find(food);
        return edible != null && edible.length > 0;
    }

    /*
         * pass this method with an array of NPC IDs and whether or not you want to
         * attack other player's monsters will return the closest NPC corresponding to
         * the IDs if multi true, and closest NPC not in combat if multi false.
         **/




    public static RSItem alchItems(int... ids){
        RSItem[] items = Inventory.find(ids);
        RSItem alchItems = null;
        if (items.length < 1) {
            return null;
        }
        for (RSItem item : items) {
            General.sleep(General.randomSD(70, 210, 140, 70));
            Magic.selectSpell("High Level alchemy");
            Timing.waitCondition(new Condition() {
                @Override
                public boolean active() {
                    General.sleep(General.randomSD(70, 210, 140, 70));
                    return Game.isUptext("->");
                }
            }, General.random(1000, 1200));
                General.sleep(General.randomSD(70, 210, 140, 70));
                item.click();
                General.sleep(650, 1200);
                alchItems = item;

        }
        return alchItems;
    }



    public static RSNPC findNextTarget(String[] targetName, boolean multi) {
        if (targetName != null) {
            RSNPC[] targets = NPCs.find(targetName);
            if (multi) {
                RSNPC[] sortedTargets = NPCs.sortByDistance(Player.getPosition(), targets);
                if (sortedTargets != null && sortedTargets.length > 0) {
                    return sortedTargets[0];
                } else {
                    return null;
                }
            } else {
                RSNPC[] sortedTargets = NPCs.sortByDistance(Player.getPosition(), targets);
                if (sortedTargets != null && sortedTargets.length > 0) {
                    for (int i = 0; i < sortedTargets.length; i++) {
                        if (!sortedTargets[i].isInCombat()) {
                            return sortedTargets[i];
                        }
                    }
                    // all nearby targets are in combat, try again soon.
                    return null;
                } else {
                    return null;
                }

            }
        } else {
            return null;
        }
    }



    /*
     * attacks the given target. Attacks an already in combat target only if boolean
     * multi is passed as true
     */
    public static boolean attackTarget(RSNPC target, boolean multi) {
        if (target != null) {
            if (!target.isOnScreen()) {
                Walking.clickTileMM(target.getPosition(), 1);
                Camera.turnToTile(target.getPosition());
            }
            if (DynamicClicking.clickRSNPC(target, "Attack")) {
                Timing.waitCondition(new Condition() {
                    @Override
                    public boolean active() {
                        General.sleep(100);
                        return Player.getRSPlayer().isInCombat() || Combat.isUnderAttack();
                    }
                }, General.random(2500, 5800));
            }
        }
        return true;
    }

    public static boolean eatFood() {
        // System.out.print("ABC2 eat at hp at beginning of eatFood method: "+eatAtHP);
        if (!hasFood()) {
            return false;
        } else {
            Filter<RSItem> food = Filters.Items.actionsContains("Eat");
            if (food != null) {
                RSItem[] edible = Inventory.find(food);
                if (edible != null && edible.length > 0) {
                    if (edible[0].click("Eat")) {
                        Timing.waitCondition(new Condition() {
                            @Override
                            public boolean active() {
                                General.sleep(100);
                                return Player.getAnimation() == 829;
                            }
                        }, 2000);
                    }
                    // Removed as ABC2 eating was causing the bot to try to eat up to 66hp
                    // even when the max health is only 19..
                    // eatAtHP = ABCEatAtHP();
                    // System.out.print("ABC2 eat at hp at END of eatFood method: "+eatAtHP);
                    return true;
                }
            }
        }
        return false;
    }

    public static int ABCEatAtHP() {
        return abc.generateEatAtHP();
    }

    public static RSItem getItemClosestToMouse(int... ids){
        RSItem[] items = Inventory.find(ids);
        Point mouse_pos = Mouse.getPos();
        RSItem closest_item = null;
        double distance = 9999, temp_distance;
        for (RSItem item : items){
            Rectangle rectangle = item.getArea();
            if (rectangle == null){
                continue;
            }
            Point item_pos = rectangle.getLocation();
            item_pos.translate(20, 20);            if ((temp_distance = item_pos.distance(mouse_pos)) < distance){
                distance = temp_distance;                closest_item = item;
            }
        }
        return closest_item;
    }

    private boolean containsOption(RSObject object, String requested)	{
        if(object != null)                {
            for(String option : object.getDefinition().getActions())		     {
                if(option.equals(requested))			     {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isInWildy(){
        return wildernessLevel() > 0;
    }

    public static int wildernessLevel() {
        RSInterfaceChild node = Interfaces.get(90, 46);
        if (node == null)
            return 0;

        String text = node.getText();
        if (text == null)
            return 0;

        text = text.replace("Level: ", "");

        try {
            return Integer.parseInt(text);
        } catch (Exception e) {
            return 0;
        }
    }

    public static void shiftDrop(Inventory.DROPPING_PATTERN pattern, int... ids) {
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
                shiftDrop(Inventory.DROPPING_PATTERN.LEFT_TO_RIGHT, ids);
                break;
            case 2:
                shiftDrop(Inventory.DROPPING_PATTERN.TOP_TO_BOTTOM, ids);
                break;
            case 3:
                shiftDrop(Inventory.DROPPING_PATTERN.ZIGZAG, ids);
                break;
        }
    }

    public static void shiftDropAll() {
        shiftDrop();
    }

    private static int stringToInt(String string){
        int value = Integer.parseInt(string.replaceAll("[^0-9]", ""));
        return value;

    }

    private static boolean openDisplayOptionsTab() {
        if (GameTab.open(GameTab.TABS.OPTIONS)) {
            RSInterface topOptions = Interfaces.get(261, 1);
            if (topOptions != null) {
                RSInterface displayButton = topOptions.getChild(0);
                if (displayButton != null) {
                    if (displayButton.getTextureID() != 762) {
                        if(displayButton.click("Display")) {
                            Timing.waitCondition(new Condition() {
                                @Override
                                public boolean active() {
                                    General.sleep(100);
                                    return displayButton.getTextureID() == 762;
                                }
                            }, 2000);
                            return displayButton.getTextureID() == 762;
                        }
                    } else {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    private static boolean openChatOptionsTab() {
        if (GameTab.open(GameTab.TABS.OPTIONS)) {
            RSInterface topOptions = Interfaces.get(261, 1);
            if (topOptions != null) {
                RSInterface chatButton = topOptions.getChild(4);
                if (chatButton != null) {
                    if (chatButton.getTextureID() != 762) {
                        if(chatButton.click("Chat")) {
                            Timing.waitCondition(new Condition() {
                                @Override
                                public boolean active() {
                                    General.sleep(100);
                                    return chatButton.getTextureID() == 762;
                                }
                            }, 2000);
                            return chatButton.getTextureID() == 762;
                        }
                    } else {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean openControlsOptionsTab() {
        if (GameTab.open(GameTab.TABS.OPTIONS)) {
            RSInterface topOptions = Interfaces.get(261, 1);
            if (topOptions != null) {
                RSInterface controlsButton = topOptions.getChild(6);
                if (controlsButton != null) {
                    if (controlsButton.getTextureID() != 762) {
                        if(controlsButton.click("Controls")) {
                            Timing.waitCondition(new Condition() {
                                @Override
                                public boolean active() {
                                    General.sleep(100);
                                    return controlsButton.getTextureID() == 762;
                                }
                            }, 2000);
                            return controlsButton.getTextureID() == 762;
                        }
                    } else {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean openAdvancedOptions() {
        if (Interfaces.get(60) != null) {
            return true;
        }
        if (GameTab.open(GameTab.TABS.OPTIONS)) {
            if (openDisplayOptionsTab()) {
                RSInterface openAdvanced = Interfaces.get(261, 21);
                if (openAdvanced != null && !openAdvanced.isHidden()) {
                    if(openAdvanced.click("Configure ")) {
                        Timing.waitCondition(new Condition() {
                            @Override
                            public boolean active() {
                                return Interfaces.get(60) != null;
                            }
                        }, 4500);
                        return Interfaces.get(60) != null;
                    }
                }
            }

        }
        return false;
    }

    private static boolean closeAdvancedOptions(){
        if(Interfaces.get(60)==null)
            return true;
        else {
            RSInterface top = Interfaces.get(60,2);
            if(top!=null&&!top.isHidden()){
                RSInterface close = top.getChild(11);
                if(close!=null&!close.isHidden()){
                    if(close.click()) {
                        Timing.waitCondition(new Condition() {
                            @Override
                            public boolean active() {
                                return Interfaces.get(60) == null;
                            }
                        }, 3000);
                        General.sleep(750);
                        return Interfaces.get(60) == null;
                    }
                }
            }
        }
        return false;
    }

    private static int getPlayerAttackOption(){
        if(openControlsOptionsTab()) {
            RSInterface optionsBox = Interfaces.get(261, 67);
            if(optionsBox!=null&&!optionsBox.isHidden()){
                RSInterface currentOptionMaster = optionsBox.getChild(4);
                if(currentOptionMaster!=null&&!currentOptionMaster.isHidden()){
                    String currentOption = currentOptionMaster.getText();
                    switch(currentOption){
                        case "Depends on combat levels":
                            return 1;
                        case "Always right-click":
                            return 2;
                        case "Left-click where available":
                            return 3;
                        case "Hidden":
                            return 4;
                    }
                }
            }
        }

        return 1;
    }

    private static int getNPCAttackOption(){
        if(openControlsOptionsTab()) {
            RSInterface optionsBox = Interfaces.get(261, 68);
            if(optionsBox!=null&&!optionsBox.isHidden()){
                RSInterface currentOptionMaster = optionsBox.getChild(4);
                if(currentOptionMaster!=null&&!currentOptionMaster.isHidden()){
                    String currentOption = currentOptionMaster.getText();
                    switch(currentOption){
                        case "Depends on combat levels":
                            return 1;
                        case "Always right-click":
                            return 2;
                        case "Left-click where available":
                            return 3;
                        case "Hidden":
                            return 4;
                    }
                }
            }
        }

        return 1;
    }

    private static boolean openKeybinding() {
        if(Interfaces.get(121)!=null){
            return true;
        }
        if (openControlsOptionsTab()) {
            RSInterface openKeybinding = Interfaces.get(261, 63);
            if (openKeybinding != null && !openKeybinding.isHidden()) {
                if (openKeybinding.click("Keybinding")) {
                    Timing.waitCondition(new Condition() {
                        @Override
                        public boolean active() {
                            return Interfaces.get(121) != null;
                        }
                    }, 3000);
                    return Interfaces.get(121)!=null;
                }
            }
        }
        return false;
    }

    /**
     *
     * @param on a boolean to determine we want to toggle on or off.
     */
    public static boolean setShiftClick(boolean on){
        if(openControlsOptionsTab()) {
            RSInterface toggleShiftClick = Interfaces.get(261, 65);
            if (toggleShiftClick != null&&!toggleShiftClick.isHidden()) {
                if (toggleShiftClick.getTextureID() == 761) {
                    if(on){
                        return toggleShiftClick.click("Toggle Shift Click Drop");
                    } else{
                        return true;
                    }
                } else{
                    if(on){
                        return true;
                    } else{
                        return toggleShiftClick.click("Toggle Shift Click Drop");
                    }
                }
            }
        }
        return false;
    }

    /**
     *
     * @param on a boolean to determine we want to toggle on or off.
     */
    public static boolean setMouseCamera(boolean on){
        if(openControlsOptionsTab()) {
            RSInterface toggleMouseCamera = Interfaces.get(261, 59);
            if (toggleMouseCamera != null&&!toggleMouseCamera.isHidden()) {
                if (toggleMouseCamera.getTextureID() == 761) {
                    if(on){
                        return toggleMouseCamera.click("Toggle Mouse Camera");
                    } else{
                        return true;
                    }
                } else{
                    if(on){
                        return true;
                    } else{
                        return toggleMouseCamera.click("Toggle Mouse Camera");
                    }
                }
            }
        }
        return false;
    }

    /**
     *
     * @param on a boolean to determine we want to toggle on or off.
     */
    public static boolean setMouseWheelZoom(boolean on) {
        if(openDisplayOptionsTab()) {
            RSInterface toggle = Interfaces.get(261, 4);
            if (toggle != null && !toggle.isHidden()) {
                General.println("2,1");
                RSInterface indicator = Interfaces.get(261, 6);
                if (indicator != null) {
                    General.println("2,2");
                    if (on) {
                        if (!indicator.isHidden()) {
                            return toggle.click("Select");
                        } else {
                            return true;
                        }
                    } else {
                        if (indicator.isHidden()) {
                            return toggle.click("Select");
                        } else {
                            return true;
                        }
                    }
                }
            }
            return true;
        } else {
            return false;
        }
    }


    /**
     *
     * @param on a boolean to determine we want to toggle on or off.
     */
    public static boolean setAcceptFirstAid(boolean on) {
        if (GameTab.open(GameTab.TABS.OPTIONS)) {
            RSInterface toggle = Interfaces.get(261,70);
            if (toggle != null) {
                if (toggle.getTextureID() != 762) {
                    if(on) {
                        if(toggle.click("Toggle Accept Aid")){
                            Timing.waitCondition(new Condition() {
                                @Override
                                public boolean active() {
                                    General.sleep(100);
                                    return toggle.getTextureID() == 762;
                                }
                            }, 2000);
                        }
                        return toggle.getTextureID() == 762;
                    } else {
                        return true;
                    }
                } else {
                    if(!on){
                        if(toggle.click("Toggle Accept Aid")) {
                            Timing.waitCondition(new Condition() {
                                @Override
                                public boolean active() {
                                    General.sleep(100);
                                    return toggle.getTextureID() == 761;
                                }
                            }, 2000);
                            return toggle.getTextureID() == 761;
                        }
                    } else {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     *
     * @param on a boolean to determine we want to toggle on or off.
     */
    public static boolean setRemoveRoofs(boolean on){
        if(openAdvancedOptions()){
            RSInterface advancedRSI = Interfaces.get(60);
            if(advancedRSI!=null){
                RSInterface toggle = advancedRSI.getChild(12);
                if(toggle!=null&&!toggle.isHidden()){
                    if(toggle.getTextureID()==762){
                        if(on){
                            return closeAdvancedOptions();
                        } else{
                            if(toggle.click("Roof-removal")) {
                                if (toggle.getTextureID() != 726) {
                                    return closeAdvancedOptions();
                                }
                            }
                        }
                    } else{
                        if(on){
                            if(toggle.click("Roof-removal")) {
                                return closeAdvancedOptions();
                            }
                        } else{
                            return closeAdvancedOptions();
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     *
     * @param setting an integer between 1-4 relating to which option to select. 1 being the lowest brightness, 4 being the highest brightness
     *
     */
    public static boolean setGameBrightness(int setting){
        int uncheckedID;
        if(openDisplayOptionsTab()){
            if(setting >= 1 && setting <= 4) {
                RSInterface optionMaster = Interfaces.get(261);
                if(optionMaster!=null&&!optionMaster.isHidden()){
                    RSInterface option = optionMaster.getChild(14+setting);
                    if(option!=null&&!option.isHidden()){
                        uncheckedID =  678 + setting;
                        if(option.getTextureID()==uncheckedID){
                            return option.click("Adjust Screen Brightness");
                        }
                    }
                }

            } else{
                System.err.println("setGameBrightness(int) PARAMETER MUST BE INT BETWEEN 1 & 4");
                return false;
            }

        } else{
            return false;
        }
        return true;
    }

    /**
     *
     * @param setting an integer between 1-4 relating to which option to select. 1 = "Depends on combat levels" 2 = "Always right-click" 3 = "Left-click where available", 4 = "Hidden"
     *
     */
    public static boolean setPlayerAttackOptions(int setting){
        if(openControlsOptionsTab()){
            RSInterface optionsBox = Interfaces.get(261,67);
            if(optionsBox!=null&&!optionsBox.isHidden()){
                if(getPlayerAttackOption()!=setting) {
                    RSInterface dropdown = optionsBox.getChild(3);
                    if (dropdown != null && !dropdown.isHidden()) {
                        if (dropdown.click()) {
                            Timing.waitCondition(new Condition() {
                                @Override
                                public boolean active() {
                                    return Interfaces.get(261, 83) != null && !Interfaces.get(261, 83).isHidden();
                                }
                            }, 2000);
                            RSInterface optionsMaster = Interfaces.get(261, 83);
                            if (optionsMaster != null && !optionsMaster.isHidden()) {
                                RSInterface option = optionsMaster.getChild(setting);
                                if (option != null && !option.isHidden()) {
                                    option.click("Select");
                                    if (getPlayerAttackOption() == setting) {
                                        return true;
                                    } else {
                                        return setPlayerAttackOptions(setting);
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
        return false;
    }

    /**
     *
     * @param setting an integer between 1-4 relating to which option to select. 1 = "Depends on combat levels" 2 = "Always right-click" 3 = "Left-click where available", 4 = "Hidden"
     *
     */
    public static boolean setNPCAttackOptions(int setting){
        if(openControlsOptionsTab()){
            RSInterface optionsBox = Interfaces.get(261,68);
            if(optionsBox!=null&&!optionsBox.isHidden()){
                if(getNPCAttackOption()!=setting) {
                    RSInterface dropdown = optionsBox.getChild(3);
                    if (dropdown != null && !dropdown.isHidden()) {
                        if (dropdown.click()) {
                            Timing.waitCondition(new Condition() {
                                @Override
                                public boolean active() {
                                    return Interfaces.get(261, 84) != null && !Interfaces.get(261, 84).isHidden();
                                }
                            }, 2000);
                            RSInterface optionsMaster = Interfaces.get(261, 84);
                            if (optionsMaster != null && !optionsMaster.isHidden()) {
                                RSInterface option = optionsMaster.getChild(setting);
                                if (option != null && !option.isHidden()) {
                                    option.click("Select");
                                    if (getPlayerAttackOption() == setting) {
                                        return true;
                                    } else {
                                        return setPlayerAttackOptions(setting);
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
        return false;
    }



    /**
     *
     * @param esc a boolean value to determine if 'Esc closes current interface' should be toggled or not.
     *
     */
    public static boolean setKeybindingDefault(boolean esc){
        if(openControlsOptionsTab()){

            if(openKeybinding()){
                General.println("411");
                RSInterface keybindingMaster = Interfaces.get(121);
                if(keybindingMaster!=null){
                    RSInterface defaultButton = keybindingMaster.getChild(104);
                    if(defaultButton!=null&&!defaultButton.isHidden()) {
                        if (defaultButton.click("Restore Defaults")) {
                            General.sleep(750);
                            RSInterface top = Interfaces.get(121,1);
                            if(top!=null&&!top.isHidden()){
                                RSInterface close = top.getChild(11);
                                RSInterface escToggle = keybindingMaster.getChild(103);
                                if (escToggle != null && !escToggle.isHidden()) {
                                    if (esc) {
                                        if (escToggle.getTextureID() == 697) {
                                            if(escToggle.click()) {
                                                Timing.waitCondition(new Condition() {
                                                    @Override
                                                    public boolean active() {
                                                        return escToggle.getTextureID() == 699;
                                                    }
                                                }, 3500);
                                            }
                                        }
                                        if (close != null && !close.isHidden()) {
                                            return close.click("Close");
                                        }
                                    } else {
                                        if (escToggle.getTextureID() == 699) {
                                            if (escToggle.click()) {
                                                Timing.waitCondition(new Condition() {
                                                    @Override
                                                    public boolean active() {
                                                        return escToggle.getTextureID() == 697;
                                                    }
                                                }, 3500);
                                            }
                                        }
                                        if (close != null && !close.isHidden()) {
                                            return close.click("Close");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     *
     * @param on a boolean to determine we want to toggle on or off.
     */
    public static boolean setDataOrbs(boolean on){
        if(openAdvancedOptions()){
            RSInterface advancedRSI = Interfaces.get(60);
            if(advancedRSI!=null){
                RSInterface toggle = advancedRSI.getChild(14);
                if(toggle!=null&&!toggle.isHidden()){
                    if(toggle.getTextureID()==762){
                        if(on){
                            return closeAdvancedOptions();
                        } else{
                            if(toggle.click("Data orbs")) {
                                if (toggle.getTextureID() != 762) {
                                    return closeAdvancedOptions();
                                }
                            }
                        }
                    } else{
                        if(on){
                            if(toggle.click("Data orbs")) {
                                return closeAdvancedOptions();
                            }
                        } else{
                            return closeAdvancedOptions();
                        }
                    }
                }
            }
        }
        return false;
    }


    public static boolean openNotificationsWindow(){
        if(Interfaces.get(492)!=null){
            return true;
        }
        if(openChatOptionsTab()){
            RSInterface openNotifications = Interfaces.get(261,50);
            if(openNotifications!=null&!openNotifications.isHidden()){
                if(openNotifications.click("Notifications")){
                    Timing.waitCondition(new Condition() {
                        @Override
                        public boolean active() {
                            return Interfaces.get(492)!=null;
                        }
                    }, 4000);
                    return Interfaces.get(492)!=null;
                }
            }
        }
        return false;
    }

    /**
     *
     * @param on true to toggle on, false to toggle off.
     * @param amount loot drop notification amount, enter any int if arg0 is false
     * @return
     */
    public static boolean setLootDropNotifications(boolean on, int amount){

        if(openNotificationsWindow()){
            RSInterface valueMaster = Interfaces.get(492, 7);
            RSInterface close = Interfaces.get(492,2);
            if(valueMaster!=null&&!valueMaster.isHidden()){
                RSInterface valueText = valueMaster.getChild(0);
                if(valueText!=null&!valueText.isHidden()){
                    if(on){
                        if(!valueText.getText().contains("Off")){
                            if(valueText.getText().contains(NumberFormat.getNumberInstance(Locale.US).format(amount))) {
                                if(close!=null&&!close.isHidden()){
                                    return close.click("Close");
                                }
                            } else{
                                if(valueText.click("Change")){
                                    Timing.waitCondition(new Condition() {
                                        @Override
                                        public boolean active() {
                                            return Interfaces.get(162,33)!=null&&!Interfaces.get(162,33).isHidden();
                                        }
                                    }, 3000);
                                    General.sleep(650);
                                    RSInterface typeValue = Interfaces.get(162,33);
                                    if(typeValue!=null&&!typeValue.isHidden()) {
                                        if (valueText.getText().contains("Set a value")) {
                                            if (typeValue.click())
                                                Keyboard.typeSend(String.valueOf(amount));
                                            Timing.waitCondition(new Condition() {
                                                @Override
                                                public boolean active() {
                                                    return valueText.getText().contains(NumberFormat.getNumberInstance(Locale.US).format(amount));
                                                }
                                            }, 3000);
                                            if (valueText.getText().contains(NumberFormat.getNumberInstance(Locale.US).format(amount))) {
                                                if (close != null && !close.isHidden()) {
                                                    General.println(657);
                                                    return close.click("Close");
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else{
                            if(valueText.click("Enable")){
                                Timing.waitCondition(new Condition() {
                                    @Override
                                    public boolean active() {
                                        return !valueText.getText().contains("Off");
                                    }
                                },2000);
                                General.sleep(500);
                                if(!valueText.getText().contains("Off")){
                                    int currentValue = stringToInt(valueText.getText());
                                    if(currentValue==amount)
                                        return true;
                                    if(valueText.click("Change")){
                                        Timing.waitCondition(new Condition() {
                                            @Override
                                            public boolean active() {
                                                return Interfaces.get(162,33)!=null&&!Interfaces.get(162,33).isHidden();
                                            }
                                        }, 3000);
                                        General.sleep(650);
                                        RSInterface typeValue = Interfaces.get(162,33);
                                        if(typeValue!=null&&!typeValue.isHidden()) {
                                            if (valueText.getText().contains("Set a value")) {
                                                if (typeValue.click())
                                                    Keyboard.typeSend(String.valueOf(amount));
                                                Timing.waitCondition(new Condition() {
                                                    @Override
                                                    public boolean active() {
                                                        return valueText.getText().contains(NumberFormat.getNumberInstance(Locale.US).format(amount));
                                                    }
                                                }, 3000);
                                                if (valueText.getText().contains(NumberFormat.getNumberInstance(Locale.US).format(amount))) {
                                                    if (close != null && !close.isHidden()) {
                                                        General.println(657);
                                                        return close.click("Close");
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                        }
                        //////////////
                    } else {
                        if(valueText.getText().contains("Off")){
                            return true;
                        } else{
                            if(valueText.click("Disable")){
                                Timing.waitCondition(new Condition() {
                                    @Override
                                    public boolean active() {
                                        return valueText.getText().contains("Off");
                                    }
                                }, 2500);
                                General.sleep(500);
                                if(valueText.getText().contains("Off")){
                                    if(close!=null&&!close.isHidden()){
                                        return close.click("Close");
                                    }
                                }
                            }
                        }

                    }
                }
            }

        }

        return false;
    }

    public RSPlayer getPlayerByName(String targetPlayerName) {
        RSPlayer[] players = Players.getAll(Filters.Players.nameEquals(targetPlayerName));
        return players.length > 0 ? players[0] : null;
    }

    public static final Condition CHOOSE_OPTION_CONDITION = chooseOptionOpen();
    public static final Condition IN_BANK_CONDITION = inBankCondition();
    public static final Condition IN_DIALOGUE_CONDITION = inDialogueCondition();
    public static final Condition DEPOSIT_BOX_OPEN_CONDITION = depositBoxOpenCondition();
    public static final Condition CLICK_CONTINUE_CONDITION = clickContinueCondition();
    public static final Condition KILL_CONDITION = killCondition();
    public static final Condition IN_GAME_CONDITION = inGameCondition();
    public static final Condition NOT_IN_GAME_CONDITION = notInGameCondition();
    public static final Condition BANK_LOADED_CONDITION = bankLoadedCondition();
    public static final Condition IN_COMBAT_CONDITION = inCombatCondition();
    public static final Condition ENTER_AMOUNT_CONDITION = enterAmountCondition();
    public static final Condition NOT_TRADING_CONDITION = notTradingCondition();
    public static final Condition NOT_MOVING_CONDITION = notMovingCondition();

    public static Condition objectOnScreenCondition(final RSObject object)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return object.isOnScreen();
            }
        };
    }

    private static Condition chooseOptionOpen()
    {
        return new Condition()
        {
            public boolean active()
            {
                General.sleep(100);
                return ChooseOption.isOpen();
            }
        };
    }

    private static Condition notMovingCondition()
    {
        return new Condition()
        {
            public boolean active()
            {
                General.sleep(100);
                return !Player.isMoving();
            }
        };
    }

    public static Condition interTextNotContains(int master, int child, String text)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                RSInterface inter = Interfaces.get(master, child);
                return inter != null && !inter.getText().equals(text);
            }

        };
    }

    private static Condition notTradingCondition()
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Trading.getWindowState() == null;
            }
        };
    }

    public static Condition varbitChanged(int varbit, int value)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return RSVarBit.get(varbit).getValue() != value;
            }
        };
    }

    public static Condition onWorldCondition(int world)
    {
        return new Condition()
        {
            public boolean active()
            {
                General.sleep(100);
                return WorldHopper.getWorld() == world;
            }
        };
    }

    public static Condition interfaceNotUp(int id)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                RSInterface inter = Interfaces.get(id);
                return inter == null || inter.isHidden();
            }
        };
    }

    public static Condition inventoryNotContains(int id)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Inventory.getCount(id) == 0;
            }
        };
    }

    public static Condition inventoryNotContains(String name)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Inventory.getCount(name) == 0;
            }
        };
    }

    public static Condition isEquipped(int id)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Equipment.isEquipped(id);
            }
        };
    }

    public static Condition settingEqualsCondition(int index, int setting)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Game.getSetting(index) == setting;
            }
        };
    }

    public static Condition withinDistanceOfTile(Positionable p, int threshold)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Player.getPosition().distanceTo(p) < threshold;
            }
        };
    }

    public static Condition yCoordGreaterThan(int y)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Player.getPosition().getY() > y;
            }
        };
    }

    public static Condition yCoordLessThan(int y)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Player.getPosition().getY() < y;
            }
        };
    }

    private static Condition enterAmountCondition()
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                RSInterface inter = Interfaces.get(162, 32);
                return inter != null && !inter.isHidden();
            }
        };
    }

    private static Condition inCombatCondition()
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Player.getRSPlayer().isInCombat();
            }
        };
    }

    private static Condition bankLoadedCondition()
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Banking.getAll().length > 0;
            }
        };
    }

    private static Condition clickContinueCondition()
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return NPCChat.getClickContinueInterface() != null;
            }
        };
    }

    private static Condition notInGameCondition()
    {
        return new Condition()
        {
            public boolean active()
            {
                General.sleep(100);
                return Game.getGameState() != 30 || Login.getLoginState() != Login.STATE.INGAME;
            }
        };
    }

    private static Condition inGameCondition()
    {
        return new Condition()
        {
            public boolean active()
            {
                General.sleep(100);
                return Login.getLoginState() == Login.STATE.INGAME
                        && Game.getGameState() == 30;
            }
        };
    }

    public static Condition interfaceUp(int id)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Interfaces.get(id) != null;
            }

        };
    }

    public static Condition killCondition()
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                RSCharacter target = Combat.getTargetEntity();
                RSCharacter rangedTarget = Player.getRSPlayer().getInteractingCharacter();
                return target != null && target.getHealth() <= 0 ||
                        (rangedTarget != null && rangedTarget.isInCombat()
                                && rangedTarget.isInteractingWithMe() && rangedTarget.getHealth() <= 0);
            }

        };
    }

    private static Condition inDialogueCondition()
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return NPCChat.getClickContinueInterface() != null || NPCChat.getSelectOptionInterface() != null;
            }
        };
    }

    public static Condition positionEquals(Positionable p)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Player.getPosition().equals(p);
            }
        };
    }

    private static Condition inBankCondition()
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Banking.isInBank();
            }

        };
    }

    private static Condition depositBoxOpenCondition()
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Banking.isDepositBoxOpen();
            }

        };
    }

    public static Condition planeChanged(final int startingPlane)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Player.getPosition().getPlane() != startingPlane;
            }
        };
    }

    public static Condition animationChanged(final int startingAnimation)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);

                return Player.getAnimation() != startingAnimation;
            }
        };
    }

    public static Condition inventoryContains(final String name)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Inventory.getCount(name) > 0;
            }
        };
    }

    public static Condition inAreaCondition(final RSArea area)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return area.contains(Player.getPosition());
            }

        };
    }

    public static Condition inventoryChanged(final int startingAmt)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Inventory.getAll().length != startingAmt;
            }
        };
    }

    public static Condition tileOnScreen(RSTile tile)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return tile.isOnScreen();
            }
        };
    }

    public static Condition uptextContains(String str)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Game.isUptext(str);
            }
        };
    }

    public static Condition tradeContains(String name, boolean other)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Trading.getCount(other, name) > 0;
            }
        };
    }

    public static Condition tradeContains(String name, int amt, boolean other)
    {
        return new Condition()
        {
            @Override
            public boolean active()
            {
                General.sleep(100);
                return Trading.getCount(other, name) >= amt;
            }
        };
    }




/*
only shows XP for skills that have gained XP goes in paint
    long timeRan = getRunningTime();
    double secondsRan = (int) (timeRan/1000);double hoursRan = secondsRan/3600;int x = 15;for (int i = 0; i < XP.length; i ++){
        if (XP[i] != Skills.getXP(Names[i])){
            g.drawString(Names[i] + ": " + NumberFormat.getNumberInstance(Locale.US).format(Math.round(((Skills.getXP(Names[i]) - XP[i]))/hoursRan)) + " Xp/h", 275, (380 + x));
            x += 15;
        }
    }
*/

}


