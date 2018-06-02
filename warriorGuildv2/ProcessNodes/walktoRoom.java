package scripts.warriorGuildv2.ProcessNodes;

import org.tribot.api.Clicking;
import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.types.generic.Condition;
import org.tribot.api2007.Banking;
import org.tribot.api2007.Objects;
import org.tribot.api2007.Player;
import org.tribot.api2007.types.RSArea;
import org.tribot.api2007.types.RSItem;
import org.tribot.api2007.types.RSObject;
import org.tribot.api2007.types.RSTile;
import scripts.warriorGuildv2.Tree_Framework.ProcessNode;
import scripts.warriorGuildv2.webwalker_logic.WebWalker;

public class walktoRoom extends ProcessNode {

    private RSArea animateRoom = new RSArea( new RSTile( 2849, 3545, 0 ), new RSTile( 2859, 3534, 0 ) );


    @Override
    public String getStatus()
    {
        return "Walking to room";
    }

    @Override
    public void execute()
    {
        RSObject[] door = Objects.find(50, 24306);

        if (!animateRoom.contains( Player.getRSPlayer().getPosition())){
           if (door.length > 0 && door[0] != null && door[0].isOnScreen() && door[0].isClickable()){
               clickanimateRoom();
           }
           if (door.length > 0 && door[0] != null && !door[0].isOnScreen()){
               WebWalker.walkTo(door[0].getPosition());
           }
       }



    }

    private void clickanimateRoom(){
        RSObject[] door = Objects.find(50, 24306);
        Clicking.click("Open door", door[0]);
        Timing.waitCondition( new Condition() {
            @Override
            public boolean active() {
                return animateRoom.contains(Player.getRSPlayer().getPosition());
            }
        }, General.random( 2300, 5410 ) );
    }

}
