package scripts.progressiveFletcher2.DecisionNodes;

import org.tribot.api2007.Inventory;
import scripts.progressiveFletcher2.Tree_Framework.DecisionNode;

public class hasoaklogs extends DecisionNode {

    @Override
    public boolean isValid() {
        return Inventory.find( "Oak logs" ).length > 0 && Inventory.find( "Oak logs" ) != null;
    }
}
