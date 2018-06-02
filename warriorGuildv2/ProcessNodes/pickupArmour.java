package scripts.warriorGuildv2.ProcessNodes;

import org.tribot.api.DynamicClicking;
import org.tribot.api.General;
import org.tribot.api.input.Mouse;
import org.tribot.api2007.Banking;
import org.tribot.api2007.Game;
import org.tribot.api2007.GroundItems;
import org.tribot.api2007.Inventory;
import org.tribot.api2007.types.RSGroundItem;
import org.tribot.api2007.types.RSItem;
import scripts.warriorGuildv2.Tree_Framework.ProcessNode;
import scripts.warriorGuildv2.webwalker_logic.WebWalker;
import scripts.warriorGuildv2.webwalker_logic.local.walker_engine.interaction_handling.AccurateMouse;

public class pickupArmour extends ProcessNode {


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

    private boolean pickupTokens(){
        RSGroundItem[] tokens = GroundItems.find("Warrior guild token");
        if (tokens.length > 0 && hasArmour()){
            DynamicClicking.clickRSGroundItem(tokens[0], 1);
            return true;

        }
        else return false;
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

    @Override
    public String getStatus()
    {
        return "Looting armour";
    }

    @Override
    public void execute()
    {
        General.sleep(General.randomSD(70, 210, 140, 70));

        pickupArmour();
        General.sleep(General.randomSD(70, 210, 140, 70));

        pickupTokens();




    }
}
