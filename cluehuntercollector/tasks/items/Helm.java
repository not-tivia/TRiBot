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
import static scripts.cluehuntercollector.data.Constants.AREA_HELM;
import static scripts.cluehuntercollector.data.Constants.AREA_TROUSERS;

public class Helm {

    private boolean continueRunning = true;
    private State SCRIPT_STATE = getState();


    private boolean inArea() {
        return AREA_HELM.equals(Player.getPosition());
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
                    if (hasItem("Helm of raedwald")) {
                        continueRunning = false;
                    } else {
                                RSItem[] spade = Inventory.find("Spade");
                                if (spade.length > 0 && spade[0] != null) {
                                    if (spade[0].click("Dig")) {
                                        Timing.waitCondition(() -> hasItem("Helm of raedwald"), General.random(2300, 3100));
                                    }
                                }


                    }
                    break;


                case WALK_TO_AREA:
                    if (!AREA_HELM.equals(Player.getPosition())) {
                        if (AREA_HELM.isClickable() && AREA_HELM.isOnScreen()) {
                            Clicking.click("Walk here", AREA_HELM.getPosition());
                            General.sleep(1200, 3000);
                        } else {
                            if (DaxWalker.walkTo(AREA_HELM)) {
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
