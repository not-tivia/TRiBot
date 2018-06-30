package scripts.progressiveFletcher2.DecisionNodes;

import org.tribot.api2007.Skills;
import scripts.progressiveFletcher2.Tree_Framework.DecisionNode;

public class levellessthanfive extends DecisionNode {

    @Override
    public boolean isValid() {
        return Skills.getActualLevel( Skills.SKILLS.FLETCHING) < 5;
    }
}
