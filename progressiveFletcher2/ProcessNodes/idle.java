package scripts.progressiveFletcher2.ProcessNodes;

import org.tribot.api.General;
import scripts.progressiveFletcher2.Tree_Framework.ProcessNode;

public class idle extends ProcessNode {

    @Override
    public String getStatus()
    {
        return "Script idling";
    }

    @Override
    public void execute() {
        General.println("Idle");
    }

}
