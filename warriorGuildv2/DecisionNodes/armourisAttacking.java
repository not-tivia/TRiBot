package scripts.warriorGuildv2.DecisionNodes;

import org.tribot.api2007.NPCs;
import org.tribot.api2007.Player;
import org.tribot.api2007.types.RSNPC;
import scripts.warriorGuildv2.Tree_Framework.DecisionNode;

public class armourisAttacking extends DecisionNode {
    RSNPC[] armour = NPCs.find( "Animated Mithril Armour" );

    @Override
    public boolean isValid() {

        return armourisAttacking();
    }

    private boolean armourisAttacking() {
        return armour.length > 0 && armour[0] != null && armour[0].isInteractingWithMe() && Player.getRSPlayer().isInCombat();
    }
}
