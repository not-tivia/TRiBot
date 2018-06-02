package scripts.warriorGuildv2.Tree_Framework;

/**
 * @author Wastedbro
 */
public abstract class DecisionNode implements INode
{
    private INode onTrueNode = null,
            onFalseNode = null;

    private boolean lastValidationResult = false;
    private long lastValidationTime = -1,
                    evaluationDelay = -1;


    /**
     * This method sets the interval (in milliseconds) in which to evaluate this node.
     * For example, if this delay is set 5000,
     *  then this decision node will only call its "isValid" method once every 5 seconds and cache the value.
     */
    public void setEvaluationDelay(long delay)
    {
        evaluationDelay = delay;
    }

    public abstract boolean isValid();

    public void addOnTrueNode(INode node)
    {
        onTrueNode = node;
    }
    public void addOnFalseNode(INode node)
    {
        onFalseNode = node;
    }

    @Override
    public INode getValidNode()
    {
        boolean valid = lastValidationResult;
        long time = System.currentTimeMillis();

        if(evaluationDelay <= 0 || time - lastValidationTime > evaluationDelay)
        {
            valid = isValid();
            lastValidationResult = valid;
            lastValidationTime = time;
        }

        if(valid)
            return onTrueNode.getValidNode();
        else
            return onFalseNode.getValidNode();
    }

    /**
     * Decision nodes should have no status
     */
    @Override
    public String getStatus()
    {
        return "";
    }

    /**
     * Decision nodes should have no execution
     */
    @Override
    public void execute() {}
}
