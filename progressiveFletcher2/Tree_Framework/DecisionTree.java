package scripts.progressiveFletcher2.Tree_Framework;

/**
 * @author Wastedbro
 */
public class DecisionTree
{
    INode root;

    public DecisionTree(INode root)
    {
        this.root = root;
    }

    public INode getValidNode()
    {
        return root.getValidNode();
    }
}
