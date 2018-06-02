package scripts.warriorGuildv2.ProcessNodes;

import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.types.generic.Condition;
import org.tribot.api2007.Banking;
import org.tribot.api2007.types.RSItem;
import scripts.warriorGuildv2.Tree_Framework.ProcessNode;
import scripts.warriorGuildv2.webwalker_logic.WebWalker;

public class withdrawCoins extends ProcessNode {

    @Override
    public String getStatus()
    {
        return "Banking";
    }

    @Override
    public void execute()
    {
        if (!Banking.isBankScreenOpen()){
            WebWalker.walkToBank();
        }
        if (Banking.isBankScreenOpen()){
            RSItem[] bankcoins = Banking.find( "Coins" );
            if (bankcoins.length > 0 && bankcoins != null) {
                if (bankcoins[0].getStack() > 10000) {
                    int coinso = General.random( 1500, 10000 );
                    Banking.withdraw( coinso, "Coins" );
                    General.sleep( 1200, 5000 );
                    Banking.close();
                }
            }
        }



    }

}
