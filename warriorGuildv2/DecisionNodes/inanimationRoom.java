package scripts.warriorGuildv2.DecisionNodes;

import org.tribot.api2007.Player;
import org.tribot.api2007.types.RSArea;
import org.tribot.api2007.types.RSTile;
import scripts.warriorGuildv2.Tree_Framework.DecisionNode;

public class inanimationRoom extends DecisionNode {
    private RSArea animateRoom = new RSArea( new RSTile( 2849, 3545, 0 ), new RSTile( 2859, 3534, 0 ) );


    @Override
    public boolean isValid()
    {
        return animateRoom.contains( Player.getPosition().getPosition());
    }

}
