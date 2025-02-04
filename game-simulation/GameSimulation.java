import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

public class GameSimulation implements Runnable {
    public class GameCharacter implements Runnable {
        public int x;
        public int y;

        public int goalX;
        public int goalY;

        public boolean collided;

        // list of points to traverse
        private LinkedList<Integer> xPoints;
        private LinkedList<Integer> yPoints;

        public GameCharacter(int startX, int startY) {
            this.x = startX;
            this.y = startY;
            
            this.collided = false;

            xPoints = new LinkedList<>();
            yPoints = new LinkedList<>();

            reset();
        }

        private void reset() {
            int diffX = ThreadLocalRandom.current().nextInt(-10, 11);
            int diffY = ThreadLocalRandom.current().nextInt(-10, 11);

            int savedX = x;
            int savedY = y;

            // check if out of bounds
            if (x + diffX < 0) {
                this.goalX = 0;
            } else if (x + diffX >= width) {
                this.goalX = width - 1;
            } else {
                this.goalX = x + diffX;
            }

            // check if out of bounds
            if (y + diffY < 0) {
                this.goalY = 0;
            } else if (y + diffY >= height) {
                this.goalY = height - 1;
            } else {
                this.goalY = y + diffY;
            }

            // generate points to traverse, using bresenham's line algorithm. Implementation of pseudocode found online
            int dx = Math.abs(goalX - x);
            int sx;

            if (x < goalX) {
                sx = 1;
            } else {
                sx = -1;
            }

            int dy = -Math.abs(goalY - y);
            int sy;

            if (y < goalY) {
                sy = 1;
            } else {
                sy = -1;
            }

            int e = dx + dy;

            while (true) {
                xPoints.add(x);
                yPoints.add(y);

                if (x == goalX && y == goalY) {
                    break;
                }

                int e2 = 2 * e;

                if (e2 >= dy) {
                    if (x == goalX) {
                        break;
                    }

                    e = e + dy;
                    x = x + sx;
                }

                if (e2 <= dx) {
                    if (y == goalY) {
                        break;
                    }

                    e = e + dx;
                    y = y + sy;
                }
            }

            // restore original points
            x = savedX;
            y = savedY;

            // remove starting point from move queue
            xPoints.remove();
            yPoints.remove();
        }

        @Override
        public void run() {
            while(!collided && running) {
                // if the character has reached the goal - generate new goal
                if (x == goalX && y == goalY) {
                    reset();
                }

                while (!xPoints.isEmpty() && running) {
                    int newX = xPoints.remove();
                    int newY = yPoints.remove();

                    // write reference into the board
                    GameCharacter reference = board[newX][newY].getAndSet(this);

                    // if the square is not null, we have collided
                    if (reference != null) {
                        // System.out.println(this + " has collided at (" + x + ", " + y + ")");

                        reference.collided = true;
                        this.collided = true;

                        board[newX][newY].set(null); 
                    } else {
                        incrementSuccessfulMoves();
                    }

                    board[x][y].set(null); 

                    // update x, y
                    x = newX;
                    y = newY;

                    // pause for 20 ms
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {

                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public final int width;
    public final int height;
    public final AtomicReference<GameCharacter> board[][];
    public final int characterCreationDelay;
    public final int startingCharacters;

    public boolean running;
    public boolean useThreadPool;

    public int successfulMoves;
    public Object countLock;

    public int maxThreads;
    

    @SuppressWarnings("unchecked")
    public GameSimulation(int maxX, int maxY, int s, int n) {
        this.width = maxX;
        this.height = maxY;

        board = new AtomicReference[width][height];

        this.startingCharacters = n;

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                board[i][j] = new AtomicReference<GameCharacter>();
            }
        }

        this.running = true;
        this.useThreadPool = false;
        this.successfulMoves = 0;
        this.countLock = new Object();
        this.characterCreationDelay = s;
    }

    public GameSimulation(int maxX, int maxY, int s, int n, int maxThreads) {
        this(maxX, maxY, s, n);

        this.maxThreads = maxThreads;
        this.useThreadPool = true;
    }

    public void incrementSuccessfulMoves() {
        synchronized (countLock) {
            successfulMoves += 1;
        }
    }

    // returns a game character with a random destination starting on the edge of the map
    private GameCharacter generate() {
        // generate point on top or bottom
        if (ThreadLocalRandom.current().nextBoolean()) {
            int startX;
            int startY = ThreadLocalRandom.current().nextInt(height);

            if (ThreadLocalRandom.current().nextBoolean()) {
                startX = 0;
            } else {
                startX = width - 1;
            }

            return new GameCharacter(startX, startY);

        // generate point on left or right
        } else {
            int startX = ThreadLocalRandom.current().nextInt(width);
            int startY;

            if (ThreadLocalRandom.current().nextBoolean()) {
                startY = 0;
            } else {
                startY = height - 1;
            }

            return new GameCharacter(startX, startY);
        }
    }

    @Override
    public void run() {
        if (useThreadPool) {
            System.out.println("Starting pooled simulation");

            // instantiate thread pool
            ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxThreads);
            List<GameCharacter> characters = new ArrayList<>();

            // instantiate n characters
            for (int i = 0; i < startingCharacters; i++) {
                characters.add(generate());
            }

            // add all characters to the board
            for (GameCharacter g : characters) {
                executor.execute(g);
            }
            
            while (running) {
                // generate character
                executor.execute(generate());

                // sleep for s ms
                try {
                    Thread.sleep(characterCreationDelay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            executor.shutdown();
        } else {
            System.out.println("Starting non-pooled simulation");

            Thread threads[] = new Thread[startingCharacters];

            for (int i = 0; i < startingCharacters; i++) {
                threads[i] = new Thread(generate());
                threads[i].start();
            }

            while (running) {
                (new Thread(generate())).start();

                // sleep for s ms
                try {
                    Thread.sleep(characterCreationDelay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
