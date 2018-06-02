package scripts.warriorGuildv2.ProcessNodes;

import org.tribot.api.DynamicClicking;
import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api.types.generic.Condition;
import org.tribot.api2007.*;
import org.tribot.api2007.types.RSItem;
import org.tribot.api2007.types.RSObject;
import scripts.warriorGuildv2.ACamera;
import scripts.warriorGuildv2.Tree_Framework.ProcessNode;

public class animateTime extends ProcessNode {


    @Override
    public String getStatus()
    {
        return "Animating Time";
    }

    @Override
    public void execute() {
        if (hasArmour()){
            animateArmour();
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

    private boolean animateArmour(){
        if (hasArmour()) {
            RSObject[] animate = Objects.find(20, 23955);
            if (animate.length > 0){
                General.sleep(General.randomSD(250, 1200, 725, 500));
                if (animate[0].isOnScreen()){
                    DynamicClicking.clickRSObject(animate[0], "Animate");


                    Timing.waitCondition( new Condition() {
                        @Override
                        public boolean active() {
                            General.sleep(General.randomSD(70, 210, 140, 70));
                            return (NPCs.find("Animated Mithril Armour").length > 0 );
                        }
                    }, General.random(1700, 3482));
                    General.sleep(General.randomSD(250, 1200, 725, 500));
                } else {
                    int choice = General.random( 1,2 );
                    if (choice == 1){
                        Camera.turnToTile(animate[0].getPosition());
                    }
                   if (choice == 2){
                       Walking.walkTo(animate[0].getPosition());
                   }
                }


            }return true;
        }
        else return false;
    }
}
