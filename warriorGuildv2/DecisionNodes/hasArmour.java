package scripts.warriorGuildv2.DecisionNodes;

import org.tribot.api2007.GroundItems;
import org.tribot.api2007.Inventory;
import org.tribot.api2007.types.RSGroundItem;
import org.tribot.api2007.types.RSItem;
import scripts.warriorGuildv2.Tree_Framework.DecisionNode;

public class hasArmour extends DecisionNode {

    @Override
    public boolean isValid()
    {
        RSItem[] helm = Inventory.find("Mithril full helm" );
        RSItem[] body = Inventory.find("Mithril platebody" );
        RSItem[] legs = Inventory.find("Mithril platelegs" );
        RSGroundItem[] tokens = GroundItems.find("Warrior guild token");
        return body.length > 0  && body != null && helm.length > 0 && helm != null && legs.length > 0 && legs != null && tokens.length < 1;
    }
}
