import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

/*
 *  Creature 3:
 *      - has points:
 *          - (x, y, z), (x + 1, y, z), (x, y, z + 1), (x, y, z + 2), (x, y + 1, z + 2)
 */

public final class LCreature extends SeaCreature {
    public LCreature(int maxX, int maxY, int maxZ, int startX, int startY, int startZ, SeaSimulation s) {
        super(maxX, maxY, maxZ, startX, startY, startZ, s);

        // no contention at the start, free to move
        this.attemptMove(0, 0, 0);
    }

    
    // attempts to acquire locks to move
    // if we have contention, give up
    public boolean attemptMove(int moveX, int moveY, int moveZ) {
        int newX = x + moveX;
        int newY = y + moveY;
        int newZ = z + moveZ;

        // acquire locks in the same order from low z to high z, low y to high y, then low x to high x
        // (0, 0, 0), (1, 0, 0), (0, 0, 1), (0, 0, 2), (0, 1, 2)
        
        // (0, 0, 0)
        boolean successfulMove = acquireLock(newX, newY, newZ);

        // (1, 0, 0)
        if (successfulMove) {
            successfulMove = acquireLock(newX + 1, newY, newZ);
        }

        // (0, 0, 1)
        if (successfulMove) {
            successfulMove = acquireLock(newX, newY, newZ + 1);
        }

        // (0, 0, 2)
        if (successfulMove) {
            successfulMove = acquireLock(newX, newY, newZ + 2);
        }

        // (0, 1, 2)
        if (successfulMove) {
            successfulMove = acquireLock(newX, newY + 1, newZ + 2);
        }

        // if able to acquire all locks, write the current reference into the grid
        // and release all previous locks, while clearing the references
        if (successfulMove) {
            // set new references
            this.simulation.grid[newX][newY][newZ] = this;
            this.simulation.grid[newX + 1][newY][newZ] = this;
            this.simulation.grid[newX][newY][newZ + 1] = this;
            this.simulation.grid[newX][newY][newZ + 2] = this;
            this.simulation.grid[newX][newY + 1][newZ + 2] = this;

            // reset old references
            this.simulation.grid[x][y][z] = null;
            this.simulation.grid[x + 1][y][z] = null;
            this.simulation.grid[x][y][z + 1] = null;
            this.simulation.grid[x][y][z + 2] = null;
            this.simulation.grid[x][y + 1][z + 2] = null;

            // update internal position
            this.x = newX;
            this.y = newY;
            this.z = newZ;
        } 

        // failed move: release all new locks
        for (ReentrantLock lock : moveLocks) {
            lock.unlock();
        }

        // resets locks
        this.moveLocks = new ArrayList<>();
    
        return successfulMove;
    }

    // checks if the desired move is within the bounds of the grid
    @Override
    public boolean isOutOfBounds(int moveX, int moveY, int moveZ) {
        // +/- one as the star figure goes by one in each direction from the center point.

        // check x coordinate is out of bounds:
        if (this.x + 1 + moveX > maxX || this.x + moveX < 0) {
            return true;
        }

        // check y coordinate is out of bounds:
        if (this.y + 1 + moveY > maxY || this.y + moveY < 0) {
            return true;
        }

        // check if z coordinate is out of bounds:
        if (this.z + 2 + moveZ > maxZ || this.z + moveZ < 0) {
            return true;
        }

        return false;
    }
}
