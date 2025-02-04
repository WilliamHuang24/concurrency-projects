import java.util.concurrent.ThreadLocalRandom;

/*
 *  Given the requirement to allow stations to function independently, a global lock does not work as stations would have to wait for others to allow passengers 
 *  So given this scenario, 4 locks were used, one for each station
 *  The overseer acquires all four locks, freezing the different stations to acquire an accurate count and release the locks when done.
 */

public class MetroSystem implements Runnable {
    public class Station implements Runnable {
        private int localCount;
        private Object lock;
        private boolean allowEntry;

        public Station(Object lock) {
            this.localCount = 0;
            this.lock = lock;
            this.allowEntry = true;
        }

        public void run() {
            while(true) {
                // generates an integer uniformly at random in the range 0 to 99 inclusive
                // use ThreadLocalRandom to avoid contention in the case of Random
                int randomNum = ThreadLocalRandom.current().nextInt(0, 100);  

                // take advantage of the uniform distribution
                // since each number between 0 - 99 has an equal chance of being generated
                // then there are 49 numbers in the range 0 <= x <= 48 
                // and there are 51 numbers in the range 49 <= x <= 99

                // so 49% chance and 51% chance respectively

                // then we only add allow entry if two conditions are true:
                // - it is the case in the 51% that a passenger chooses to leave
                // - it is the case that the overseer allows passengers to enter : not above maximum capacity

                // however, there are no restrictions on leaving the station as long as we are in the 49% case
                synchronized (this.lock) {
                    if (randomNum < 49) {
                        this.localCount -= 1;
                    } else if (allowEntry) {
                        this.localCount += 1;
                    }
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static final int NUM_STATIONS = 4;
    private Station[] stations;
    private Object[] locks;
    private int maxCapacity;
    private int minBound;
    private int observerCount;
    private boolean normalPeriodicity;
    private int period;

    public MetroSystem(int maxCapacity, int interval) {
        // instantiate stations and locks
        stations = new Station[NUM_STATIONS];
        locks = new Object[NUM_STATIONS];

        for (int i = 0; i < NUM_STATIONS; i++) {
            locks[i] = new Object();
            stations[i] = new Station(locks[i]);
        }


        this.maxCapacity = maxCapacity;
        this.minBound =  (int) (0.75 * maxCapacity);
        
        this.period = interval;
        this.normalPeriodicity = true;

        this.observerCount = 0;
        
    }

    public void run() {
        // start the stations
        for (Station s : stations) {
            Thread t = new Thread(s);
            t.start();
        }

        while (true) {
            // acquire locks on all stations
            // this prevents passengers from being admitted or for passengers to leave
            synchronized (this.locks[0]) {
                synchronized (this.locks[1]) {
                    synchronized (this.locks[2]) {
                        synchronized (this.locks[3]) {
                            // tally the total number of passengers
                            this.observerCount = 0;

                            for (int i = 0; i < NUM_STATIONS; i++) {
                                this.observerCount += stations[i].localCount;
                            }

                            boolean allowEntry;

                            // if we are checking normally, we have not been over cap previously
                            // then we allow entry if the count is less than the cap and disallow entry when the sum is over the cap

                            // otherwise, if we are in only allowing people out : this means that we recheck until the station reaches 0.75n capacity
                            if (this.normalPeriodicity) {
                                if (this.observerCount < this.maxCapacity) {
                                    allowEntry = true;
                                } else {
                                    allowEntry = false;
                                    this.normalPeriodicity = false;
                                }
                            } else {
                                // if we find that the station has reached 0.75n capacity, we can resume operating as normal
                                if (this.observerCount < this.minBound) {
                                    allowEntry = true;
                                    this.normalPeriodicity = true;
                                } else {
                                    allowEntry = false;
                                }
                            }
                            
                            // configure stations to allow passengers or not
                            for (int i = 0; i < NUM_STATIONS; i++) {
                                stations[i].allowEntry = allowEntry;
                            }
                            
                        }
                    }
                }
            }

            // print the overseer count every time it is computed
            String msg = "Overseer Count : " + Integer.toString(this.observerCount);
            System.out.println(msg);

            // if we are over capacity : check every q / 10 ms
            // if we are operating as normal : check every q milliseconds
            try {
                if (this.normalPeriodicity) {
                    Thread.sleep(period);
                } else {
                    Thread.sleep(period / 10);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    // entry point of the program - parse command line input and intitiate the metro system
    // expected syntax is java MetroSystem(.java) n q
    // depending on if using javac or not
    public static void main(String[] args) {
        int n;
        int q;

        try {
            n = Integer.parseInt(args[0]);
            q = Integer.parseInt(args[1]);

            MetroSystem m = new MetroSystem(n, q);

            Thread t = new Thread(m);
            t.start();

        } catch (Exception e) {
            System.out.println("Only the following syntax is accepted: \"java MetroSystem.java n q\" where n and q are integers");
        }
    } 
}
