package scripts.cannonalcher.data;

import org.tribot.api.Timing;
import org.tribot.api2007.types.RSArea;
import org.tribot.api2007.types.RSTile;

public class Constants {

    public final static long startTime = Timing.currentTimeMillis();

    public static final String apiKey = "sub_DPjXXzL5DeSiPf";
    public static final String secretKey =  "public-KEY";

    public enum Locations {


        CASTLE_WARS("Castle wars ogres", new RSArea(
                new RSTile[] {
                        new RSTile(1657, 10089, 0),
                        new RSTile(1653, 10080, 0),
                        new RSTile(1673, 10081, 0),
                        new RSTile(1667, 10086, 0),
                        new RSTile(1667, 10094, 0),
                        new RSTile(1661, 10093, 0)
                })),
        COMBAT_TRAINING_AREA("Combat training area", new RSArea(new RSTile(2524, 3371, 0), new RSTile(2532, 3370, 0)));

        final String areaName;
        final RSArea areaLocation;

        static final Locations[] locations = {CASTLE_WARS, COMBAT_TRAINING_AREA};

        Locations(String areaName, RSArea areaLocation) {
            this.areaName = areaName;
            this.areaLocation = areaLocation;
        }

        public String getName() {
            return areaName;
        }

        public RSArea getArea() {
            return areaLocation;
        }

    }

}
