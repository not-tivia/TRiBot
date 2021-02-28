package scripts.cluehuntercollector.data;

import org.tribot.api.Timing;
import org.tribot.api2007.types.RSArea;
import org.tribot.api2007.types.RSItem;
import org.tribot.api2007.types.RSTile;

public class Constants {

    public final static RSArea AREA_GE = new RSArea(new RSTile(3157, 3496, 0), new RSTile(3171, 3482, 0));

    public final static RSArea AREA_GLOVES_AND_BOOTS = new RSArea(new RSTile(2578, 3378, 0), new RSTile(2580, 3376, 0));

    public final static RSTile AREA_GARB = (new RSTile(1596, 3628, 0));

    public final static RSTile AREA_TROUSERS = (new RSTile(2820, 3127, 0));

    public final static RSArea AREA_CLOAK = new RSArea(new RSTile(2613, 3066, 0), new RSTile(2616, 3063, 0));

    public final static RSArea AREA_HELM = new RSArea(new RSTile(2593, 3229, 0), new RSTile(2589, 3233, 0));

    public final static RSArea AREA_SPADE = new RSArea(new RSTile(2976, 3242, 0), new RSTile(2982, 3237, 0));

    public final static long startTime = Timing.currentTimeMillis();

    public final static String foodTypes[] = "Trout, Lobster";




}
