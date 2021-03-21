package scripts.cluehuntercollector.tasks.items;


import org.tribot.api.Clicking;
import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.util.abc.ABCUtil;
import org.tribot.api2007.*;
import org.tribot.api2007.ext.Filters;
import org.tribot.api2007.types.RSItem;
import scripts.api.dax_api.api_lib.DaxWalker;

import static scripts.api.items.ItemCheck.hasItem;
import static scripts.cluehuntercollector.data.Constants.AREA_GARB;

public class Garb {

    private static final int TIMEOUT = General.random(5000, 10000);

    private boolean continueRunning = true;
    private State SCRIPT_STATE = getState();



    private boolean inArea() {
        return AREA_GARB.equals(Player.getPosition());
    }

    private State getState() {

            if (inArea()) {
                return State.DIG;
            } else {
                return State.WALK_TO_AREA;
            }





    }

    public void run() {
        while (continueRunning) {
            SCRIPT_STATE = getState();

            switch (SCRIPT_STATE) {



                case DIG:

                    if (hasItem("Clue hunter garb")) {
                        continueRunning = false;
                    } else {
                            RSItem[] spade = Inventory.find("Spade");
                            if (spade.length > 0 && spade[0] != null) {
                                if (spade[0].click("Dig")) {
                                    Timing.waitCondition(() -> hasItem("Clue hunter garb"), TIMEOUT);
                                }
                            }


                    }

                    break;

                case WALK_TO_AREA:
                    if (AREA_GARB.isOnScreen() && AREA_GARB.isClickable()) {
                        if (Walking.clickTileMS(AREA_GARB, "Walk here")) {
                            Timing.waitCondition(() -> !Player.isMoving(), General.random(2500, 4100));
                        }
                    } else {
                        if (!AREA_GARB.equals(Player.getPosition())) {
                            if (DaxWalker.walkTo(AREA_GARB)) {
                                Timing.waitCondition(() -> !Player.isMoving(), General.random(600, 1100));
                            }
                        }
                    }


                    break;


            }

        }

    }



    public enum State {
        WALK_TO_AREA, DIG,
    }






}
