import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

/*
 *  Creature 2:
 *      - has 7 points:
 *          - (x + 1, y + 1, z), (x + 1, y, z + 1), (x, y + 1, z + 1), (x + 1, y + 1, z + 1), (x + 2, y + 1, z + 1), (x +1, y + 2, z + 1), (x + 1, y + 1, z + 2)
 */
public final class StarCreature extends SeaCreature {
    public StarCreature(int maxX, int maxY, int maxZ, int startX, int startY, int startZ, SeaSimulation s) {
        super(maxX, maxY, maxZ, startX, startY, startZ, s);
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
        if (this.y + 2 + moveY > maxY || this.y + moveY < 0) {
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
        // (1, 1, 0), (1, 0, 1), (0, 1, 1), (1, 1, 1), (2, 1, 1), (1, 2, 1), (1, 1, 2)

        // (1, 1, 0)
        boolean successfulMove = this.acquireLock(newX + 1, newY + 1, newZ);

        // (1, 0, 1)
        if (successfulMove) {
            this.acquireLock(newX + 1, newY, newZ + 1);
        }

        // (0, 1, 1)
        if (successfulMove) {
            this.acquireLock(newX, newY + 1, newZ + 1);
        }

        // (1, 1, 1)
        if (successfulMove) {
            this.acquireLock(newX + 1, newY + 1, newZ + 1);
        }

        // (2, 1, 1)
        if (successfulMove) {
            this.acquireLock(newX + 2, newY + 1, newZ + 1);
        }

        // (1, 2, 1)
        if (successfulMove) {
            this.acquireLock(newX + 1, newY + 2, newZ + 1);
        }


        // (1, 1, 2)
        if (successfulMove) {
            this.acquireLock(newX + 1, newY + 1, newZ + 2);
        }
    
        // if able to acquire all locks, write the current reference into the grid
        // and release all previous locks, while clearing the references
        // (1, 1, 0), (1, 0, 1), (0, 1, 1), (1, 1, 1), (2, 1, 1), (1, 2, 1), (1, 1, 2)
        if (successfulMove) {
            // set new references
            this.simulation.grid[newX + 1][newY + 1][newZ] = this;
            this.simulation.grid[newX + 1][newY][newZ + 1] = this;
            this.simulation.grid[newX][newY + 1][newZ + 1] = this;
            this.simulation.grid[newX + 1][newY + 1][newZ + 1] = this;
            this.simulation.grid[newX + 2][newY + 1][newZ + 1] = this;
            this.simulation.grid[newX + 1][newY + 2][newZ + 1] = this;
            this.simulation.grid[newX + 1][newY + 1][newZ + 2] = this;
            

            // reset old references
            this.simulation.grid[x + 1][y + 1][z] = this;
            this.simulation.grid[x + 1][y][z + 1] = this;
            this.simulation.grid[x][y + 1][z + 1] = this;
            this.simulation.grid[x + 1][y + 1][z + 1] = this;
            this.simulation.grid[x + 2][y + 1][z + 1] = this;
            this.simulation.grid[x + 1][y + 2][z + 1] = this;
            this.simulation.grid[x + 1][y + 1][z + 2] = this;

            // update internal position
            this.x = newX;
            this.y = newY;
            this.z = newZ;
        } 

        // release all locks
        for (ReentrantLock lock : moveLocks) {
            lock.unlock();
        }

        // reset the locks list
        moveLocks = new ArrayList<>();
        
        return successfulMove;
    }
}
