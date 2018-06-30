package scripts.progressiveFletcher2.DecisionNodes;

import org.tribot.api2007.Inventory;
import org.tribot.api2007.ext.Filters;
import scripts.progressiveFletcher2.Tree_Framework.DecisionNode;

public class hasyewbows extends DecisionNode {
    @Override
    public boolean isValid() {
        return Inventory.find( Filters.Items.nameContains( "Yew" )).length > 0;
    }
}
