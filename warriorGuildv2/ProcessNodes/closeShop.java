package scripts.warriorGuildv2.ProcessNodes;

import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.types.generic.Condition;
import org.tribot.api2007.Banking;
import org.tribot.api2007.Interfaces;
import org.tribot.api2007.Inventory;
import org.tribot.api2007.Player;
import org.tribot.api2007.ext.Filters;
import org.tribot.api2007.types.RSItem;

import scripts.warriorGuildv2.OSShopping;
import scripts.warriorGuildv2.Tree_Framework.ProcessNode;

public class closeShop extends ProcessNode {
    private RSItem[] FOOD_IN_INVENTORY = Inventory.find( Filters.Items.actionsContains( "Eat" ) );


    @Override
    public String getStatus()
    {
        return "Close shop";
    }

    @Override
    public void execute() {
        if (OSShopping.isShopOpen()) {
            Timing.waitCondition( new Condition() {
                @Override
                public boolean active() {
                    return OSShopping.isShopOpen();
                }
            }, General.random( 1000, 3200) );
            OSShopping.close();
        }
    }

}
