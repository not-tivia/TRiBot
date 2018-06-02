package scripts.warriorGuildv2.ProcessNodes;

import org.tribot.api.General;
import org.tribot.api2007.Inventory;
import org.tribot.api2007.ext.Filters;
import org.tribot.api2007.types.RSArea;
import org.tribot.api2007.types.RSItem;
import org.tribot.api2007.types.RSTile;
import scripts.warriorGuildv2.OSShopping;
import scripts.warriorGuildv2.Tree_Framework.ProcessNode;

public class buysFood extends ProcessNode {
    private RSArea areafood = new RSArea( new RSTile( 2839, 3548, 0 ), new RSTile( 2843, 3555, 0 ) );
    private RSItem[] FOOD_IN_INVENTORY = Inventory.find( Filters.Items.actionsContains( "Eat" ) );
    @Override
    public String getStatus()
    {
        return "Buying food from the store";
    }

    @Override
    public void execute() {
        if (OSShopping.isShopOpen()) {
            //General.println( "0" );
            if (OSShopping.inStock( 300, 16, 5, 2003 ) && !Inventory.isFull()) {
                int buy = General.random( 1, 2 );
                if (buy > 1) {
                    OSShopping.buy( 300, 16, 5, 0, "Buy 50" );
                    General.sleep( 243, 1100 );
                } else {
                    OSShopping.buy( 300, 16, 5, 0, "Buy 10" );
                    General.sleep( 243, 1100 );
                }

            }
            if (OSShopping.inStock( 300, 16, 4, 6705 ) && !Inventory.isFull()) {
                int buy = General.random( 1, 2 );
                if (buy > 1) {
                    OSShopping.buy( 300, 16, 4, 0, "Buy 50" );
                    General.sleep( 243, 1100 );
                } else {
                    OSShopping.buy( 300, 16, 4, 0, "Buy 10" );
                    General.sleep( 243, 1100 );
                }

            }
            if (OSShopping.inStock( 300, 16, 3, 2289 ) && !Inventory.isFull()) {
                int buy = General.random( 1, 2 );
                if (buy > 1) {
                    OSShopping.buy( 300, 16, 3, 0, "Buy 50" );
                    General.sleep( 243, 1100 );
                } else {
                    OSShopping.buy( 300, 16, 3, 0, "Buy 10" );
                    General.sleep( 243, 1100 );
                }
            }
            if (OSShopping.inStock( 300, 16, 1, 333 ) && !Inventory.isFull()) {
                General.println( "2" );
                int buy = General.random( 1, 2 );
                if (buy > 1) {
                    OSShopping.buy( 300, 16, 1, 1, "Buy 50" );
                    General.sleep( 243, 1100 );
                } else {
                    OSShopping.buy( 300, 16, 1, 1, "Buy 10" );
                    General.sleep( 243, 1100 );
                }
            }
            General.sleep( 243, 1100 );
            if (FOOD_IN_INVENTORY.length > 0) {
                OSShopping.close();

            }
        }
    }
}
