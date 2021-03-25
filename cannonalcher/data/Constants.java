package scripts.cannonalcher.data;

import org.tribot.api.Timing;
import org.tribot.api2007.types.RSArea;
import org.tribot.api2007.types.RSTile;

public class Constants {

    public final static long startTime = Timing.currentTimeMillis();

    public static final String apiKey = "sub_DPjXXzL5DeSiPf";
    public static final String secretKey =  "public-KEY";

    public enum Locations {


        CASTLE_WARS("Castle wars ogres", new RSArea(new RSTile(2494, 3099, 0), new RSTile(2492, 3097, 0)), new RSTile(2493,3098)),
        COMBAT_TRAINING_AREA("Combat training area", new RSArea(new RSTile(2529, 3370, 0), new RSTile(2527, 3372, 0)), new RSTile(2528,3371,0));

        final String areaName;
        final RSArea areaLocation;
        final RSTile middleTile;

        static final Locations[] locations = {CASTLE_WARS, COMBAT_TRAINING_AREA};

        Locations(String areaName, RSArea areaLocation, RSTile middleTile) {
            this.areaName = areaName;
            this.areaLocation = areaLocation;
            this.middleTile = middleTile;
        }

        public String getName() {
            return areaName;
        }

        public RSArea getArea() {
            return areaLocation;
        }

        public RSTile getMiddleTile() { return middleTile; }

    }

}
