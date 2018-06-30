package scripts.progressiveFletcher2.ProcessNodes;

import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.types.generic.Condition;
import org.tribot.api2007.Banking;
import org.tribot.api2007.Inventory;
import scripts.progressiveFletcher2.Tree_Framework.ProcessNode;

public class withdrawbowstrings extends ProcessNode {

    @Override
    public String getStatus()
    {
        return "Withdrawing bowstrings";
    }

    @Override
    public void execute() {
        if (Banking.find( "Bowstring" ).length > 0 && Banking.find( "Bowstring" )!= null ){
            Banking.withdraw( 14, "Bowstring" );
            Timing.waitCondition( new Condition() {
                @Override
                public boolean active() {
                    return Inventory.find( "Bowstring").length > 0;
                }

            }, General.random( 1800, 3200 ) );

        }
        if (Inventory.isFull()){
            Banking.close();
        }
        General.println("Withdrawing Bowstrings ");
    }
}
