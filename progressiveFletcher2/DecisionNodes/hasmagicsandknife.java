package scripts.progressiveFletcher2.DecisionNodes;

import org.tribot.api2007.Inventory;
import scripts.progressiveFletcher2.Tree_Framework.DecisionNode;

public class hasmagicsandknife extends DecisionNode {
    @Override
    public boolean isValid() {
        return Inventory.find("Knife").length > 0 && Inventory.find("Knife")!= null && Inventory.find( "Magic logs" ).length > 0 && Inventory.find( "Magic logs" ) != null;
    }
}
