package scripts.progressiveFletcher2.DecisionNodes;

import org.tribot.api2007.Banking;
import scripts.progressiveFletcher2.Tree_Framework.DecisionNode;

public class isbankopen extends DecisionNode {

    @Override
    public boolean isValid() {
        return Banking.isBankScreenOpen();
    }
}
