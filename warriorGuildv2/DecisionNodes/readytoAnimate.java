package scripts.warriorGuildv2.DecisionNodes;

import org.tribot.api2007.Inventory;
import org.tribot.api2007.Player;
import org.tribot.api2007.types.RSItem;
import scripts.warriorGuildv2.Tree_Framework.DecisionNode;

public class readytoAnimate extends DecisionNode {

    private boolean hasarmour(){
        RSItem[] helm = Inventory.find("Mithril full helm" );
        RSItem[] body = Inventory.find("Mithril platebody" );
        RSItem[] legs = Inventory.find("Mithril platelegs" );
        if (body.length > 0  && body != null && helm.length > 0 && helm != null && legs.length > 0 && legs != null){
            return true;
        } else return false;
    }

    @Override
    public boolean isValid() {
        return hasarmour();
    }


}
