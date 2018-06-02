package scripts.warriorGuildv2.DecisionNodes;

import org.tribot.api2007.Inventory;
import org.tribot.api2007.ext.Filters;
import org.tribot.api2007.types.RSItem;
import scripts.warriorGuildv2.Tree_Framework.DecisionNode;

public class hasFood extends DecisionNode {

    private RSItem[] FOOD_IN_INVENTORY = Inventory.find( Filters.Items.actionsContains( "Eat" ) );
    @Override
    public boolean isValid() {
        return (FOOD_IN_INVENTORY.length > 0) && (FOOD_IN_INVENTORY != null);

    }
}
