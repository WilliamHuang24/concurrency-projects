import java.util.LinkedList;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class StackSimulation implements Runnable {
    // node class used by the stacks. what is passed in push methods and what is returned from pop methoeds
    public static class Node<T> {
        public T element;
        public Node<T> next;

        public Node(T element) {
            this.element = element;
        }
    }

    // base stack class
    public static abstract class BaseStack<T> {
        public abstract Node<T> pop();
        public abstract void push(Node<T> element);
        public abstract int size();
    }

    public static class LockFreeStack<T> extends BaseStack<T> {
        private AtomicReference<Node<T>> head;

        public LockFreeStack() {
            head = new AtomicReference<>();
        }

        @Override
        public Node<T> pop() {
            Node<T> top;
            Node<T> temp;

            // attempt to get the head
            // change the head if the head is not changed by some other thread dusing this time
            do {
                temp = head.get();

                if (temp == null) {
                    return null;
                }

                top = temp.next;
            } while (!head.compareAndSet(temp, top));

            // make sure the node is not pointing at anything and return
            temp.next = null;
            return temp;
        }

        @Override
        public void push(Node<T> newNode) {
            Node<T> temp;

            // temp holds the reference to the top of the stack
            // the compare and set only changes the head of the stack if it has not change. i.e. nothing is pushed before we push
            do {
                temp = head.get();
                newNode.next = temp;
            } while (!head.compareAndSet(temp, newNode));
        }

        @Override
        public int size() {
            Node<T> temp = head.get();
            int count = 0;

            while (temp.next != null) {
                count++;
                temp = temp.next;
            }

            return count;
        }
        
    }

    public static class EliminationStack<T> extends BaseStack<T> {
        private Exchanger<Node<T>> exchangers[];
        private AtomicReference<Node<T>> head;
        public final int maxDelay;

        @SuppressWarnings("unchecked")
        public EliminationStack(int eliminationSize, int maxDelay) {
            this.maxDelay = maxDelay;

            exchangers = new Exchanger[eliminationSize];

            for (int i = 0; i < eliminationSize; i++) {
                exchangers[i] = new Exchanger<Node<T>>();
            }

            head = new AtomicReference<>();
        }

        private Node<T> exchange(Node<T> item) throws TimeoutException {
            // select random exchanger
            int index = ThreadLocalRandom.current().nextInt(exchangers.length);
            try {
                return exchangers[index].exchange(item, maxDelay, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } 

            // return null if the thread is interrupted
            return null;
        }

        // try to exchange in the elimination array
        // if we hit a timeout, then we pop as normal
        @Override
        public Node<T> pop() {
            try {
                Node<T> result = exchange(null);

                // case where we managed to exchange an item
                if (result != null) {
                    // reset next pointer for reuse later
                    result.next = null;

                    return result;
                }
            } catch (TimeoutException t) {}

            // at this point, the node has not been successfully exchanged. Push as normal
            Node<T> top;
            Node<T> temp;

            // attempt to get the head
            // change the head if the head is not changed by some other thread dusing this time
            do {
                temp = head.get();

                if (temp == null) {
                    return null;
                }

                top = temp.next;
            } while (!head.compareAndSet(temp, top));

            // make sure the node is not pointing at anything and return
            temp.next = null;
            return temp;
        }

        // try to exchange in the elimination array
        // if we hit a timeout, then we push as normal
        @Override
        public void push(Node<T> newNode) {
            try {
                Node<T> result = exchange(newNode);

                // case where we managed to exchange an item
                if (result == null) {
                    return;
                }

            } catch (TimeoutException t) {}

            // at this point, if the node has not been successfully exchanged, push as normal
            Node<T> temp;

            // temp holds the reference to the top of the stack
            // the compare and set only changes the head of the stack if it has not change. i.e. nothing is pushed before we push
            do {
                temp = head.get();
                newNode.next = temp;
            } while (!head.compareAndSet(temp, newNode));
        }

        @Override
        public int size() {
            Node<T> temp = head.get();
            int count = 0;

            while (temp.next != null) {
                count++;
                temp = temp.next;
            }

            return count;
        }
    }

    public final BaseStack<Integer> stack;
    public final int maxSleepTime;
    public final int maxOperations;
    
    private int operationCount;
    private LinkedList<Node<Integer>> prevNodes;
    

    public StackSimulation(BaseStack<Integer> stack, int maxSleepTime, int maxOperations) {
        this.stack = stack;
        this.maxSleepTime = maxSleepTime;
        this.maxOperations = maxOperations;

        this.prevNodes = new LinkedList<>();
    }

    @Override
    public void run() {
        while (operationCount < maxOperations) {
            // push action
            if (ThreadLocalRandom.current().nextBoolean()) {
                Node<Integer> temp;

                if (!prevNodes.isEmpty()) {
                    if (ThreadLocalRandom.current().nextBoolean()) {
                        // retrieves the first item of the list
                        temp = prevNodes.poll();
                    } else {
                        temp = new Node<Integer>(Integer.valueOf(ThreadLocalRandom.current().nextInt(1000)));
                    }
                } else {
                    temp = new Node<Integer>(Integer.valueOf(ThreadLocalRandom.current().nextInt(1000)));
                }

                // push to stack
                stack.push(temp);

                operationCount++;

            // pop action
            } else {
                Node<Integer> result = stack.pop();

                // add the node to the cache
                if (result != null) {
                    if (prevNodes.size() < 50) {
                        prevNodes.addFirst(result);
                    } else {
                        prevNodes.pollLast();
                        prevNodes.addLast(result);
                    }

                    // succcesful operation
                    operationCount++;
                }
            }

            // sleep for some random amount of time
            int sleepTime = ThreadLocalRandom.current().nextInt(maxSleepTime);

            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    // test driver code - read command line arguements and start simulation
    public static void main(String[] args) {
        int x = 0;
        int t = 10;
        int n = 1000;
        int s = 20;
        int e = 10;
        int w = 20;

        try {
            x = Integer.parseInt(args[0]);
            t = Integer.parseInt(args[1]);
            n = Integer.parseInt(args[2]);
            s = Integer.parseInt(args[3]);
            e = Integer.parseInt(args[4]);
            w = Integer.parseInt(args[5]);
        } catch (Exception exception) {
            System.out.println("Incorrect command line arguments");
            System.exit(0);
        }

        // set up the simulation
        Thread threads[] = new Thread[t];
        BaseStack<Integer> stack;

        if (x == 0) {
            stack = new LockFreeStack<>();
        } else {
            stack = new EliminationStack<>(e, w);
        }

        for (int i = 0; i < t; i++) {
            threads[i] = new Thread(new StackSimulation(stack, s, n));
        }

        // start the simulation and the timer
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < t; i++) {
            threads[i].start();
        }

        for (int i = 0; i < t; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
        }

        long endTime = System.currentTimeMillis();
        long diff = endTime - startTime;

        System.out.println(diff + " " + stack.size());
    }
}
