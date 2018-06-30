package scripts.progressiveFletcher2.ProcessNodes;

import org.tribot.api.General;
import org.tribot.api2007.Interfaces;
import org.tribot.api2007.Inventory;
import org.tribot.api2007.Skills;
import org.tribot.api2007.types.RSInterface;
import org.tribot.api2007.types.RSItem;
import scripts.progressiveFletcher2.Tree_Framework.ProcessNode;

public class useknifeonoaks extends ProcessNode {
    int flevel = Skills.getActualLevel( Skills.SKILLS.FLETCHING);


    @Override
    public String getStatus()
    {
        return "Using Knife on oak logs";
    }

    private boolean combine() {

        RSItem[] logs = Inventory.find("Oak logs");
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
            if (flevel >= 20 && flevel < 25) {
                RSInterface mshortbow = Interfaces.get( 270, 15 );
                if (mshortbow != null) {
                    mshortbow.click();
                    General.println( "Clicking shortbow Interface" );
                    General.sleep( 17, 82 );
                }
            }
            if (flevel >= 25 && flevel < 35) {
                RSInterface mlongbow = Interfaces.get( 270, 16 );
                if (mlongbow != null) {
                    General.println( "Clicking longbow Interface" );
                    mlongbow.click();
                }
            }

        }
    }
}
