package scripts.progressiveFletcher2.DecisionNodes;

import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.types.generic.Condition;
import org.tribot.api2007.Player;
import scripts.progressiveFletcher2.Tree_Framework.DecisionNode;

public class isanimating extends DecisionNode {

    private boolean currentlyPerformingAction = false;

    public static boolean continuouslyAnimating() {
        return Timing.waitCondition( new Condition() {
            @Override
            public boolean active() {
                return Player.getAnimation() != -1;
            }
        }, General.random(2000, 3000));

    }

    private boolean isanimating() {
        if (continuouslyAnimating()){
            return currentlyPerformingAction = true;
        } else
            return currentlyPerformingAction = false;
    }

    @Override
    public boolean isValid() {
        return isanimating();
    }
}
