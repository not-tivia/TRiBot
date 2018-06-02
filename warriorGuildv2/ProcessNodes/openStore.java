package scripts.warriorGuildv2.ProcessNodes;

import org.tribot.api.DynamicClicking;
import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.types.generic.Condition;
import org.tribot.api2007.Interfaces;
import org.tribot.api2007.NPCs;
import org.tribot.api2007.Player;
import org.tribot.api2007.types.RSArea;
import org.tribot.api2007.types.RSNPC;
import org.tribot.api2007.types.RSTile;
import scripts.warriorGuildv2.Tree_Framework.ProcessNode;

public class openStore extends ProcessNode {

    private RSArea areafood = new RSArea( new RSTile( 2839, 3548, 0 ), new RSTile( 2843, 3555, 0 ) );

    @Override
    public String getStatus()
    {
        return "Open the store";
    }

    @Override
    public void execute() {

        if (areafood.contains( Player.getRSPlayer().getPosition() )){
            openShop();
        }
    }



    private void openShop() {
        RSNPC[] lidio = NPCs.find( "Lidio" );
        if (lidio.length > 0 && lidio != null) {
            DynamicClicking.clickRSNPC( lidio[0], "Trade" );
            Timing.waitCondition( new Condition() {
                @Override
                public boolean active() {
                    General.sleep( General.randomSD( 70, 210, 140, 70 ) );
                    return Interfaces.isInterfaceSubstantiated( 300 );
                }
            }, General.random( 1000, 1200 ) );
        }
    }
}
