package scripts.progressiveFletcher2.Tree_Framework;

/**
 * @author Wastedbro
 */
public class GenericProcessNode extends ProcessNode
{
    private String status = "";
    private IAction action;


    public GenericProcessNode(IAction action)
    {
        this.action = action;
    }
    public GenericProcessNode(IAction action, String status)
    {
        this.action = action;
        this.status = status;
    }

    @Override
    public String getStatus()
    {
        return status;
    }

    @Override
    public void execute()
    {
        action.execute();
    }
}
