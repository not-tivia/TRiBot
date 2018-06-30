package scripts.progressiveFletcher2.ProcessNodes;

import org.tribot.api.General;
import org.tribot.api2007.Inventory;
import org.tribot.api2007.ext.Filters;
import org.tribot.api2007.types.RSItem;
import scripts.progressiveFletcher2.Tree_Framework.ProcessNode;

public class stringcombine extends ProcessNode {
    @Override
    public String getStatus()
    {
        return "Combining bowstrings and bows";
    }

    @Override
    public void execute() {
        string();
        General.println("Combining bowstrings and bows");
    }

    private boolean string(){
        RSItem[] bows = Inventory.find( "Magic longbow(u)");
        RSItem[] bowstrings = Inventory.find("Bowstring");
        if (bows.length > 0 && bows != null && bowstrings.length > 0 && bowstrings != null){
            return bows[bows.length-1].click() && bowstrings[0].click();
        }
        return true;
    }

}
