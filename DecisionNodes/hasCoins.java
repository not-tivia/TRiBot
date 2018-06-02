package scripts.warriorGuildv2.DecisionNodes;

import org.tribot.api2007.Inventory;
import org.tribot.api2007.types.RSItem;
import scripts.warriorGuildv2.Tree_Framework.DecisionNode;

public class hasCoins extends DecisionNode {

    RSItem[] coins = Inventory.find( "Coins" );

    @Override
    public boolean isValid() {
        return coins.length > 0 && coins != null && coins[0].getStack() > 1000;
    }
}
