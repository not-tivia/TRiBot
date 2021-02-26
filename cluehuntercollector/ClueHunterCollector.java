package scripts.cluehuntercollector;

import javafx.application.Platform;
import org.tribot.api.Clicking;
import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.input.Mouse;
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
import scripts.api.interaction.Interactions;
import scripts.cluehuntercollector.data.Constants;
import scripts.cluehuntercollector.gui.GUI;
import scripts.cluehuntercollector.tasks.items.*;

import java.awt.*;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import static scripts.api.FluffeePaint.FluffeesPaint.PaintLocations.INVENTORY_AREA;
import static scripts.cluehuntercollector.data.Constants.AREA_GE;
import static scripts.cluehuntercollector.data.Constants.AREA_SPADE;
import static scripts.cluehuntercollector.data.Vars.hasCheckedBank;
import static scripts.cluehuntercollector.data.Vars.needsSpade;

@ScriptManifest(authors = {"adamhackz"}, category = "Tools", name = "ClueHunterCollector", description = "Collects the clue hunter outfit for wintertodt/fashionscape", version = 1)


public class ClueHunterCollector extends Script implements Painting, PaintInfo, MessageListening07, Arguments {

    public static final File GUIPATH = new File(Util.getWorkingDirectory() + File.separator + "adamhackz"  + File.separator, "scriptname_" + "settings.ini");
    public static ArrayList<String> tasks = new ArrayList<>();
    public final FluffeesPaint FLUFFEES_PAINT = new FluffeesPaint(this, INVENTORY_AREA, new Color[]{new Color(255, 251, 255)}, "Trebuchet MS", new Color[]{new Color(93, 156, 236, 127)},
            new Color[]{new Color(39, 95, 175)}, 1, false, 5, 3, 0);
    /*
    Gui stuff
     */
    private URL fxml;
    private GUI gui;

    private State scriptState;
    private String args;
    private boolean continueRunning = true;

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
            for (String arg : args.split(",")) {
                if (arg.contains("outfit")) {
                    tasks.add("cloak");
                    tasks.add("garb");
                    tasks.add("trousers");
                    tasks.add("glovesandboots");
                }
                if (arg.contains("helm")) {
                    tasks.add("helm");
                }
                if (arg.contains("trousers")){
                    tasks.add("trousers");
                }
            }
        } else if (hashMap.containsKey("autostart")) {
            args = hashMap.get("autostart");
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
/*
Loading settings
 */
        try {
            Platform.runLater(() -> {
                guiExample.loadSettings();
                guiExample.setVisible(true);
            });
        } catch (Throwable ignore) {
            General.println("Failed to load script settings.");
        }
        try {
            fxml = new URL("https://pastebin.com/raw/fk6Y0X1t");
            gui = new GUI(fxml);
            gui.show();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }


        while (gui.isOpen()) {
            sleep(150);
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
        General.println(scriptState);
        switch (scriptState) {
            case CHECK_BANK:
                          /*
                   Deposit all if we are at the bank
                     */
                if (Banking.isInBank() || AREA_GE.contains(Player.getPosition())) {
                    if (Banking.isBankScreenOpen()) {
                        /*
                        We need to make space for everything we will potentially need
                        which includes
                        A spade, 6 armour pieces, coins,
                        and if collecting the helm - Nature rune,Leather boots,Superantipoison(1)
                         */
                        Banking.depositAll();
                        Timing.waitCondition(() -> openInventorySpots() == 28, General.random(3000, 6000));

                        if (!hasItem("Spade")) {
                            if (bankHasItem("Spade")) {
                                if (Banking.withdraw(1, "Spade")) {
                                    Timing.waitCondition(() -> hasItem("Spade"), General.random(3000, 6000));
                                }
                            } else {
                                needsSpade = true;
                            }
                        }

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
                if (hasItem("Spade")){
                    needsSpade = false;
                }
                if (openInventorySpots() >= 2) {
                        Interactions.interactWithObject("Take", "Spade", AREA_SPADE);
                        /*
                    if (AREA_SPADE.contains(Player.getPosition())) {
                        RSObject[] obj = Objects.findNearest(20, Filters.Objects.inArea(AREA_SPADE).and(Filters.Objects.nameEquals("Spade").and(Filters.Objects.actionsContains("Take"))));
                        if (obj.length > 0 && obj[0] != null) {
                            if (obj[0].isOnScreen() && obj[0].isClickable()) {
                                if (PathFinding.canReach(obj[0].getPosition(), true)) {
                                    if (Clicking.click("Take", obj[0])) {
                                        Timing.waitCondition(() -> hasItem("Spade"), General.random(2300, 3100));
                                    }
                                } else {
                                    if (DaxWalker.walkTo(obj[0].getPosition())) {
                                        Timing.waitCondition(() -> !Player.isMoving(), General.random(2300, 3100));
                                    }
                                }
                            } else {
                                if (obj[0].adjustCameraTo()) {
                                    Timing.waitCondition(() -> !obj[0].isOnScreen() && !obj[0].isClickable(), General.random(2300, 3100));
                                }
                            }
                        }
                    } else {
                        if (DaxWalker.walkTo(AREA_SPADE.getRandomTile())) {
                            Timing.waitCondition(() -> !Player.isMoving(), General.random(600, 1100));
                        }
                    }

                         */
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
        return bank.length > 0 || banker.length > 0 ;
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


    public enum State {
        CHECK_BANK, COLLECT_OUTFIT, COLLECT_SPADE
    }


}




