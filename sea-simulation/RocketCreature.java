
/*
 *  Creature 4:
 *      - has 6 points:
 *          - (x, y, z), (x + 2, y, z), (x, y, z + 1), (x + 1, y, z + 1), (x + 2, y, z + 1), (x + 1, y, z + 2)
 */

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

public final class RocketCreature extends SeaCreature {
    public RocketCreature(int maxX, int maxY, int maxZ, int startX, int startY, int startZ, SeaSimulation s) {
        super(maxX, maxY, maxZ, startX, startY, startZ, s);
        
        // set start position - assume no contention
        this.attemptMove(0, 0, 0);
    }

    
    @Override
    public boolean isOutOfBounds(int moveX, int moveY, int moveZ) {
        // check x coordinate is out of bounds:
        // highest x bound : x + 2, lowest x bound : x
        if (this.x + 2 + moveX > maxX || this.x + moveX < 0) {
            return true;
        }

        // check y coordinate is out of bounds:
        // highest y bound and lowest y bound : y
        if (this.y + moveY > maxY || this.y + moveY < 0) {
            return true;
        }

        // check if z coordinate is out of bounds:
        // highest z bound : z + 2, lowest z bound : z
        if (this.z + 2 + moveZ > maxZ || this.z + moveZ < 0) {
            return true;
        }

        return false;
    }

    @Override
    public boolean attemptMove(int moveX, int moveY, int moveZ) {
        int newX = x + moveX;
        int newY = y + moveY;
        int newZ = z + moveZ;

        // acquire locks in the same order from low z to high z, low y to high y, then low x to high x
        // (0, 0, 0), (2, 0, 0), (0, 0, 1), (1, 0, 1), (2, 0, 1), (1, 0, 2)

        // (0, 0, 0)
        boolean successfulMove = this.acquireLock(newX, newY, newZ);

        // (2, 0, 0)
        if (successfulMove) {
            successfulMove = this.acquireLock(newX + 2, newY, newZ);
        }

        // (0, 0, 1)
        if (successfulMove) {
            successfulMove = this.acquireLock(newX, newY, newZ + 1);
        }

        // (1, 0, 1)
        if (successfulMove) {
            successfulMove = this.acquireLock(newX + 1, newY, newZ + 1);
        }

        // (2, 0, 1)
        if (successfulMove) {
            successfulMove = this.acquireLock(newX + 2, newY, newZ + 1);
        }

        // (1, 0, 2)
        if (successfulMove) {
            successfulMove = this.acquireLock(newX + 1, newY, newZ + 2);
        }

        // at the end of the lock gathering, if we find that this object has all the necessary locks:
        // write references and release locks
        if (successfulMove) {
            // reset references

            // (0, 0, 0), (2, 0, 0), (0, 0, 1), (1, 0, 1), (2, 0, 1), (1, 0, 2)
            this.simulation.grid[x][y][z] = null;
            this.simulation.grid[x + 2][y][z] = null;
            this.simulation.grid[x][y][z + 1] = null;
            this.simulation.grid[x + 1][y][z + 1] = null;
            this.simulation.grid[x + 2][y][z + 1] = null;
            this.simulation.grid[x + 1][y][z + 2] = null;

            // update internal references
            this.x = newX;
            this.y = newY;
            this.z = newZ;

            // set new references
            this.simulation.grid[x][y][z] = this;
            this.simulation.grid[x + 2][y][z] = this;
            this.simulation.grid[x][y][z + 1] = this;
            this.simulation.grid[x + 1][y][z + 1] = this;
            this.simulation.grid[x + 2][y][z + 1] = this;
            this.simulation.grid[x + 1][y][z + 2] = this;
        } 

        // release all the locks for the move, no longer need them
        for (ReentrantLock lock : this.moveLocks) {
            lock.unlock();
        }

        // reset locks
        this.moveLocks = new ArrayList<>();

        return successfulMove;
    }
}
