package scripts.progressiveFletcher2.ProcessNodes;

import org.tribot.api.General;
import org.tribot.api2007.Banking;
import scripts.progressiveFletcher2.Tree_Framework.ProcessNode;

public class depositallbutknife extends ProcessNode {

    @Override
    public String getStatus()
    {
        return "Depositing all but knife";
    }

    @Override
    public void execute() {

        Banking.depositAllExcept( "Knife" );
        General.println("Depositing all but knife");
    }

}
