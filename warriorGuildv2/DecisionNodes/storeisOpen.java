package scripts.warriorGuildv2.DecisionNodes;

import org.tribot.api.General;
import org.tribot.api2007.Interfaces;
import org.tribot.api2007.Player;
import scripts.warriorGuildv2.OSShopping;
import scripts.warriorGuildv2.Tree_Framework.DecisionNode;

public class storeisOpen extends DecisionNode {

    @Override
    public boolean isValid() {
        General.sleep(2000,5000);
        return OSShopping.isShopOpen();
    }
}
