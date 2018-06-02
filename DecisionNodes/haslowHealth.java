package scripts.warriorGuildv2.DecisionNodes;

import org.tribot.api.General;
import org.tribot.api.util.abc.ABCUtil;
import org.tribot.api2007.Skills;
import scripts.warriorGuildv2.Tree_Framework.DecisionNode;

public class haslowHealth extends DecisionNode {
    int eat_at = this.abc.generateEatAtHP();
    private static ABCUtil abc = new ABCUtil();


    @Override
    public boolean isValid() {
        int currenthp = Skills.getCurrentLevel( Skills.SKILLS.HITPOINTS );
        return currenthp <= this.eat_at;
    }

}
