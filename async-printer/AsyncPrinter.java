import java.awt.image.*;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import javax.imageio.*;

public class AsyncPrinter {
    // output image
    public static BufferedImage imgout;

    // parameters and their default values
    public static String imagebase = "cat" ; // base name of the input images; actual names append "1.png", "2.png" etc.
    public static int threads = 1; // number of threads to use
    public static int outputheight = 4096; // output image height
    public static int outputwidth = 4096; // output image width
    public static int attempts = 20; // number of failed attempts before a thread gives up
    public static BufferedImage outputImage;
    public static BufferedImage placeholderImage;
    
    // print out command-line parameter help and exit
    public static void help(String s) {
        System.out.println("Could not parse argument \""+s+"\".  Please use only the following arguments:");
        System.out.println(" -i inputimagebasename (string; current=\""+imagebase+"\")");
        System.out.println(" -h outputimageheight (integer; current=\""+outputheight+"\")");
        System.out.println(" -w outputimagewidth (integer; current=\""+outputwidth+"\")");
        System.out.println(" -a attempts (integer value >=1; current=\""+attempts+"\")");
        System.out.println(" -t threads (integer value >=1; current=\""+threads+"\")");
        System.exit(1);
    }

    // process command-line options
    public static void opts(String[] args) {
        int i = 0;

        try {
            for (;i<args.length;i++) {

                if (i==args.length-1)
                    help(args[i]);

                if (args[i].equals("-i")) {
                    imagebase = args[i+1];
                } else if (args[i].equals("-h")) {
                    outputheight = Integer.parseInt(args[i+1]);
                } else if (args[i].equals("-w")) {
                    outputwidth = Integer.parseInt(args[i+1]);
                } else if (args[i].equals("-t")) {
                    threads = Integer.parseInt(args[i+1]);
                } else if (args[i].equals("-a")) {
                    attempts = Integer.parseInt(args[i+1]);
                } else {
                    help(args[i]);
                }
                // an extra increment since our options consist of 2 pieces
                i++;
            }
        } catch (Exception e) {
            System.err.println(e);
            help(args[i]);
        }
    }

    // main.  we allow an IOException in case the image loading/storing fails.
    public static void main(String[] args) throws IOException {
        // process options
        opts(args);

        // create an output image
        outputImage = new BufferedImage(outputwidth,outputheight,BufferedImage.TYPE_INT_ARGB);
        placeholderImage = new BufferedImage(outputwidth,outputheight,BufferedImage.TYPE_INT_ARGB);

        // load the images and instantiate the writer objects
        Thread thread_list[] = new Thread[6];

        for (int i = 0; i < threads; i++) {
            BufferedImage imageIn = ImageIO.read(new File(imagebase + Integer.toString(i + 1) + ".png"));
            int h = imageIn.getHeight();
            int w = imageIn.getWidth();

            thread_list[i] = new Thread(new ImageWriter(imageIn, w, h));
        }

        long start = System.currentTimeMillis();

        // start the threads :
        // we do this after loading to account for i/o latency, so all threads will start close in time:
        for (int i = 0; i < threads; i++) {
            thread_list[i].start();
        }

        // we want the final completed product 
        // must join threads to see result:

        for (int i = 0; i < threads; i++) {
            try {
                thread_list[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        long end = System.currentTimeMillis();

        System.out.println(end - start);

        // Write out the image
        File outputfile = new File("outputimage.png");
        ImageIO.write(outputImage, "png", outputfile);
    }


    public static ArrayList<Rectangle> reservedPoints = new ArrayList<>();

    public static class Rectangle {
        public final int x_left;
        public final int x_right;
        public final int y_top;
        public final int y_bottom;

        public Rectangle(int x_left, int x_right, int y_top, int y_bottom) {
            this.x_left = x_left;
            this.x_right = x_right;
            this.y_top = y_top;
            this.y_bottom = y_bottom;
        }

        public boolean overlap(Rectangle r) {
            // this rectangle is on the left of r
            // or if this rectangle is on the right of r
            // then we cannot overlap

            if (this.x_right < r.x_left || this.x_left > r.x_right) {
                return false;
            }

            // same logic except for the y axis:

            if (this.y_top > r.y_bottom || this.y_bottom < r.y_top) {
                return false;
            }

            // if we reach this point, then the rectangles intersect at some point
            // as one rectangle is neither higher than the other nor more to the right

            return true;
        }

        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }

            if (o instanceof Rectangle) {
                Rectangle r1 = (Rectangle) o;
                return this.x_left == r1.x_left && this.x_right == r1.x_right && this.y_bottom == r1.y_bottom && this.y_top == r1.y_top;
            } else {
                return false;
            }
        }


    }

    // check if the given point is not reserved by another thread
    // synchronized across q2 class. Only one worker thread can use this at a time
    public static synchronized boolean checkFreePoint(Rectangle possibleImage) {
        for (Rectangle r : reservedPoints) {
            if (r.overlap(possibleImage)) {
                return false;
            } 
        }

        // if the possible spot is free -> reserve immediately
        reservedPoints.add(possibleImage);
    
        return true;
    }

    public static synchronized boolean removeReservation(Rectangle rect) {
        return reservedPoints.remove(rect);
    }

    public static class ImageWriter implements Runnable {
        public final int imageWidth;
        public final int imageHeight;
        public final BufferedImage image;

        private int attempts;

        public ImageWriter(BufferedImage image, int width, int height) {
            this.image = image;
            this.imageWidth = width;
            this.imageHeight = height;

            this.attempts = 0;
        }

        @Override
        public void run() {
            while (attempts < 20) {
                // sample a random point :
                int i = ThreadLocalRandom.current().nextInt(0, q2.outputwidth - imageWidth - 1);  
                int j = ThreadLocalRandom.current().nextInt(0, q2.outputheight - imageHeight - 1);  

                

                // check if the spot is available from an already written image
                // check if the top left corner is coloured :
                if ((placeholderImage.getRGB(i, j) == 1) || (placeholderImage.getRGB(i + imageWidth, j) == 1)
                || (placeholderImage.getRGB(i, j + imageHeight) == 1) || (placeholderImage.getRGB(i + imageWidth, j + imageHeight) == 1)) {
                    this.attempts++;
                    continue;
                }

                // check if the current point is reserved by another thread :
                Rectangle r = new Rectangle(i, i + imageWidth, j, j + imageHeight);
                boolean isFree = checkFreePoint(r);

                if (isFree) {
                    // if we have reached this point that means that the spot is free
                    // and that it has been reserved for this thread
 
                    // print the image at position i, j on the output image
                    for (int x = 0; x < this.imageWidth; x++) {
                        for (int y = 0; y < this.imageHeight; y++) {

                            outputImage.setRGB(x + i, y + j, this.image.getRGB(x,y));
                            placeholderImage.setRGB(x, y, 1);
                            
                        }
                    }

                    attempts = 0;

                } else {
                    attempts++;
                    continue;
                }
            }
        }
    }
}
