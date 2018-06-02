package scripts.warriorGuildv2.DecisionNodes;

import jdk.nashorn.internal.runtime.Timing;
import org.tribot.api.General;
import org.tribot.api.types.generic.Condition;
import org.tribot.api2007.GroundItems;
import org.tribot.api2007.Inventory;
import org.tribot.api2007.NPCs;
import org.tribot.api2007.Player;
import org.tribot.api2007.types.RSGroundItem;
import org.tribot.api2007.types.RSItem;
import scripts.warriorGuildv2.Tree_Framework.DecisionNode;

public class isAnimated extends DecisionNode {



    private boolean hasarmour(){
        RSItem[] helm = Inventory.find("Mithril full helm" );
        RSItem[] body = Inventory.find("Mithril platebody" );
        RSItem[] legs = Inventory.find("Mithril platelegs" );
        return body.length > 0  && body != null && helm.length > 0 && helm != null && legs.length > 0 && legs != null;
    }

private boolean animated(){

    return NPCs.find( "Animated Mithril Armour" ).length > 0;

}


    @Override
    public boolean isValid() {
    General.sleep( 2500, 4200 );
       return animated();
    }
}
