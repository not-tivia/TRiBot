package scripts.progressiveFletcher2.ProcessNodes;

import org.tribot.api.General;
import org.tribot.api2007.Interfaces;
import org.tribot.api2007.Inventory;
import org.tribot.api2007.Skills;
import org.tribot.api2007.types.RSInterface;
import org.tribot.api2007.types.RSItem;
import scripts.progressiveFletcher2.Tree_Framework.ProcessNode;

import java.util.HashMap;

public class useknifeonlogs extends ProcessNode {

    String args;

    int flevel = Skills.getActualLevel( Skills.SKILLS.FLETCHING);

    public void passArguments (HashMap< String, String > hashMap){
        if (hashMap.containsKey("custom_input")) {
            args = hashMap.get("custom_input");
        } else if (hashMap.containsKey("autostart")) {
            args = hashMap.get("autostart");
        }
    }
    @Override
    public String getStatus()
    {
        return "Using Knife on logs";
    }

    private boolean combine() {

        RSItem[] logs = Inventory.find("Logs");
        RSItem[] knife = Inventory.find("Knife");
        if (logs.length > 0 && logs != null && knife.length > 0 && knife != null){
            return knife[0].click() && logs[0].click();
        }
        return true;
    }

    @Override
    public void execute() {
        if (!Interfaces.isInterfaceSubstantiated( 270 )){
            combine();
            General.println("Opening interface");
        }
        if (Interfaces.isInterfaceSubstantiated( 270 )){

            if (flevel < 5) {
                RSInterface arrowshafts = Interfaces.get(270, 14);
                if (arrowshafts != null) {
                    arrowshafts.click();
                }
            }
            if (flevel >= 5 && flevel < 10){
                if (args.equals("progressive" )){
                    RSInterface mshortbow = Interfaces.get(270, 15);
                    if (mshortbow != null) {
                        mshortbow.click();
                        General.println("Clicking shortbow Interface");
                        General.sleep( 17,82 );
                    }
                }
            }

            if (flevel >= 10 && flevel < 20){
                if (args.equals( "progressive" )){
                    RSInterface mlongbow = Interfaces.get(270, 16);
                    if (mlongbow != null) {
                        General.println("Clicking longbow Interface");
                        mlongbow.click();
                    }
                }
            }


        }
    }
}
