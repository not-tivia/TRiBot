package scripts.progressiveFletcher2.ProcessNodes;

import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.types.generic.Condition;
import org.tribot.api2007.Banking;
import org.tribot.api2007.Inventory;
import scripts.progressiveFletcher2.Tree_Framework.ProcessNode;

public class withdrawlogs extends ProcessNode {

    @Override
    public String getStatus()
    {
        return "Withdrawing logs";
    }

    @Override
    public void execute() {
        if (Banking.find( "Logs" ).length > 0 && Banking.find( "Logs" )!= null ){
            Banking.withdraw( 0, "Logs" );
            Timing.waitCondition( new Condition() {
                @Override
                public boolean active() {
                    return Inventory.find( "Logs").length > 0;
                }

            }, General.random( 1800, 3200 ) );
            Banking.close();
        }


        General.println("Withdrawing logs");
    }
}
