package AlphaBuild.Processing;

import AlphaBuild.Model.Model;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.*;

public class ImageProcessing implements Observer {

    private Model model;

    /**
     * Searches for a cube in a image
     */
    public void findCube() {
        // Get a image form the model and clone it
        Mat image = model.getOriginalImage().clone();

        // Skips the processing if no image was selected
        if (image == null) return;

        // Convert the image from brg to hsv
        Imgproc.cvtColor(image, image, Imgproc.COLOR_BGR2HSV);

        // Split the hvs image into three individual channels (h, s and v) and store them into a array list
        // -> 0 = hue (color); 1 = saturation; 2 = value (brightness)
        List<Mat> splittedMat = new ArrayList<>();
        Core.split(image, splittedMat);

        // Keep the image with the value channel (brightness map = grayscale image)
        Mat greyImg = splittedMat.get(2);
        debugOutput(greyImg, "1_greyscale");

        // Blur the image with the kernel size 15
        Imgproc.GaussianBlur(greyImg, greyImg, new Size(15, 15), 0);

        // Apply the canny algorithm to the image (edge detection)
        Imgproc.Canny(greyImg, greyImg, 5.0, 17.0);
        debugOutput(greyImg, "2_canny");

        // Make the lines thicker
        Mat dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3,3));
        Imgproc.dilate(greyImg, greyImg, dilateKernel);

        // Find all contours in the image and store them into the list contours
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(greyImg, contours, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        // Approximates the shape of the contours with forms
        // ArcLength calculates the length of a contour
        // Closed = true -> keep only the forms that are closed
        List<MatOfPoint2f> approximations = new ArrayList<>();
        for (int i = 0; i < contours.size(); i++) {
            approximations.add(new MatOfPoint2f());
            Imgproc.approxPolyDP(new MatOfPoint2f(contours.get(i).toArray()), approximations.get(i),
                    0.1 * Imgproc.arcLength(new MatOfPoint2f(contours.get(i).toArray()),
                            true), true);
        }

        // Debug output for approximations
        List<MatOfPoint> drawableApproximations = convertMat2fToMat(approximations);
        Mat approximationMat = drawContoursOnMat(drawableApproximations);
        debugOutput(approximationMat, "3_approximations");

        MatOfPoint2f cubeBoundingRect = findCubeBoundingRect(approximations);

        // Draws the found rectangle
        Mat cubeBoundingRectImage = new Mat(1080, 1920, CvType.CV_8UC3);
        Point[] points = cubeBoundingRect.toArray();
        Imgproc.rectangle(cubeBoundingRectImage, points[0], points[2], new Scalar(255, 255, 255), 5);
        debugOutput(cubeBoundingRectImage, "4_cubeBoundingRect");




        Point[][] inPoints = new Point[3][3];
        double kantenlänge = 0;
        for(int index = 0; kantenlänge <= 50; index++){
            kantenlänge = Math.abs(points[0].x - points[index].x);
        }
        double abstand = kantenlänge / 3.0;
        double offset = abstand / 2;
        double y_startwert = points[0].y + offset;

        Mat circleMat = model.getOriginalImage().clone();

        for(int y = 0; y < 3; y++){
            double x_startwert = points[0].x + offset;
            for(int x = 0; x < 3;  x++){
                inPoints[x][y] = new Point(x_startwert + x * abstand, y_startwert + y * abstand);
                Imgproc.circle(circleMat, inPoints[x][y], 1, new Scalar(255, 255, 255), 5);
            }
        }
        debugOutput(circleMat, "5_circles");

        // 0 white;
        // 1 green,
        // 2 red;
        // 3 orange;
        // 4 blue;
        // 5 yellow



        int[][] normalizedColors = new int[3][3];

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                // Get the hsv color from a point in the cube grid
                double[] hsvColor = image.get((int)inPoints[x][y].y, (int)inPoints[x][y].x);

                // Normalize the color to an int value and store it in the array colors
                normalizedColors[x][y] = normalizeColors(hsvColor);
            }
        }

        model.setNormalizedColors(normalizedColors);
        model.callObservers("cubeFound");
    }

    private int normalizeColors(double[] color) {
        double hue = color[0];
        double sat = color[1];
        double val = color[2];

        if (!(hue > 20 && hue < 70) && sat < 102 && val > 100) return 0; // white
        if (hue < 5) return 2; // red
        if (hue < 20) return 3; // orange
        if (hue < 45 || hue < 60 && sat < 155) return 5; // yellow
        if (hue < 90) return 1; // green
        if (hue < 140) return 4; // blue
        if (hue <= 180) return 2; // red
        return -1;
    }

    private MatOfPoint2f findCubeBoundingRect(List<MatOfPoint2f> approximations) {
        double largestEdge = 0;
        MatOfPoint2f cubeBoundingRect = new MatOfPoint2f();

        for (MatOfPoint2f approximation : approximations) {
            if (approximation.rows() != 4) continue;
            Point[] points = approximation.toArray();

            double[] yPositions = {points[0].y, points[1].y, points[2].y, points[3].y};
            Arrays.sort(yPositions);

            double yDifference0 = Math.abs(yPositions[0] - yPositions[1]);
            double yDifference1 = Math.abs(yPositions[2] - yPositions[3]);

            if (yDifference0 > 15 || yDifference1 > 15) continue;

            double height = Math.abs(yPositions[0] - yPositions[3]);
            if (largestEdge < height){
                largestEdge = height;
                cubeBoundingRect = approximation;
            }
        }
        return cubeBoundingRect;
    }

    private Mat drawContoursOnMat(List<MatOfPoint> contours) {
        Mat mat = new Mat(1080, 1920, CvType.CV_8UC3);
        for (int index = 0; index < contours.size(); index++)
            Imgproc.drawContours(mat, contours, index, new Scalar(255, 255, 255), 5);
        return mat;
    }

    private List<MatOfPoint> convertMat2fToMat(List<MatOfPoint2f> matOfPoint2f) {
        List<MatOfPoint> matOfPoint = new ArrayList<>();
        for (MatOfPoint2f approximation : matOfPoint2f) {
            MatOfPoint contour = new MatOfPoint();
            approximation.convertTo(contour, CvType.CV_32S);
            matOfPoint.add(contour);
        }
        return matOfPoint;
    }

    /**
     * Print an debug image
     * @param mat The image to be saved
     * @param name The name of the output
     */
    public void debugOutput(Mat mat, String name) {
        Imgcodecs.imwrite("src/AlphaBuild/Resources/Images/Processed/" + name + ".jpg", mat);
    }

    @Override
    public void update(Observable o, Object arg) {
        switch ((String)arg) {
            case "image loaded":
                findCube();
                break;
        }
    }

    public void initModel(Model model) {
        this.model = model;
    }
}