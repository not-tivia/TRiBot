package scripts.cluehuntercollector;

import org.tribot.api.Clicking;
import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.input.Mouse;
import org.tribot.api.util.abc.ABCUtil;
import org.tribot.api2007.*;
import org.tribot.api2007.ext.Filters;
import org.tribot.api2007.types.RSInterface;
import org.tribot.api2007.types.RSItem;
import org.tribot.api2007.types.RSNPC;
import org.tribot.api2007.types.RSObject;
import org.tribot.script.Script;
import org.tribot.script.ScriptManifest;
import org.tribot.script.interfaces.Arguments;
import org.tribot.script.interfaces.MessageListening07;
import org.tribot.script.interfaces.Painting;
import org.tribot.util.Util;
import scripts.api.FluffeePaint.FluffeesPaint;
import scripts.api.FluffeePaint.PaintInfo;
import scripts.api.dax_api.api_lib.DaxWalker;
import scripts.api.dax_api.api_lib.models.DaxCredentials;
import scripts.api.dax_api.api_lib.models.DaxCredentialsProvider;
import scripts.api.dax_api.walker_engine.WalkingCondition;
import scripts.api.interaction.Interactions;
import scripts.cluehuntercollector.data.Constants;
import scripts.cluehuntercollector.gui.GUI;
import scripts.cluehuntercollector.tasks.items.*;

import java.awt.*;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.HashMap;

import static scripts.api.FluffeePaint.FluffeesPaint.PaintLocations.INVENTORY_AREA;
import static scripts.cluehuntercollector.data.Constants.AREA_GE;
import static scripts.cluehuntercollector.data.Constants.AREA_SPADE;
import static scripts.cluehuntercollector.data.Vars.*;

@ScriptManifest(authors = {"adamhackz"}, category = "Tools", name = "ClueHunterCollector", description = "Collects the clue hunter outfit for wintertodt/fashionscape", version = 1)


public class ClueHunterCollector extends Script implements Painting, PaintInfo, MessageListening07, Arguments {

    public static final File SETTINGS_PATH = new File(Util.getWorkingDirectory() + File.separator + "adamhackz" + File.separator, "scriptname_" + "settings.ini");
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

    private final int eatAt = abcInstance.generateEatAtHP();

    @Override
    public void onPaint(Graphics graphics) {
        FLUFFEES_PAINT.paint(graphics);
    }

