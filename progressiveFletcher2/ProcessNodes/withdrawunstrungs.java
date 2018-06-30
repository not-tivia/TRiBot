package scripts.progressiveFletcher2.ProcessNodes;

import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.types.generic.Condition;
import org.tribot.api2007.Banking;
import org.tribot.api2007.Inventory;
import scripts.progressiveFletcher2.Tree_Framework.ProcessNode;

public class withdrawunstrungs extends ProcessNode {

    @Override
    public String getStatus()
    {
        return "Withdrawing magic longbow(u)";
    }

    @Override
    public void execute() {
        if (Banking.find( "Magic longbow(u)" ).length > 0 && Banking.find( "Magic longbow(u)" )!= null ){
            Banking.withdraw( 0, "Magic longbow(u)" );
            Timing.waitCondition( new Condition() {
                @Override
                public boolean active() {
                    return Inventory.find( "Magic longbow(u)").length > 0;
                }

            }, General.random( 1800, 3200 ) );
            Banking.close();
        }
        General.println("Withdrawing magic longbow unstrungs");
    }
}
