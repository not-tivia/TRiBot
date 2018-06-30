package scripts.progressiveFletcher2.DecisionNodes;

import org.tribot.api2007.Inventory;
import scripts.progressiveFletcher2.Tree_Framework.DecisionNode;

public class hasunstrungs extends DecisionNode {

    @Override
    public boolean isValid() {
        return Inventory.find( "Magic longbow(u)" ).length > 0 && Inventory.find( "Magic longbow(u)" ) != null;
    }
}
