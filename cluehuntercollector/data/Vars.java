package scripts.cluehuntercollector.data;

import java.util.ArrayList;


public class Vars {

    public static boolean needsSpade = false;
    public static boolean hasCheckedBank = false;


    public static ArrayList<String> tasks = new ArrayList<>();
    public static boolean collectingHelm = false;

    public static int requiredInventorySpace = 1;
    //We start with one because we need a spade no matter what

    public static int foodCount;

    public static boolean usingStamina = false;

    public static boolean usingFood = false;
    public static String foodName;

    public static boolean continueRunning = true;


}
