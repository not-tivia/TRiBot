package scripts.warriorsGuild;

import org.tribot.api.General;
import org.tribot.api.interfaces.Positionable;
import org.tribot.api2007.Camera;
import org.tribot.api2007.Camera.ROTATION_METHOD;
import org.tribot.api2007.Player;
import org.tribot.script.Script;

/**
 *
 * @author Final Calibur & WastedBro (no specific order)
 *
 */
public class ACamera
{
    //Local constants
    private final int ROTATION_THRESHOLD = 30; //We will not change the rotation when turning to a tile if the current rotation is +- this amount from the optimal value
    private final int ANGLE_THRESHOLD = 15; //Same as above, but for angle

    //Private instance fields
    private RotationThread rotationThread; //The thread that will handle camera rotation
    private AngleThread angleThread; //The thread that will handle camera angle
    private Script script; //The script we are handling the camera for. This is so we can know when to shut off the threads.
    private boolean runsWithoutScript;

    /*
     * Normal, widely used constructor
     * If you use this constructor, the threads
     * will terminate automatically when you end the script
     * associated with this object.
     */
    public ACamera(Script s)
    {
        instantiateVars(s);
    }

    /*
     * Default constructor.
     *
     * Threads will keep running even after you end the script.
     * This can be useful for "background" scripts such as a bot farm manager
     * for example.
     */
    public ACamera()
    {
        runsWithoutScript = true;

        instantiateVars(null);
    }

    /*
     *	This method instantiates all of our variables for us.
     *	I decided to make this method because it makes instantiating the variables in the constructors less redundant.
     */
    private void instantiateVars(Script s)
    {
        script = s;
        rotationThread = new RotationThread();
        rotationThread.setName("ACamera Rotation Thread");
        angleThread = new AngleThread();
        angleThread.setName("ACamera Angle Thread");
        Camera.setRotationMethod(ROTATION_METHOD.ONLY_KEYS); //DON'T CHANGE THIS
    }

    public void setCameraAngle(int angle)
    {
        synchronized(angleThread)
        {
            if(!angleThread.isAlive())
                angleThread.start();

            angleThread.sleepTime = calculateSleepTime();
            angleThread.angle = angle;
            angleThread.notify();
        }
    }

    public void setCameraRotation(int rotation)
    {
        synchronized(rotationThread)
        {
            if(!rotationThread.isAlive())
                rotationThread.start();

            rotationThread.rotation = rotation;
            rotationThread.notify();
        }
    }

    public void turnToTile(Positionable tile)
    {
        int optimalAngle = adjustAngleToTile(tile);
        int optimalRotation = Camera.getTileAngle(tile);

        if(Math.abs(optimalAngle - Camera.getCameraAngle()) > ANGLE_THRESHOLD)
            setCameraAngle(optimalAngle + General.random(-12, 12));

        if(Math.abs(optimalRotation - Camera.getCameraRotation()) > ROTATION_THRESHOLD)
            setCameraRotation(optimalRotation + General.random(-30, 30));
    }

    private long calculateSleepTime()
    {
        int diff = Math.abs(Camera.getCameraRotation() - rotationThread.rotation);
        int minTime = (int)(diff * 2.0);
        int maxTime = (int)(diff * 3.0);

        return (General.random(minTime, maxTime));
    }


    public int adjustAngleToTile(Positionable tile)
    {
        //Distance from player to object - Used in calculating the optimal angle.
        //Objects that are farther away require the camera to be turned to a lower angle to increase viewing distance.
        int distance = Player.getPosition().distanceTo(tile);

        //The angle is calculated by taking the max height (100, optimal for very close objects),
        //and subtracting an arbitrary number (I chose 6 degrees) for every tile that it is away.
        int angle = 100 - (distance * 6);

        return angle;
    }

    private class RotationThread extends Thread
    {
        protected int rotation = Camera.getCameraRotation();

        @Override
        public synchronized void run()
        {
            try
            {
                while((script != null && script.isActive()) || runsWithoutScript)
                {
                    Camera.setCameraRotation(rotation);

                    wait();
                }
            }
            catch(Exception e)
            {
                e.printStackTrace();
                General.println("Error initiating wait on angle thread.");
            }
        }

    }

    private class AngleThread extends Thread
    {
        protected int angle = Camera.getCameraAngle();
        protected long sleepTime = 0;

        @Override
        public synchronized void run()
        {
            try
            {
                while((script != null && script.isActive()) || runsWithoutScript)
                {
                    sleep(sleepTime);

                    Camera.setCameraAngle(angle);

                    wait();
                }
            }
            catch(Exception e)
            {
                General.println("Error initiating wait on angle thread.");
            }
        }

    }

    public RotationThread getRotationThread()
    {
        return rotationThread;
    }

    public AngleThread getAngleThread()
    {
        return angleThread;
    }

    public Script getScript()
    {
        return script;
    }

}