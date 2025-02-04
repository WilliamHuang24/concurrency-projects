/*
 *  Base abstract class for sea creatures. Defines functions to be implemented by different children.
 */

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

public sealed abstract class SeaCreature implements Runnable permits
    LineCreature, StarCreature, LCreature, RocketCreature {

    // defines a basis point for the creature: 
    // this corresponds to the point (0, 0, 0) relative to the creature as defined in the assignment description
    // (this depends on the creature chosen)
    protected int x;
    protected int z;
    protected int y;
    
    // defines the board width for the sea creatures
    public final int maxX;
    public final int maxY;
    public final int maxZ;

    // unique identifier with regards to each sea creature
    public final int identifier;

    // static count to keep track of which values are unique
    private static int lastUsedIdentifier = 0;

    // store reference to the simulation, which has the grid and the locks
    protected SeaSimulation simulation;

    // stores the locks that the current object is holding for purposes of moving
    protected List<ReentrantLock> moveLocks;

    public SeaCreature(int maxX, int maxY, int maxZ, int startX, int startY, int startZ, SeaSimulation s) {
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;

        this.x = startX;
        this.y = startY;
        this.z = startZ;
        
        this.identifier = getUniqueIdentifier();

        this.simulation = s;
        this.moveLocks = new ArrayList<>();
    }

    public static synchronized int getUniqueIdentifier() {
        SeaCreature.lastUsedIdentifier++;
        return lastUsedIdentifier;
    }

    // defines if a move in the X, Y, Z direction is out of the bounds of the board
    public abstract boolean isOutOfBounds(int moveX, int moveY, int moveZ);

    // checks if move is possible - if the move is possible, move to that square
    public abstract boolean attemptMove(int moveX, int moveY, int moveZ);

    @Override
    public void run() {
        while (this.simulation.simluationStatus) {
            // generate a random move for the sea creature -1, 0, +1 in the x, y, z axises
            int xMove = ThreadLocalRandom.current().nextInt(-1, 2);
            int yMove = ThreadLocalRandom.current().nextInt(-1, 2);
            int zMove = ThreadLocalRandom.current().nextInt(-1, 2);

            // check if this is in bounds - no synchronization is required
            if (isOutOfBounds(xMove, yMove, zMove)) {
                // print failure message to the console.
                System.out.println("Failed move for " + this.getClass().getSimpleName() + " with id " + this.identifier + ". Reason: attempted move out of bounds");
            } else {
                // if the move is inside the bounds - attempt to acquire locks related to itself
                int oldX = this.x;
                int oldY = this.y;
                int oldZ = this.z;
                
                boolean success = attemptMove(xMove, yMove, zMove);

                if (success) {
                    System.out.println(this.toString() + " has moved from (" + oldX + "," + oldY + "," + oldZ + ") to (" + this.x + "," + this.y + "," + this.z + ").");
                } else {
                    System.out.println(this.toString() + " has failed to move. Stayed from (" + oldX + "," + oldY + "," + oldZ + ") to (" + this.x + "," + this.y + "," + this.z + ").");
                }
            }

            // sleep for some (random) amount of time between 10 and 50 milliseconds inclusive
            int sleepTime = ThreadLocalRandom.current().nextInt(10, 51);

            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println(this.toString() + " has terminated");
    }

    // tries to acquire a lock at the point x, y, z.
    // if successful, returns true and adds lock to the moveLocks list
    // returns false on failure
    protected boolean acquireLock(int x, int y, int z) {
        this.simulation.locks[x][y][z].lock();
        boolean successfulMove = true;

        try {
            // if the space is already taken, give up. 
            if (this.simulation.grid[x][y][z] != null) {
                // set return and give up lock
                successfulMove = false;
                this.simulation.locks[x][y][z].unlock();
            } else {
                this.moveLocks.add(this.simulation.locks[x][y][z]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return successfulMove;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " id: " + Integer.toString(this.identifier);
    }
}