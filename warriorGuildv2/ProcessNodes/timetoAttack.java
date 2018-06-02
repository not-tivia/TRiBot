package scripts.warriorGuildv2.ProcessNodes;

import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.types.generic.Condition;
import org.tribot.api2007.Banking;
import org.tribot.api2007.NPCs;
import org.tribot.api2007.Player;
import org.tribot.api2007.types.RSNPC;
import scripts.warriorGuildv2.aHelper;
import scripts.warriorGuildv2.Tree_Framework.ProcessNode;

public class timetoAttack extends ProcessNode {
    RSNPC[] armour  = NPCs.find( "Animated Mithril Armour" );

    @Override
    public String getStatus()
    {
        return "Attacking armour";
    }

    @Override
    public void execute() {
        if (armour.length > 0 && armour[0] != null && armour[0].isInteractingWithMe()){
            if (!Player.getRSPlayer().isInCombat()){
                Timing.waitCondition( new Condition() {
                    @Override
                    public boolean active() {
                        return (aHelper.attackTarget(armour[0], false));
                    }
                }, General.random( 1100, 3210 ));
            }
        }
    }


}
