package scripts.cannonalcher.data;

import org.tribot.api.General;
import org.tribot.api2007.types.RSArea;

import org.tribot.api2007.types.RSTile;


public class Variables {



    public static String alchName;
    public static String cannonballName;

    public static boolean lootingEnabled = false;
    public static boolean alchingEnabled = false;
    public static boolean telegrabbingLoot = false;

    public static boolean cannonPlaced = false;

    public static boolean continueRunning = true;

    public static RSArea cannonArea = null;;
    public static RSTile cannonTile = null;

    public static boolean reloadEnabled = false;

    public static long LAST_LOAD = System.currentTimeMillis();
    public static int NEXT_LOAD = General.random(7000, 36000);




}
