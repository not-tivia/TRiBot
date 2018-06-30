package scripts.progressiveFletcher2.ProcessNodes;

import org.tribot.api.General;
import org.tribot.api2007.Banking;
import scripts.progressiveFletcher2.Tree_Framework.ProcessNode;

public class closebank extends ProcessNode {
    @Override
    public String getStatus()
    {
        return "Close bank";
    }

    @Override
    public void execute() {
        Banking.close();
        General.println("Close bank");
    }

}
