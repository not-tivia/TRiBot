package scripts.progressiveFletcher2.ProcessNodes;

import org.tribot.api.General;
import org.tribot.api.input.Mouse;
import org.tribot.api.types.generic.Filter;
import org.tribot.api2007.Interfaces;
import org.tribot.api2007.Inventory;
import org.tribot.api2007.types.RSInterface;
import org.tribot.api2007.types.RSItem;
import scripts.progressiveFletcher2.Tree_Framework.ProcessNode;

import java.awt.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

public class useknifeonyew extends ProcessNode {
    String args;



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
        return "Using Knife on yew logs";
    }

    private boolean combine() {
        RSItem yews = getItemClosestToMouse( 1515 );

        RSItem[] logs = Inventory.find( "Yew logs" );
        RSItem[] knife = Inventory.find( "Knife" );
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
            if (args.equals("yew shortbow" )){
                RSInterface mshortbow = Interfaces.get(270, 15);
                if (mshortbow != null) {
                    mshortbow.click();
                    General.println("Clicking shortbow Interface");
                    General.sleep( 17,82 );
                }
            }
            if (args.equals( "yew longbow" )){
                RSInterface mlongbow = Interfaces.get(270, 16);
                if (mlongbow != null) {
                    General.println("Clicking longbow Interface");
                    mlongbow.click();
                }
            }

        }
    }

    private static Point getCenterPoint(RSItem i) {
        Rectangle r = i.getArea();
        if (r != null) {
            Point rPoint = r.getLocation();
            return new Point(rPoint.x + r.width / 2, rPoint.y + r.height / 2);
        }
        return null;
    }

    private static Comparator<RSItem> closestToFarthest = (o1, o2) -> {
        Point botMousePoint = new Point( Mouse.getPos());
        Point p1 = getCenterPoint(o1);
        Point p2 = getCenterPoint(o2);
        if (p1 != null && p2 != null) {
            return Integer.compare((int) botMousePoint.distance(p1), (int) botMousePoint.distance(p2)) > 0 ? 1 : -1;
        }
        return -1;
    };

    public static RSItem[] findNearestToMouse() {
        RSItem[] items = Inventory.getAll();
        Arrays.sort(items, closestToFarthest);
        return items;
    }

    public static RSItem[] findNearestToMouse(Filter<RSItem> filter) {
        RSItem[] items = Inventory.find(filter);
        Arrays.sort(items, closestToFarthest);
        return items;
    }

    public static RSItem[] findNearestToMouse(String... names) {
        RSItem[] items = Inventory.find(names);
        Arrays.sort(items, closestToFarthest);
        return items;
    }

    public static RSItem[] findNearestToMouse(int... ids) {
        RSItem[] items = Inventory.find(ids);
        Arrays.sort(items, closestToFarthest);
        return items;
    }



    public static RSItem getItemClosestToMouse(int... ids) {
        RSItem[] items = Inventory.find(ids);
        Point mouse_pos = Mouse.getPos();
        RSItem closest_item = null;
        double distance = 9999, temp_distance;
        for (RSItem item : items) {
            Rectangle rectangle = item.getArea();
            if (rectangle == null) {
                continue;
            }
            Point item_pos = rectangle.getLocation();
            item_pos.translate(20, 20);
            if ((temp_distance = item_pos.distance(mouse_pos)) < distance) {
                distance = temp_distance;
                closest_item = item;
            }
        }
        return closest_item;
    }

}
