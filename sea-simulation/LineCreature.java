import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

/*
 *  Creature 1:
 *      - has points:
 *          - (x, y, z), (x, y, z + 1), (x, y, z + 2)
 */

public final class LineCreature extends SeaCreature {
    public LineCreature(int maxX, int maxY, int maxZ, int startX, int startY, int startZ, SeaSimulation s) {
        super(maxX, maxY, maxZ, startX, startY, startZ, s);

        // set start position -  we know this will be free from contention because of our allocator
        attemptMove(0, 0, 0);
    }
    
    @Override
    public boolean isOutOfBounds(int moveX, int moveY, int moveZ) {
        // check x coordinate is out of bounds:
        if (this.x + moveX > maxX || this.x + moveX < 0) {
            return true;
        }

        // check y coordinate is out of bounds:
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
        // (0, 0, 0), (0, 0, 1), (0, 0, 2)

        // (0, 0, 0)
        boolean successfulMove = this.acquireLock(newX, newY, newZ);
        
        // (0, 0, 1)
        if (successfulMove) {
            successfulMove = this.acquireLock(newX, newY, newZ + 1);
        }

        // (0, 0, 2)
        if (successfulMove) {
            successfulMove = this.acquireLock(newX, newY, newZ + 2);
        }

        // if able to acquire all locks
        if (successfulMove) {
            this.simulation.grid[newX][newY][newZ] = this;
            this.simulation.grid[newX][newY][newZ + 1] = this;
            this.simulation.grid[newX][newY][newZ + 2] = this;

            this.x = newX;
            this.y = newY;
            this.z = newZ;

            this.simulation.grid[newX][newY][newZ] = null;
            this.simulation.grid[newX][newY][newZ + 1] = null;
            this.simulation.grid[newX][newY][newZ + 2] = null;
        }

        // release locks
        for (ReentrantLock lock : this.moveLocks) {
            lock.unlock();
        }

        // reset locks
        this.moveLocks = new ArrayList<>();

        return successfulMove;
    }
}