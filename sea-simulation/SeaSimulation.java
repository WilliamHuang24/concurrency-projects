import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

public class SeaSimulation implements Runnable {
    public SeaCreature grid[][][];
    public ReentrantLock locks[][][];
    public List<SeaCreature> creatures;

    public final int width;
    public final int length;
    public final int height;
    public final int numCreatures;
    public final int numSeconds;

    public boolean simluationStatus;

    public SeaSimulation(int numCreatures, int numSeconds) {
        this.width = 5 * numCreatures;
        this.length = 5 * numCreatures;
        this.height = 5 * numCreatures;

        // grid that holds the reference to all sea creature
        // instantiated as null
        grid = new SeaCreature[width][length][height];

        // locks that are held by the respective threads. lock x, y, z is head by the creature in the grid at x, y, z 
        locks = new ReentrantLock[width][length][height];

        // instantiate locks
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < length; j++) {
                for (int k = 0; k < height; k++) {
                    locks[i][j][k] = new ReentrantLock();
                }
            } 
        }

        this.numCreatures = numCreatures;
        this.numSeconds = numSeconds;
        this.simluationStatus = true;
        
        // list of creatures inside the grid
        creatures = new ArrayList<>();
    }

    @Override
    public void run() {
        // initialise the simulation
        
        // instantiate the sea creatures in a round robin fashion 
        // we place each creature in its own 3 x 3 x 3 square to ensure that no squares intercept
        for (int i = 0; i < numCreatures; i++) {
            // choose random creature to create
            int creatureNum = ThreadLocalRandom.current().nextInt(0, 3);

            int numCreaturesInLayer = (width % 3) * (length % 3);

            // choose starting location

            // find z - axis value - each layer can store (width % 3) * (length % 3) creatures
            int z = (i % numCreaturesInLayer) * 3;

            // find column of the axis : 
            int x = ((i / numCreaturesInLayer) % (width % 3)) * 3;
            int y = ((i / numCreaturesInLayer) / (width % 3)) * 3;
            
            // instantiate creature
            SeaCreature s = switch (creatureNum) {
                case 0 -> {
                    yield new LineCreature(width - 1, length - 1, height - 1, x, y, z, this);
                }

                case 1 -> {
                    yield new StarCreature(width - 1, length - 1, height - 1, x, y, z, this);
                }

                case 2 -> {
                    yield new LCreature(width - 1, length - 1, height - 1, x, y, z, this);
                }

                default -> {
                    yield new RocketCreature(width, length, height, x, y, z, this);
                }
            };

            creatures.add(s);
        }

        // begin the simulation
        for (SeaCreature s : creatures) {
            Thread t = new Thread(s);
            t.start();
        } 
        
        // run for n seconds
        try {
            Thread.sleep(this.numSeconds * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // send signal to child thread to stop
        this.simluationStatus = false;
    }

    // entry point for the simulation
    public static void main(String args[]) {
        int k = 0;
        int n = 0;

        // parse commant line args
        try {
            k = Integer.parseInt(args[0]);
            n = Integer.parseInt(args[1]);
        } catch (Exception e) {
            System.out.println("Expected integer inputs k and n");
            System.exit(-1);
        }

        // if we have values, start simlutation
        System.out.println("Starting simluation");

        SeaSimulation s = new SeaSimulation(k, n);
        Thread t = new Thread(s);
        t.start();

        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        System.out.println("Simluation finished");
    }
}