    @Override
    public String[] getPaintInfo() {
        NumberFormat.getInstance();
        long runtime = (Timing.currentTimeMillis() - Constants.startTime) / 1000;
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
                if (arg.contains("outfit")) {
                    tasks.add("cloak");
                    tasks.add("garb");
                    tasks.add("trousers");
                    tasks.add("glovesandboots");
                    requiredInventorySpace = requiredInventorySpace + 5;
                }
                if (arg.contains("helm")) {
                    tasks.add("helm");
                    requiredInventorySpace = requiredInventorySpace + 4;
                }
                if (arg.contains("trousers")) {
                    tasks.add("trousers");
                    requiredInventorySpace = requiredInventorySpace + 1;
                }
                if (arg.contains("cloak")) {
                    tasks.add("cloak");
                    requiredInventorySpace = requiredInventorySpace + 1;
                }
                if (arg.contains("garb")) {
                    tasks.add("garb");
                    requiredInventorySpace = requiredInventorySpace + 1;
                }
                if (arg.contains("glovesandboots")) {
                    tasks.add("glovesandboots");
                    requiredInventorySpace = requiredInventorySpace + 2;
                }
                if (arg.contains("stamina")) {
                    usingStamina = true;
                    requiredInventorySpace = requiredInventorySpace + 1;
                }
                if (arg.contains("food")) {
                    usingFood = true;
                    foodName = arg.substring(arg.lastIndexOf(":") + 1);

                    foodCount = General.random(4, 10);
                    requiredInventorySpace = requiredInventorySpace + foodCount;
                }
                General.println("We will need " + requiredInventorySpace + " free spaces." + "Tasks: " + tasks.toString());
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
        DaxWalker.setGlobalWalkingCondition(() -> {
            //check health and eat
            if (getHpPercent() <= eatAt) {
                Eat();
            }
            runHandler();

            return WalkingCondition.State.CONTINUE_WALKER;
        });

        Mouse.setSpeed(General.random(95, 280));
        if (!inGame()) {
            Timing.waitCondition(() -> inGame(), General.random(2500, 4100));
        } else {
            Collections.shuffle(tasks);
            General.println("Current Account: " + Player.getRSPlayer().getName());

        }


        return true;
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

    public State getState() {
        if (hasCheckedBank) {
            if (needsSpade) {
                return State.COLLECT_SPADE;
            }
            return State.COLLECT_OUTFIT;
        } else {
            return State.CHECK_BANK;
        }
    }

    public int loop() {
        scriptState = getState();
        General.sleep(50);


        switch (scriptState) {
            case CHECK_BANK:
                          /*
                          We need a minimum of 7 spaces for gear if not collecting the helm
                          11 if we are collecting the helm which includes the required *Nature rune,Leather boots,Superantipoison(1)*
                          and then we need to account for food if food is selected in the GUI
                          The reason we do not deposit everything on start all the time,
                          is to account for people using script queueing and daxwalker jewlery/tab teleports
                           */

                if (Banking.isInBank() || AREA_GE.contains(Player.getPosition())) {
                    if (Banking.isBankScreenOpen()) {
                        if (openInventorySpots() <= requiredInventorySpace) {
                            Banking.depositAll();
                            Timing.waitCondition(() -> openInventorySpots() > requiredInventorySpace, General.random(3000, 6000));
                        }

                        if (!hasItem("Spade")) {
                            if (bankHasItem("Spade")) {
                                if (Banking.withdraw(1, "Spade")) {
                                    Timing.waitCondition(() -> hasItem("Spade"), General.random(3000, 6000));
                                }
                            } else {
                                needsSpade = true;
                            }
                        }

                        // We withdraw coins to be able to take a boat to go to brimhaven or other ships
                        if (!hasItem("Coins") && bankHasItem("Coins")) {
                            if (Banking.withdraw(General.random(5000, 10000), "Coins")) {
                                Timing.waitCondition(() -> hasItem("Coins"), General.random(3000, 6000));

                            }
                        }

                        if (tasks.contains("helm")) {
                            /*
                            If we do not have the item in our inventory
                            Then check if we have the item in the bank
                            If we have the item in the bank, then withdraw,
                            If we do not have any of the items,
                            them remove helm from our tasks list
                             */
                            if (!hasItem("Nature rune")) {
                                if (bankHasItem("Nature rune")) {
                                    if (Banking.withdraw(1, "Nature rune")) {
                                        Timing.waitCondition(() -> hasItem("Nature rune"), General.random(3000, 6000));
                                    }
                                } else {
                                    tasks.remove("helm");
                                    General.println("Removed 'helm' from our tasks list");
                                }
                            }
                            if (!hasItem("Superantipoison(1)")) {
                                if (bankHasItem("Superantipoison(1)")) {
                                    if (Banking.withdraw(1, "Superantipoison(1)")) {
                                        Timing.waitCondition(() -> hasItem("Superantipoison(1)"), General.random(3000, 6000));
                                    }
                                } else {
                                    tasks.remove("helm");
                                    General.println("Removed 'helm' from our tasks list");
                                }
                            }
                            if (!hasItem("Leather boots")) {
                                if (bankHasItem("Leather boots")) {
                                    if (Banking.withdraw(1, "Leather boots")) {
                                        Timing.waitCondition(() -> hasItem("Leather boots"), General.random(3000, 6000));
                                    }
                                } else {
                                    tasks.remove("helm");
                                    General.println("Removed 'helm' from our tasks list");
                                }
                            }
                        }

                        if (usingFood) {
                            if (!hasItem(foodName) && bankHasItem(foodName)) {
                                if (Banking.withdraw(foodCount, foodName)) {
                                    Timing.waitCondition(() -> hasItem(foodName), General.random(3000, 6000));
                                }
                            }
                        }
                        if (usingStamina) {
                            if (!hasItemFilter("Stamina")) {
                                RSItem[] staminaBank = Banking.find("Stamina potion(4)", "Stamina potion(3)", "Stamina potion(2)", "Stamina potion(1)");
                                if (staminaBank.length > 0 && staminaBank[0] != null) {
                                    if (Banking.withdraw(1, staminaBank[0].getID())) {
                                        Timing.waitCondition(() -> hasItemFilter("Stamina potion(4)", "Stamina potion(3)", "Stamina potion(2)", "Stamina potion(1)"), General.random(2400, 5000));
                                    }
                                }
                            }
                        }


                        hasCheckedBank = true;
                    } else {

                        /*
                        Closes the new bank interface if needed
                        else open bank
                         */
                        RSInterface bankTutorialInterface = Interfaces.get(664, 28);
                        if (bankTutorialInterface != null) {
                            if (bankTutorialInterface.click()) {
                                Timing.waitCondition(() -> !Interfaces.isInterfaceSubstantiated(664), General.random(3000, 6000));
                            }
                        }

                        if (Banking.openBank()) {
                            Timing.waitCondition(() -> Banking.isBankScreenOpen(), General.random(2500, 4100));
                        }

                    }
                } else {
                    General.println("Walking to the nearest bank");
                    if (DaxWalker.walkToBank()) {
                        Timing.waitCondition(() -> !Player.isMoving(), General.random(2500, 4100));
                    }
                }


                break;

            case COLLECT_SPADE:
                if (hasItem("Spade")) {
                    needsSpade = false;
                }
                if (openInventorySpots() >= 2) {
                    Interactions.interactWithObject("Take", "Spade", AREA_SPADE);
                } else {
                    if (isAtBank()) {
                        if (Banking.isBankScreenOpen()) {
                            Banking.depositAllExcept("Spade");
                            Timing.waitCondition(() -> Inventory.getAll().length < 2, General.random(2000, 4000));
                            Banking.close();
                        } else {
                            Banking.openBank();
                            Timing.waitCondition(() -> Banking.isBankScreenOpen(), General.random(600, 1100));
                        }
                    } else {
                        if (DaxWalker.walkToBank()) {
                            Timing.waitCondition(() -> !Player.isMoving(), General.random(600, 1100));
                        }
                    }
                }

                break;

            case COLLECT_OUTFIT:

                if (tasks.isEmpty()) {
                    General.println("Walking to the nearest bank and ending script.");
                    if (DaxWalker.walkToBank()) {
                        Timing.waitCondition(() -> !Player.isMoving(), General.random(2500, 4100));
                    }
                    continueRunning = false;
                } else {
                    if (Banking.isBankScreenOpen()){
                        Banking.close();
                        Timing.waitCondition(() -> Banking.isBankScreenOpen(), General.random(1200, 2000));

                    }
                    if (tasks.get(0).contains("cloak")) {
                        if (hasItem("Clue hunter cloak")) {
                            tasks.remove(0);
                        } else {
                            General.println("Our new task is " + tasks.get(0));
                            Cloak cloak = new Cloak();
                            cloak.run();
                        }

                    } else if (tasks.get(0).contains("garb")) {
                        if (hasItem("Clue hunter garb")) {
                            tasks.remove(0);
                        } else {
                            General.println("Our new task is " + tasks.get(0));
                            Garb garb = new Garb();
                            garb.run();
                        }
                    } else if (tasks.get(0).contains("glovesandboots")) {
                        if (hasItem("Clue hunter gloves") && hasItem("Clue hunter boots")) {
                            tasks.remove(0);
                        } else {
                            General.println("Our new task is " + tasks.get(0));
                            GlovesAndBoots glovesAndBoots = new GlovesAndBoots();
                            glovesAndBoots.run();
                        }
                    } else if (tasks.get(0).contains("trousers")) {
                        if (hasItem("Clue hunter trousers")) {
                            tasks.remove(0);
                        } else {
                            General.println("Our new task is " + tasks.get(0));
                            Trousers trousers = new Trousers();
                            trousers.run();
                        }
                    } else if (tasks.get(0).contains("helm")) {
                        if (hasItem("Helm of raedwald")) {
                            tasks.remove(0);
                        } else {
                            General.println("Our new task is " + tasks.get(0));
                            Helm helm = new Helm();
                            helm.run();
                        }
                    }


                }


                break;


        }
        return 50;
    }

    private boolean isAtBank() {
        RSObject[] bank = Objects.findNearest(5, "Bank booth", "Bank chest", "Bank Booth");
        RSNPC[] banker = NPCs.findNearest("Banker");
        return bank.length > 0 || banker.length > 0;
    }

    private boolean hasItemFilter(String... ItemName) {
        RSItem[] items = Inventory.find(Filters.Items.nameContains(ItemName));
        return items.length > 0 && items[0] != null;
    }

    private boolean bankHasItemFilter(String... ItemName) {
        RSItem[] items = Banking.find(Filters.Items.nameContains(ItemName));
        return items.length > 0 && items[0] != null;
    }

    private boolean hasItem(String... ItemName) {
        RSItem[] items = Inventory.find(ItemName);
        return items.length > 0 && items[0] != null;
    }

    private boolean bankHasItem(String... ItemName) {
        RSItem[] items = Banking.find(ItemName);
        return items.length > 0 && items[0] != null;
    }

    private int openInventorySpots() {
        return 28 - Inventory.getAll().length;
    }

    private void Eat() {
        if (GameTab.getOpen() != GameTab.TABS.INVENTORY) {
            GameTab.open(GameTab.TABS.INVENTORY);
        }
        RSItem[] food = Inventory.find("Trout");
        if (food.length > 0) {
            food[0].click("Eat");
            General.sleep(500, 1200);
            General.println("Ate food..");
        }
    }

    private int getHpPercent() {
        return (Skills.getCurrentLevel(Skills.SKILLS.HITPOINTS) * 100) / Skills.getActualLevel(Skills.SKILLS.HITPOINTS);
    }




    public enum State {
        CHECK_BANK, COLLECT_OUTFIT, COLLECT_SPADE
    }

    private void runHandler() {
        if (!Banking.isBankScreenOpen()) {

            if (hasItemFilter("Stamina potion") && !staminaActive() && Game.getRunEnergy() <= run_at) {
                RSItem[] stamina = Inventory.find(Filters.Items.nameContains("Stamina potion"));
                if (stamina.length > 0 && stamina[0] != null) {
                    if (Clicking.click("Drink", stamina[0])) {
                        Timing.waitCondition(() -> staminaActive(), General.random(1200, 2000));
                        General.println("Drank stamina potion");
                    }
                }
            }
            if (!Game.isRunOn() && Game.getRunEnergy() >= run_at) {
                if (Options.setRunOn(true)) {
                    Timing.waitCondition(() -> Options.isRunEnabled(), General.random(600, 1100));
                    run_at = abcInstance.generateRunActivation();
                    General.println("Enabled run. Next run at " + run_at);
                }
            }
        }
    }

    private boolean staminaActive() {
        return Game.getVarBit(25) == 1;
    }




}




