package scripts.warriorGuildv2.ProcessNodes;

import org.tribot.api.DynamicClicking;
import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.input.Mouse;
import org.tribot.api.types.generic.Condition;
import org.tribot.api2007.*;
import org.tribot.api2007.types.*;
import scripts.warriorGuildv2.ShiftDrop;
import scripts.warriorGuildv2.Tree_Framework.ProcessNode;
import scripts.warriorGuildv2.webwalker_logic.WebWalker;
import scripts.warriorGuildv2.webwalker_logic.local.walker_engine.interaction_handling.AccurateMouse;

public class gotoStore extends ProcessNode {

    private RSArea areafood = new RSArea( new RSTile( 2839, 3548, 0 ), new RSTile( 2843, 3555, 0 ) );

    @Override
    public String getStatus()
    {
        return "going to store";
    }

    @Override
    public void execute() {
        if (!areafood.contains( Player.getRSPlayer().getPosition()) && hasArmour()){
            WebWalker.walkTo( areafood.getRandomTile());
            General.sleep( 1200,5000 );
        }
        if (!hasArmour()){
            pickupArmour();
        }
        if (hasArmour()){
            if (!areafood.contains( Player.getRSPlayer().getPosition())) {
                WebWalker.walkTo( areafood.getRandomTile() );
                General.sleep( 1200, 2311 );
                ShiftDrop.shiftDrop(1923);
            }
        }

    }

    private boolean hasArmour(){
        RSItem[] armour1 = Inventory.find("Mithril platebody");
        RSItem[] armour2 = Inventory.find("Mithril platelegs");
        RSItem[] armour3 = Inventory.find( "Mithril full helm");
        if (armour1.length > 0 && armour2.length >0 && armour3.length>0) {
            return true;
        }
        else return false;
    }

    private boolean pickupArmour(){
        RSGroundItem[] armourset = GroundItems.find("Mithril platebody", "Mithril platelegs", "Mithril full helm");
        if (armourset.length > 0){
            General.sleep(General.randomSD(70, 210, 140, 70));
            if (!Game.getUptext().contains("Mithril")){
                AccurateMouse.click(armourset[0], "Take");
                //DynamicClicking.clickRSGroundItem(armourset[0], 1);
                General.sleep(General.randomSD(70, 210, 140, 70));
            }

            if (Game.getUptext().contains("Mithril")){
                //General.sleep(General.randomSD(70, 210, 140, 70));
                // Mouse.click(1);
                General.sleep(General.randomSD(70, 210, 140, 70));
                Mouse.click(1);
                General.sleep(General.randomSD(70, 210, 140, 70));
            }
            return true;

        }
        else return false;
    }

}
