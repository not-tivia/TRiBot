package scripts.progressiveFletcher2.ProcessNodes;

import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.types.generic.Condition;
import org.tribot.api2007.Banking;
import scripts.progressiveFletcher2.Tree_Framework.ProcessNode;

public class openbank extends ProcessNode{
    @Override
    public String getStatus()
    {
        return "Opening bank";
    }

    @Override
    public void execute() {

        Banking.openBank();
        Timing.waitCondition( new Condition() {
            @Override
            public boolean active() {
                return Banking.isBankScreenOpen();
            }

        }, General.random( 1800, 3200 ) );
        General.println("Opening Bank");
    }

}
