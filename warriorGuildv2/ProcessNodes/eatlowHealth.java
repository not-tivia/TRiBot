package scripts.warriorGuildv2.ProcessNodes;

import org.tribot.api.General;
import org.tribot.api.input.Mouse;
import org.tribot.api.util.abc.ABCUtil;
import org.tribot.api2007.*;
import org.tribot.api2007.ext.Filters;
import org.tribot.api2007.types.RSGroundItem;
import org.tribot.api2007.types.RSItem;
import scripts.warriorGuildv2.Tree_Framework.ProcessNode;
import scripts.warriorGuildv2.webwalker_logic.local.walker_engine.interaction_handling.AccurateMouse;

public class eatlowHealth extends ProcessNode {
    int eat_at = this.abc.generateEatAtHP();
    private static ABCUtil abc = new ABCUtil();
    private boolean doeat = false;
    @Override
    public String getStatus()
    {
        return "Eating";
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

    @Override
    public void execute()

    {

        pickupArmour();
        // Do bank stuff
        int healing = 0;
        int maxhp = Skills.getActualLevel(Skills.SKILLS.HITPOINTS) - General.random(0, 4);
        doeat = false;
        int currenthp = Skills.getCurrentLevel(Skills.SKILLS.HITPOINTS);
        int newhp = (healing + currenthp);

        int newhp7 = (maxhp - currenthp);
        int newhp8 = newhp7 - healing - General.random(0, 5);

        RSItem[] food = Inventory.find( Filters.Items.actionsContains("Eat"));
        if (food.length > 0 && food != null) {
            //General.println("Food was found");
            if (food[0].getDefinition().getName().equals("Shrimp")) {
                healing = 3;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Cooked chicken")) {
                healing = 3;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Cooked meat")) {
                healing = 3;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Sardine")) {
                healing = 4;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Bread")) {
                healing = 5;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Herring")) {
                healing = 20;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Trout")) {
                healing = 7;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Tuna")) {
                healing = 10;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().contains("cake")) {
                healing = 4;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints per a slice.");
            }
            if (food[0].getDefinition().getName().equals("Lobster")) {
                healing = 12;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Bass")) {
                healing = 13;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Swordfish")) {
                healing = 14;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Potato with butter")) {
                healing = 14;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Potato with cheese")) {
                healing = 16;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Monkfish")) {
                healing = 16;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Cooked karambwan")) {
                healing = 18;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Ugthanki kebab")) {
                healing = 19;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Sea turtle")) {
                healing = 21;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Shark")) {
                healing = 20;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().contains("Pineapple pizza")) {
                healing = 11;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints per a slice.");
            }
            if (food[0].getDefinition().getName().equals("Manta ray")) {
                healing = 22;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Tuna potato")) {
                healing = 22;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (food[0].getDefinition().getName().equals("Anglerfish")) {
                healing = 22;
                General.println(food[0].getDefinition().getName() + " was found and heals for " + healing + " hitpoints.");
            }
            if (newhp7 > 0) {
                if (newhp7 <= maxhp) {
                    General.println("you need to heal " + newhp7 + " to be at max health");
                    if (healing <= newhp8) {
                        General.println("You will heal for " + newhp8);
                        General.sleep(General.randomSD(156, 463, 206, 92));
                        General.println("You will eat at " + this.abc.generateEatAtHP());
                        if (currenthp <= this.abc.generateEatAtHP()) {
                            if (GameTab.getOpen() != GameTab.TABS.INVENTORY) {
                                GameTab.open(GameTab.TABS.INVENTORY);
                            }
                            food[0].click("Eat");
                            General.sleep(General.randomSD(156, 463, 206, 92));
                            General.println("Ate food..");
                            for (int i = 0; i < 10 && Player.getAnimation() == 829; i++) {
                                General.sleep(90, 110);
                                this.eat_at = this.abc.generateEatAtHP();
                            }
                        }
                    }
                }
            }
        }

    }

}
