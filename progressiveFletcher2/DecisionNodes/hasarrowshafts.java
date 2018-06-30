package scripts.progressiveFletcher2.DecisionNodes;

import org.tribot.api2007.Inventory;
import scripts.progressiveFletcher2.Tree_Framework.DecisionNode;

public class hasarrowshafts extends DecisionNode {

    @Override
    public boolean isValid() {
        return Inventory.find( "Arrow shafts" ).length > 0;
    }
}
