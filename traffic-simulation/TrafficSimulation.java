import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class TrafficSimulation implements Runnable {
    // class that represents a vehicle in the simulation
    public class Vehicle implements Runnable {
        public enum Direction {
            LEFT, RIGHT;
        }

        // keeps track of the last int used as id. Increment to get new id
        private static int lastUniqueId = 0;
        private static Object idLock = new Object();

        // non - static fields, each vehicle has a travel direction and a unique identifier
        public final Direction travelDirection;
        public final int identifier;

        // current segment the vehicle occupies
        private int segment;
        
        public Vehicle(Direction d, int segment) {
            this.identifier = getUniqueIdentifier();
            this.travelDirection = d;

            this.segment = segment;

            // print creation message
            System.out.println("car: " + identifier + "," + segment + ", " + travelDirection);
        }

        // thread safe generator for unique ids
        // ensures no data races for the lastUniqueId field using a mutex
        private static int getUniqueIdentifier() {
            synchronized(idLock) {
                lastUniqueId++;
                return lastUniqueId;
            }
        }

        @Override
        public void run() {
            // depending on the direction, we call begin. This instructs the car to either wait or to enter the road.
            // upon entry, we move along the board, acquiring segments in front and the currrent segment's locks to ensure no overtaking
            // upon exit, we call the end function which changes the board accordingly
            if (this.travelDirection == Direction.LEFT) {
                beginLeft();
                System.out.println("enter: " + identifier + "," + segment);
                while (segment > 0) {

                    synchronized(locks[segment]) {
                        synchronized(locks[segment - 1]) {
                            segments[segment] = null;
                            segments[segment - 1] = this; 
                        }
                    }
                        
                    this.segment -= 1;
    
                    try {
                        Thread.sleep(timeToTraverseSegment);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                System.out.println("exit: " + identifier);
                endLeft();
            } else {
                beginRight();
                System.out.println("enter: " + identifier + "," + segment);
                while (segment < roadSegments - 1) {
                    synchronized(locks[segment]) {
                        synchronized(locks[segment + 1]) {
                            segments[segment] = null;
                            segments[segment + 1] = this; 
                        }
                    }
                    
                    this.segment += 1;

                    System.out.println("traverse: " + identifier + "," + segment);
    
                    try {
                        Thread.sleep(timeToTraverseSegment);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                System.out.println("exit: " + identifier);
                endRight();
            }
        }
    }

    // fields belonging to the simulation
    public final int roadSegments;
    public final int timeToTraverseSegment;
    public final int s;
    public Direction roadDirection;

    private boolean simulationStatus;

    private Vehicle segments[];
    private Object locks[];

    // keeps track of amount of cars queued at any point
    // AtomicIntegers are not strictly necessary
    private AtomicInteger goingLeft;
    private AtomicInteger goingRight;
    private AtomicInteger waitingLeft;
    private AtomicInteger waitingRight;

    private Object roadLock;

    public TrafficSimulation(int n, int d, int s, Direction initialDirection) {
        this.roadSegments = n;
        this.timeToTraverseSegment = d;
        this.s = s;
        this.simulationStatus = true;

        this.segments = new Vehicle[n];
        this.locks = new Object[n];

        for (int i = 0; i < n; i++) {
            this.locks[i] = new Object();
        }

        this.roadDirection = initialDirection;
        this.roadLock = new Object();

        goingLeft = new AtomicInteger(0);
        goingRight = new AtomicInteger(0);
        waitingLeft = new AtomicInteger(0);
        waitingRight = new AtomicInteger(0);
    }


    // each vehicle moving left will call this
    // it either tells the vehicle to wait for vehicles moving in the opposite direction to end or that the path is clear to proceed
    // depending on the direction of the road.

    public void beginLeft() {
        synchronized(roadLock) {
            //System.out.println("begin left #1. (waiting right, going right): (" + waitingRight.get() + "," + goingRight.get() + ") " + this.roadDirection);

            // if there are currently vehicles going right or if there are vehicles waiting to go right
            if (goingRight.get() > 0 || waitingRight.get() > 0) {
                waitingLeft.incrementAndGet();

                do {
                    try {
                        roadLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } while (this.roadDirection != Direction.LEFT);

                waitingLeft.decrementAndGet();
                
            }

            //System.out.println("begin left #2. (waiting right, going right): (" + waitingRight.get() + "," + goingRight.get() + ") " + this.roadDirection);

            goingLeft.incrementAndGet();
        }
    }

    // each vehicle done moving will call this.
    // will switch directions to ensure fairness and notify all waiting vehicles to enter.

    public void endLeft() {
        synchronized (roadLock) {
            goingLeft.decrementAndGet();

            if (goingLeft.get() == 0) {
                this.roadDirection = Direction.RIGHT;
                roadLock.notifyAll();
            }
        }
    }

    // same as the beginLeft, except for the opposite direction
    public void beginRight() {
        synchronized(roadLock) {
            //System.out.println("begin right #1. (waiting left, going left): (" + waitingLeft.get() + "," + waitingLeft.get() + ") " + this.roadDirection);

            // if there are currently vehicles going left or if there are vehicles waiting to go right
            if (goingLeft.get() > 0 || waitingLeft.get() > 0) {
                waitingRight.incrementAndGet();

                do {
                    try {
                        roadLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } while (this.roadDirection != Direction.RIGHT);

                waitingRight.decrementAndGet();
            }

            //System.out.println("begin right #2. (waiting left, going left): (" + waitingLeft.get() + "," + waitingLeft.get() + ") " + this.roadDirection);

            goingRight.incrementAndGet();
        }
    }

    // same as endLeft, but for Right.
    public void endRight() {
        synchronized (roadLock) {
            goingRight.decrementAndGet();

            if (goingLeft.get() == 0) {
                this.roadDirection = Direction.LEFT;
                roadLock.notifyAll();
            }
        }
    }

    @Override
    public void run() {
        while (simulationStatus) {
            // pick which which way the car will travel
            // 45% chance left
            // 45% chance right
            // 10% chance residence

            // generate uniformly random integer in the range 0 - 99 inclusive and determine the next step
            int randomNum = ThreadLocalRandom.current().nextInt(0, 100);

            if (randomNum < 45) {
                // going left means we start at the limit and move towards smaller numbers
                new Thread(new Vehicle(Direction.LEFT, roadSegments - 1)).start();
            } else if (randomNum < 90) {
                // going right means we start at -1 and move towards larger numbers
                new Thread(new Vehicle(Direction.RIGHT, 0)).start();
            } else {
                // pick random starting point
                int startPoint = ThreadLocalRandom.current().nextInt(0, roadSegments);

                // pick random direction
                int direction = ThreadLocalRandom.current().nextInt(0,  2);

                if (direction == 1) {
                    new Thread(new Vehicle(Direction.LEFT, startPoint)).start();
                } else {
                    new Thread(new Vehicle(Direction.RIGHT, startPoint)).start();
                }
            }

            // generate +- 20 for the delay between adding vehicles
            int delay = s + ThreadLocalRandom.current().nextInt(-20, 21);
            
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } 
    }

    // program entry point
    public static void main(String [] args) {
        // default values to satisfy compiler (will be overwritten on successful input parse)
        int n = 5;
        int s = 25;
        int d = 15;
    
        // parse command line arguments : n > 2, s > 20, d > 10 in that order
        try {
            n = Integer.parseInt(args[0]);

            if (n <= 2) {
                throw new IllegalArgumentException();
            }

            s = Integer.parseInt(args[1]);

            if (s <= 20) {
                throw new IllegalArgumentException();
            }

            d = Integer.parseInt(args[2]);

            if (d <= 10) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            System.out.println("Invalid command line inputs. n > 2, s > 20, d > 10 in that order. They must be integers");
            System.exit(-1);
        }

        // if parse is successful, begin simulation
        TrafficSimulation simulation = new TrafficSimulation(n, d, s, Direction.LEFT);

        Thread t = new Thread(simulation);
        t.start();
    }   
}
