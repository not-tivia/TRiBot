package scripts.progressiveFletcher2.DecisionNodes;

import org.tribot.api2007.Skills;
import scripts.progressiveFletcher2.Tree_Framework.DecisionNode;

public class lessthanlevel85 extends DecisionNode {

    @Override
    public boolean isValid() {
        return Skills.SKILLS.FLETCHING.getActualLevel() >= 65 && Skills.SKILLS.FLETCHING.getActualLevel() < 85;

    }
}
