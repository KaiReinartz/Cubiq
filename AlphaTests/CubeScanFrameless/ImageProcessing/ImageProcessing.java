package AlphaTests.CubeScanFrameless.ImageProcessing;

import AlphaTests.CubeScanFrameless.Model.CubeScanFramelessModel;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.videoio.VideoCapture;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ImageProcessing implements Observer {

    private CubeScanFramelessModel model;
    private VideoCapture videoCapture;
    private List<int[][]> scannedCubeSides = new ArrayList<>();
    private List<double[]> centerColorValues = new ArrayList<>();
    private ScheduledExecutorService timer;

    private void startWebcamStream() {
        videoCapture = new VideoCapture(0);
        if (!videoCapture.isOpened()) throw new CvException("Webcam could not be found");

        // Track the framerate
        int framerate = new FramerateTracker(videoCapture).getFramerate();

        // Set width and height TODO width/ height von webcam auslesen
        videoCapture.set(3, 1280);
        videoCapture.set(4, 720);
        // Set the framerate
        videoCapture.set(5, framerate);

        // Create a loop, that repeats "process" at the same speed as the framerate of the webcam
        timer = Executors.newSingleThreadScheduledExecutor();
        timer.scheduleAtFixedRate(this::process, 0, 1000 / framerate, TimeUnit.MILLISECONDS);
    }

    private void process() {
        Mat frame = new Mat();
        if (model.getLoadedMat() == null) {
            videoCapture.read(frame);
            Imgproc.cvtColor(frame, frame, Imgproc.COLOR_BGR2HSV);
        }
        else frame = model.getLoadedMat().clone();

        model.setProcessedMat(frame);

        // Get the position of the stickers
        List<Point> centers = cubeBoundarys(frame);

        // If no or less than 8 stickers were found, show the unprocessed image
        if (centers == null || centers.size() < 8) {
            model.updateImageView();
            return;
        }

        // Get the boundaries of the cube
        RotatedRect boundingRect = Imgproc.minAreaRect(new MatOfPoint2f(centers.toArray(new Point[0])));

        // Check if the boundingRect is a square. If not, show the unprocessed image
        if (!boundingRectIsSquare(boundingRect)) {
            model.updateImageView();
            return;
        }

        // Get a 3x3 grid (scanpoints) based on the bounding rectangle
        Point[][] scanpoints = gridBasedOnRect(boundingRect);

        // Get the colors at the scanpoints
        int[][] colorMatrix = colorsAtScanpoints(scanpoints, frame);

        // TODO Logo kann Farbe von weißem Mittelstein abfälschen

        // Check if the scanned color matrix is already stored. If not, store it in the list differentColorMatrices
        if (isNewCubeSide(colorMatrix)) {
            scannedCubeSides.add(colorMatrix);
            // Save the found colors as .txt, and the frame as .jpg
            if (model.isDebug()) {
                Output output = new Output();
                output.printSchemes(scannedCubeSides);
                output.printImage(frame.clone(), String.valueOf(scannedCubeSides.size()));
                System.out.println(scannedCubeSides.size() + " SIDES SCANNED");
            }
        }

        // If all 6 sides were scanned, stop the loop
        if (scannedCubeSides.size() == 6) {
            logoCorrection();
            timer.shutdown();
            videoCapture.release();

            // Build the cube with the given color faces
            ColorScheme colorScheme = new ColorScheme(scannedCubeSides);
            buildCube(colorScheme);
        }

        // Draw the found contours in the unprocessed image
        Mat contourMat = frame.clone();
        if (model.isDebug()) {
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    Imgproc.circle(contourMat, scanpoints[x][y], 5, new Scalar(0, 0, 255), 10);
                }
            }
        }
        // Show processedMat
        model.setProcessedMat(contourMat);
        model.updateImageView();
    }

    private List<Point> cubeBoundarys(Mat frame) {
        // Convert hsv to gray
        List<Mat> splittedMat = new ArrayList<>();
        Core.split(frame, splittedMat);
        // Get the value
        Mat processedMat = splittedMat.get(2);

        // Add gaussian blur
        Imgproc.GaussianBlur(processedMat, processedMat, new Size(2 * model.getBlurThreshold() + 1, 2 * model.getBlurThreshold() + 1), 0);

        // Add Canny
        Imgproc.Canny(processedMat, processedMat, model.getCannyThreshold1(), model.getCannyThreshold2());

        // Make the lines thicker
        Mat dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2 * model.getDilateKernel() + 1, 2 * model.getDilateKernel() + 1));
        Imgproc.dilate(processedMat, processedMat, dilateKernel);

        // Find contours
        List<MatOfPoint> foundContours = new ArrayList<>();
        Imgproc.findContours(processedMat, foundContours, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        // Approximate the shape of the contours
        List<MatOfPoint2f> approximations = new ArrayList<>();
        for (int i = 0; i < foundContours.size(); i++) {
            approximations.add(new MatOfPoint2f());
            Imgproc.approxPolyDP(new MatOfPoint2f(foundContours.get(i).toArray()), approximations.get(i), 0.1 * Imgproc.arcLength(new MatOfPoint2f(foundContours.get(i).toArray()), true), true);
        }

        // Find the cube squares and their centers
        List<Point> centers = new ArrayList<>();
        List<MatOfPoint2f> cubeSquares = new ArrayList<>();
        for (MatOfPoint2f approximation : approximations) {
            // Proceed with the approximations that are squares
            if (!isSquare(approximation)) continue;
            // Save the found squares
            cubeSquares.add(approximation);
            // Get the centers
            Moments moments = Imgproc.moments(approximation);
            if (moments.m00 != 0) centers.add(new Point(moments.m10 / moments.m00, moments.m01 / moments.m00));
        }

        //TODO Bedinung bei der alle squares ungefähr gleich groß sein müssen

        // If nothing was found
        if (cubeSquares.isEmpty()) return null;

        // Remove overlapping squares TODO centerThreshold abhängig von der Quadratgröße machen -> immer das kleinere Quadrat nehmen
        int centerThreshold = 20;
        for (int i = 0; i < cubeSquares.size(); i++) {
            for (int c = 0; c < cubeSquares.size(); c++) {
                if (c == i) continue;
                if (centers.get(c).x < centers.get(i).x - centerThreshold || centers.get(c).x > centers.get(i).x + centerThreshold) continue;
                if (centers.get(c).y < centers.get(i).y - centerThreshold || centers.get(c).y > centers.get(i).y + centerThreshold) continue;
                centers.remove(c);
                cubeSquares.remove(c);
            }
        }
        return centers;
    }

    /**
     * Test whether the given approximations form a square
     * that is not too big or too small.
     * @param cornerMat The matrix, with the found approximations
     * @return true if the approximation is a square
     */
    private boolean isSquare(MatOfPoint2f cornerMat) {
        // All approximations that don't have four corners get filtered out
        if (cornerMat.rows() != 4) return false;

        // Sort the corners based on their location
        Point[] corners = sortCorners(cornerMat.toArray());

        // Calculate the length of all four sides
        double distanceTop = distanceBetweenTwoPoints(corners[0], corners[1]);
        double distanceRight = distanceBetweenTwoPoints(corners[1], corners[2]);
        double distanceBottom = distanceBetweenTwoPoints(corners[2], corners[3]);
        double distanceLeft = distanceBetweenTwoPoints(corners[3], corners[0]);
        double[] distances = new double[] {distanceTop, distanceRight, distanceBottom, distanceLeft};

        // Calculate the threshold for the rectangle
        double maxDistance = getMax(distances);
        double minDistance = maxDistance * model.getSideLengthThreshold();

        // If any side is much smaller than the given threshold or is generally very short or long, return false
        for (double distance : distances) {
            // Check for great length differences
            if (distance < minDistance) {
                return false;
            }
            // Check for sides that are shorter than 2% of the image width
            if (distance < model.getProcessedMat().width() * 0.02)
                return false;
            // Check for sides that are longer than 15% of the image width
            if (distance > model.getProcessedMat().width() * 0.15)
                return false;
        }

        // The angles of all four corners must not be outside the threshold
        double minAngle = 90 - model.getAngleThreshold();
        double maxAngle = 90 + model.getAngleThreshold();

        double angleTopLeft = getAngle(corners[3], corners[1], corners[0]);
        if (angleTopLeft < minAngle || angleTopLeft > maxAngle)
            return false;

        double angleTopRight = getAngle(corners[0], corners[2], corners[1]);
        if (angleTopRight < minAngle || angleTopRight > maxAngle)
            return false;

        double angleBottomRight = getAngle(corners[0], corners[2], corners[3]);
        if (angleBottomRight < minAngle || angleBottomRight > maxAngle)
            return false;

        double angleBottomLeft = getAngle(corners[3], corners[1], corners[2]);
        if (angleBottomLeft < minAngle || angleBottomLeft > maxAngle)
            return false;

        // The rectangle must not be rotated further than the threshold
        double farLeft = getMin(new double[] {corners[0].x, corners[1].x, corners[2].x, corners[3].x});
        double farRight = getMax(new double[] {corners[0].x, corners[1].x, corners[2].x, corners[3].x});
        double farUp = getMin(new double[] {corners[0].y, corners[1].y, corners[2].y, corners[3].y});
        Point boundingRectTopLeft = new Point(farLeft, farUp);
        Point boundingRectTopRight = new Point(farRight, farUp);


        if (corners[0].y > corners[1].y) {
            double angleRight = getAngle(corners[0], boundingRectTopLeft, corners[1]);
            if (angleRight > model.getRotationThreshold())
                return false;
        }

        else {
            double angleLeft = getAngle(corners[1], boundingRectTopRight, corners[0]);
            if (angleLeft > model.getRotationThreshold())
                return false;
        }

        return true;
    }

    private void buildCube(ColorScheme colorScheme) {
        List<int[]> possibleFirstThings = new ArrayList<>();
        List<int[]> possibleSecondThings = new ArrayList<>();
        List<int[]> possibleThirdThings = new ArrayList<>();
        List<int[]> possibleFourthThings = new ArrayList<>();

        if (!colorsExistsNineTimes(colorScheme)) {
            System.err.println("WRONG COLOR SCHEME");
            return;
        }

        // Seiten, die oben an die weiße Seite passen
        for (int sideIndex = 1; sideIndex < 5; sideIndex++)
            for (int edgeIndex = 0; edgeIndex < 4; edgeIndex++) {

                int[] edge0 = colorScheme.getEdge(0, 0);
                int[] edge1 = colorScheme.getEdge(sideIndex, edgeIndex);

                if (edgesCouldBeNeighbours(edge0, edge1))
                    possibleFirstThings.add(new int[]{sideIndex, edgeIndex});
            }

        // Seiten, die an den Partner von weiß oben und rechts an die weiße Seite passen
        for (int i = 0; i < possibleFirstThings.size(); i++) {

            int[] edge0 = colorScheme.getEdge(possibleFirstThings.get(i)[0], nextEdgeCounterClockWise(possibleFirstThings.get(i)[1]));
            int[] edge1 = colorScheme.getEdge(0, 1);

            for (int sideIndex = 1; sideIndex < 5; sideIndex++) {
                if (sideIndex == possibleFirstThings.get(i)[0]) continue;

                for (int edgeIndex = 0; edgeIndex < 4; edgeIndex++) {
                    int[] edge2 = colorScheme.getEdge(sideIndex, edgeIndex);
                    int[] edge3 = colorScheme.getEdge(sideIndex, nextEdgeClockWise(edgeIndex));

                    if (edgesCouldBeNeighbours(edge0, edge3) && edgesCouldBeNeighbours(edge1, edge2)) {
                        possibleSecondThings.add(new int[] {possibleFirstThings.get(i)[0], possibleFirstThings.get(i)[1], sideIndex, edgeIndex});
                    }
                }
            }
        }

        for (int i = 0; i < possibleSecondThings.size(); i++) {

            int[] edge0 = colorScheme.getEdge(possibleSecondThings.get(i)[2], nextEdgeCounterClockWise(possibleSecondThings.get(i)[3]));
            int[] edge1 = colorScheme.getEdge(0, 2);

            // Alle Seiten bis auf Weiß, Gelb, die erste und die zweite Seite
            for (int sideIndex = 1; sideIndex < 5; sideIndex++) {
                if (sideIndex == possibleSecondThings.get(i)[0] || sideIndex == possibleSecondThings.get(i)[2]) continue;

                for (int edgeIndex = 0; edgeIndex < 4; edgeIndex++) {
                    int[] edge2 = colorScheme.getEdge(sideIndex, edgeIndex);
                    int[] edge3 = colorScheme.getEdge(sideIndex, nextEdgeClockWise(edgeIndex));

                    if (edgesCouldBeNeighbours(edge0, edge3) && edgesCouldBeNeighbours(edge1, edge2)) {
                        possibleThirdThings.add(new int[] {possibleSecondThings.get(i)[0], possibleSecondThings.get(i)[1], possibleSecondThings.get(i)[2], possibleSecondThings.get(i)[3], sideIndex, edgeIndex});
                    }
                }
            }
        }

        // Fourth
        for (int i = 0; i < possibleThirdThings.size(); i++) {

            int[] edge0 = colorScheme.getEdge(possibleThirdThings.get(i)[4], nextEdgeCounterClockWise(possibleThirdThings.get(i)[5]));
            int[] edge1 = colorScheme.getEdge(0, 3);
            int[] edge5 = colorScheme.getEdge(possibleThirdThings.get(i)[0], nextEdgeClockWise(possibleThirdThings.get(i)[1]));

            // Alle Seiten bis auf Weiß, Gelb, die erste, die zweite und die dritte Seite
            for (int sideIndex = 1; sideIndex < 5; sideIndex++) {
                if (sideIndex == possibleThirdThings.get(i)[0] || sideIndex == possibleThirdThings.get(i)[2]|| sideIndex == possibleThirdThings.get(i)[4]) continue;

                for (int edgeIndex = 0; edgeIndex < 4; edgeIndex++) {
                    int[] edge2 = colorScheme.getEdge(sideIndex, edgeIndex);
                    int[] edge3 = colorScheme.getEdge(sideIndex, nextEdgeClockWise(edgeIndex));
                    int[] edge4 = colorScheme.getEdge(sideIndex, nextEdgeCounterClockWise(edgeIndex));

                    if (edgesCouldBeNeighbours(edge0, edge3) && edgesCouldBeNeighbours(edge1, edge2) && edgesCouldBeNeighbours(edge5, edge4)) {
                        possibleFourthThings.add(new int[] {possibleThirdThings.get(i)[0], possibleThirdThings.get(i)[1], possibleThirdThings.get(i)[2], possibleThirdThings.get(i)[3], possibleThirdThings.get(i)[4], possibleThirdThings.get(i)[5], sideIndex, edgeIndex});
                    }
                }
            }
        }

        // Last side
        for (int[] possibleFourthThing : possibleFourthThings) {
            for (int j = 0; j < 4; j++) {
                for (int i = 0; i < 4; i++) {
                    int[] edge0 = colorScheme.getEdge(possibleFourthThing[i * 2], nextOppositeEdge(possibleFourthThing[i * 2 + 1]));
                    int edge1EdgeIndex = i;
                    for (int r = j; r > 0; r--)
                        edge1EdgeIndex = nextEdgeClockWise(edge1EdgeIndex);
                    int[] edge1 = colorScheme.getEdge(5, edge1EdgeIndex);
                    if (!edgesCouldBeNeighbours(edge0, edge1)) continue;
                    if (i == 3) System.out.println("COMBINATION FOUND");
                }
            }
        }

        System.out.println("First round: " + possibleFirstThings.size());
        int counter = 0;
        int[] sameComb = new int[] {0, 0};
        for (int[] possibleFourthThing : possibleFourthThings) {
            int side = possibleFourthThing[0];
            int edge = possibleFourthThing[1];
            if (side != sameComb[0] || edge != sameComb[1]) counter++;
            sameComb[0] = side;
            sameComb[1] = edge;

        }
        System.out.println("Fourth round: " + counter);

        for (int[] possibleFourthThing : possibleFourthThings) {
            System.out.println(Arrays.toString(possibleFourthThing));
        }
    }

    /**
     * Tests if the edges could be neighbours.
     * The rules are:
     *  - No single cube part can have the same color more than once
     *  - No cube part can have colors tha are supposed to be on the
     *    opposite side of the cube (white-yellow, blue-green, red-orange)
     * @param edge0 The first edge, containing two corner and one edge pieces (3x1)
     * @param edge1 The second edge, containing two corner and one edge pieces (3x1)
     * @return True if the given 3x1 edges are possible neighbours
     */
    private boolean edgesCouldBeNeighbours(int[] edge0, int[] edge1) {
        for (int i = 0; i < 3; i++)
            if (edge0[i] == edge1[2 - i] || edge0[i] + edge1[2 - i] == 5)
                return false;
        return true;
    }

    /**
     * Returns the edge that is located next to the given edge index
     * @param input The index of the starting edge
     * @return The index of the next edge counter clock wise
     */
    private int nextEdgeCounterClockWise(int input) {
        if (input > 0) return input - 1;
        else return 3;
    }

    /**
     * Returns the edge that is located next to the given edge index
     * @param input The index of the starting edge
     * @return The index of the next edge clock wise
     */
    private int nextEdgeClockWise(int input) {
        if (input < 3) return input + 1;
        else return 0;
    }

    /**
     * Returns the edge that is located at the opposite edge
     * @param input The index of the starting edge
     * @return The index of the edge at the opposite side
     */
    private int nextOppositeEdge(int input) {
        if (input <= 1) return input + 2;
        else return input - 2;
    }

    private boolean colorsExistsNineTimes(ColorScheme colorScheme) {
        int[] colorCounter = new int[] {0, 0, 0, 0, 0, 0};
        for (int i = 0; i < 6; i++) {
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    switch(colorScheme.get(i)[x][y]) {
                        case 0:
                            colorCounter[0]++;
                            break;
                        case 1:
                            colorCounter[1]++;
                            break;
                        case 2:
                            colorCounter[2]++;
                            break;
                        case 3:
                            colorCounter[3]++;
                            break;
                        case 4:
                            colorCounter[4]++;
                            break;
                        case 5:
                            colorCounter[5]++;
                            break;
                    }
                }
            }
        }
        for (int i = 0; i < 6; i++) {
            if (colorCounter[i] != 9) return false;
        }
        return true;
    }

    private void logoCorrection() {
        // TODO Wenn zwei Mitten gleich sind -> nur weitermachen wenn Weiß fehlt > Farbe identifizieren > Mitte mit weniger Sättigung als Weiß deklarieren
        List<Integer> doubleColorIndexes = new ArrayList<>();
        List<Integer> centerColors = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int color = scannedCubeSides.get(i)[1][1];
            for (int j = 0; j < centerColors.size(); j++) {
                if (centerColors.get(j) == color) {
                    doubleColorIndexes.add(i);
                    doubleColorIndexes.add(j);
                }
        }
            centerColors.add(color);
        }
        if (doubleColorIndexes.size() == 0) return; // Keine Korrektur nötig
        if (doubleColorIndexes.size() > 2) return; // Error. Zu viele Center doppelt. Cube neu einscannen
        if (centerColors.contains(0)) return; // Error. Weiß ist vorhanden. Cube neu einscannen

        // Ab hier ist klar, dass das Logo das Ergebnis abgefälscht hat
        // TODO Doppelte Farbwerte vergleichen. Farbe mit geringerer Sättigung ist Weiß
        double sat0 = centerColorValues.get(doubleColorIndexes.get(0))[1];
        double sat1 = centerColorValues.get(doubleColorIndexes.get(1))[1];

        System.out.println("Doppelte Mittelstein Index " + doubleColorIndexes.get(0) + doubleColorIndexes.get(1));
        System.out.println("Doppelte Mittelstein Farbwerte " + sat0 + ", " + sat1);

        int whiteIndex;
        if (sat0 < sat1) whiteIndex = doubleColorIndexes.get(0);
        else whiteIndex = doubleColorIndexes.get(1);

        int[][] singleCubeSide = scannedCubeSides.get(whiteIndex);
        singleCubeSide[1][1] = 0;
        scannedCubeSides.set(whiteIndex, singleCubeSide);

        System.out.println("Der Mittelstein von der Seite " + whiteIndex + " wurde zu Weiß geändert :)");
    }

    /**
     * Returns the angle at the point anglePoint for the triangle
     * formed by the three given points.
     * @param point0 The first point
     * @param point1 The second point
     * @param anglePoint The point at which the angle is calculated
     * @return The angle in degrees
     */
    private double getAngle(Point point0, Point point1, Point anglePoint) {

        // Get the distance between the points (Side x -> opposite side of point x)
        double side0 = distanceBetweenTwoPoints(anglePoint, point1);
        double side1 = distanceBetweenTwoPoints(point0, anglePoint);
        double side2 = distanceBetweenTwoPoints(point0, point1);

        // Calculate the cosine angle at anglePoint
        double cosAngle = (Math.pow(side0, 2) + Math.pow(side1, 2) - Math.pow(side2, 2)) / (2 * side0 * side1);

        // Limit the angle to 0 and 180
        if (cosAngle > 1) cosAngle = 1;
        else if (cosAngle < -1) cosAngle = -1;

        // Acos
        cosAngle = Math.acos(cosAngle);

        // Change to degrees
        cosAngle = Math.toDegrees(cosAngle);

        return cosAngle;
    }

    /**
     * Sorts the points so that they have the following order:
     * -> topLeft: 0, topRight: 1, bottomRight: 2, bottomLeft: 3
     * @param corners The points that are to be checked
     * @return The sorted points in an array
     */
    private Point[] sortCorners(Point[] corners) {

        List<Point> results = new ArrayList<>();

        // Get the min and max values for x and y
        double[] xValues = new double[] {corners[0].x, corners[1].x, corners[2].x, corners[3].x};
        double[] yValues = new double[] {corners[0].y, corners[1].y, corners[2].y, corners[3].y};
        double minX = getMin(xValues);
        double maxX = getMax(xValues);
        double minY = getMin(yValues);
        double maxY = getMax(yValues);

        // Top left
        results.add(getBoundingCorner(corners, results, new Point(minX, minY)));

        // Top right
        results.add(getBoundingCorner(corners, results, new Point(maxX, minY)));

        // Bootom Right
        results.add(getBoundingCorner(corners, results, new Point(maxX, maxY)));

        // Bootom Left
        results.add(getBoundingCorner(corners, results, new Point(minX, maxY)));

        return results.toArray(new Point[4]);
    }

    private Point getBoundingCorner(Point[] corners, List<Point> results, Point processCorner) {
        Point outputCorner = new Point();
        double topRightDistance = -1;
        for (Point corner : corners) {
            if (results.contains(corner)) continue;
            double distance = distanceBetweenTwoPoints(processCorner, corner);
            if (topRightDistance == -1 || distance < topRightDistance) {
                outputCorner = corner;
                topRightDistance = distance;
            }
        }
        return outputCorner;
    }

    private boolean boundingRectIsSquare(RotatedRect rotatedRect) {
        double thresholdPercentage = 20;
        // Checks if the width is outside of the threshold
        if (rotatedRect.size.width < rotatedRect.size.height * (100 - thresholdPercentage) / 100) return false;
        if (rotatedRect.size.width > rotatedRect.size.height * (100 + thresholdPercentage) / 100) return false;

        // If the rectangle is rotated further than allowed
        if (rotatedRect.angle < -90 || rotatedRect.angle > 0) return false;
        if (rotatedRect.angle > -60 && rotatedRect.angle < -30) return false;
        return true;
    }

    private Point centerBetweenTwoPoints(Point point0, Point point1) {
        double x, y;
        x = (point0.x + point1.x) / 2;
        y = (point0.y + point1.y) / 2;

        return new Point(x, y);
    }

    /**
     * Calculates the mean color from a rectangle with the size scanAreaSize and the center,
     * given by the two dimensional array scanpoints.
     * @param scanpoints A 3x3 array of points, where the colors should be scanned
     * @param frame The mat where the colors should be read from
     * @return The calculated colors, stored as a two dimensional int array:
     * 0 = white,
     * 1 = green,
     * 2 = red,
     * 3 = orange,
     * 4 = blue,
     * 5 = yellow
     */
    private int[][] colorsAtScanpoints(Point[][] scanpoints, Mat frame) {
        double scanAreaHalf = model.getScanAreaSize() / 2;
        int[][] colors = new int[3][3];
        double hue, sat = 0, val = 0;
        double[] readColor;
        double unitVectorX, unitVectorY;

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                unitVectorX = 0;
                unitVectorY = 0;

                // The start and endpoints, where the colors should get read from
                int startX = (int)Math.round(scanpoints[x][y].x - scanAreaHalf);
                int startY = (int)Math.round(scanpoints[x][y].y - scanAreaHalf);
                int endX = (int)Math.round(scanpoints[x][y].x + scanAreaHalf);
                int endY = (int)Math.round(scanpoints[x][y].y + scanAreaHalf);

                // Read all colors inside the scanRect
                for (int row = startY; row < endY; row++) {
                    for (int col = startX; col < endX; col++) {
                        // Read the Color from a pixel in the given rectangle
                        readColor = frame.get(row, col);
                        // Convert the hue into unit vectors
                        unitVectorX += Math.cos(Math.toRadians(readColor[0]*2));
                        unitVectorY += Math.sin(Math.toRadians(readColor[0]*2));
                        sat += readColor[1];
                        val += readColor[2];
                    }
                }
                // Get the mean of both unit vectors
                unitVectorX /= Math.pow(model.getScanAreaSize(), 2);
                unitVectorY /= Math.pow(model.getScanAreaSize(), 2);
                // Convert the calculated unit vector to an angle that can be used as a hue value
                hue = Math.toDegrees(Math.atan2(unitVectorY, unitVectorX));
                // Because the hue value is a circle, negative values should be added with 360°
                if (hue < 0) hue += 360;
                // Normalise the hue to the Open Cv range uses hue values between 0 - 179
                hue /= 2;
                sat /= Math.pow(model.getScanAreaSize(), 2);
                val /= Math.pow(model.getScanAreaSize(), 2);

                if (x == 1 && y == 1) {
                    centerColorValues.add(new double[] {hue, sat, val});
                }

                // TODO Weiß wird bei schlechten Lichtverhältnissen nicht verlässlich erkannt/ Relativ teuer-------------------------------------------------
                if (!(hue > 20 && hue < 70) && sat < 102 && val > 100) colors[x][y] = 0; // white
                else if (hue < 5) colors[x][y] = 2; // red
                else if (hue < 20) colors[x][y] = 3; // orange
                else if (hue < 45 || hue < 60 && sat < 155) colors[x][y] = 5; // yellow
                else if (hue < 90) colors[x][y] = 1; // green
                else if (hue < 140) colors[x][y] = 4; // blue
                else if (hue <= 180) colors[x][y] = 2; // red
            }
        }
        return colors;
    }

    private boolean isNewCubeSide(int[][] matrix) {
        if (scannedCubeSides.size() == 0) return true;
        int sameValuesCounter;
        for (int[][] scannedCubeSide : scannedCubeSides) {
            for (int rotation = 0; rotation < 4; rotation++) {
                sameValuesCounter = 0;
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < 3; x++) {
                        // Test if a color of the given matrix exists in the same place at scannedCubeSide
                        if (matrix[x][y] == scannedCubeSide[x][y]) {
                            // Raise the counter up
                            sameValuesCounter++;
                        }
                    }
                }
                // Sides are the same if 8 or more colors are at the same place
                if (sameValuesCounter >= 8) return false;
                if (rotation < 3) scannedCubeSide = rotateClockwise(scannedCubeSide);
            }
        }
        return true;
    }

    private int[][] rotateClockwise(int[][] array) {
        int[][] newArray = new int[3][3];
        for (int y = 0; y < 3; ++y)
            for (int x = 0; x < 3; ++x)
                newArray[y][x] = array[2 - x][y];
        return newArray;
    }

    private Point[][] gridBasedOnRect(RotatedRect boundingRect) {
        Point[][] scanpoints = new Point[3][3];
        Point[] boundingRectCorners = new Point[4];

        boundingRect.points(boundingRectCorners);

        // Creates a grid based on the centers of the stickers on the cube
        scanpoints[0][0] = boundingRectCorners[1];
        scanpoints[1][0] = centerBetweenTwoPoints(boundingRectCorners[1], boundingRectCorners[2]);
        scanpoints[2][0] = boundingRectCorners[2];
        scanpoints[0][1] = centerBetweenTwoPoints(boundingRectCorners[1], boundingRectCorners[0]);
        scanpoints[1][1] = centerBetweenTwoPoints(boundingRectCorners[1], boundingRectCorners[3]);
        scanpoints[2][1] = centerBetweenTwoPoints(boundingRectCorners[2], boundingRectCorners[3]);
        scanpoints[0][2] = boundingRectCorners[0];
        scanpoints[1][2] = centerBetweenTwoPoints(boundingRectCorners[0], boundingRectCorners[3]);
        scanpoints[2][2] = boundingRectCorners[3];
        return scanpoints;
    }

    /**Get both outer centers
     * Calculate the euclidean distance between two points
     * @param point0 The first point
     * @param point1 The second point
     * @return The euclidean distance between the two given points
     */
    private double distanceBetweenTwoPoints(Point point0, Point point1) {
        double xDiff = point0.x - point1.x;
        double yDiff = point0.y - point1.y;
        return Math.sqrt(Math.pow(xDiff, 2) + Math.pow(yDiff, 2));
    }

    /**
     * Calculate the smallest double value in the given array
     * @param values An array of doubles to be examined
     * @return The smallest double found
     */
    private double getMin(double[] values) {
        double min = values[0];
        for (int i = 1; i < values.length; i++) {
            min = Math.min(min, values[i]);
        }
        return min;
    }

    /**
     * Calculate the biggest double value in the given array
     * @param values An array of doubles to be examined
     * @return The biggest double found
     */
    private double getMax(double[] values) {
        double max = values[0];
        for (int i = 1; i < values.length; i++) {
            max = Math.max(max, values[i]);
        }
        return max;
    }

    @Override
    public void update(Observable o, Object arg) {
        switch ((String)arg) {
            case "processImage":
                process();
                break;
            case "startWebcamStream":
                startWebcamStream();
                break;
        }
    }

    public void initModel(CubeScanFramelessModel model) {
        this.model = model;
    }
}
