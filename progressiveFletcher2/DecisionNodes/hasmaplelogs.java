package scripts.progressiveFletcher2.DecisionNodes;

import org.tribot.api2007.Inventory;
import scripts.progressiveFletcher2.Tree_Framework.DecisionNode;

public class hasmaplelogs extends DecisionNode {

    @Override
    public boolean isValid() {
        return Inventory.find( "Maple logs" ).length > 0 && Inventory.find( "Maple logs" ) != null;
    }
}
