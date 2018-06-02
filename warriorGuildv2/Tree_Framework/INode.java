package scripts.warriorGuildv2.Tree_Framework;

/**
 * @author Wastedbro
 */
public interface INode
{
    INode getValidNode();

    String getStatus();

    void execute();
}
