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
import static scripts.cluehuntercollector.data.Constants.AREA_TROUSERS;

public class Trousers {

    private static final int TIMEOUT = General.random(5000, 10000);
    private static long START_TIME = System.currentTimeMillis();
    private boolean continueRunning = true;
    // this is an instanced class you can use throughout the whole script.
    private State SCRIPT_STATE = getState();



    private boolean inArea() {
        return AREA_TROUSERS.equals(Player.getPosition());
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

                    if (hasItem("Clue hunter trousers")) {
                        continueRunning = false;
                    } else {
                        RSItem[] spade = Inventory.find("Spade");
                        if (spade.length > 0 && spade[0] != null) {
                            if (spade[0].click("Dig")) {
                                Timing.waitCondition(() -> hasItem("Clue hunter trousers"), General.random(2300, 3100));
                            }
                        }
                    }
                    break;


                case WALK_TO_AREA:
                    if (!AREA_TROUSERS.equals(Player.getPosition())) {
                        if (AREA_TROUSERS.isClickable() && AREA_TROUSERS.isOnScreen()) {
                            Clicking.click("Walk here", AREA_TROUSERS.getPosition());
                            General.sleep(1200, 3000);
                        } else {
                            if (DaxWalker.walkTo(AREA_TROUSERS)) {
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
