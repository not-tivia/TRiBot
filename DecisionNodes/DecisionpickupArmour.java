package scripts.warriorGuildv2.DecisionNodes;

import org.tribot.api2007.GroundItems;
import org.tribot.api2007.types.RSGroundItem;
import scripts.warriorGuildv2.Tree_Framework.DecisionNode;

public class DecisionpickupArmour extends DecisionNode {


        private boolean pickupArmour(){
            RSGroundItem[] armourset = GroundItems.find("Mithril platebody", "Mithril platelegs", "Mithril full helm");
            if (armourset.length > 0){
                return true;
            }
            else return false;
        }
        @Override
        public boolean isValid() {
            return pickupArmour();
        }
    }



