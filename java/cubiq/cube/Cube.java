package cubiq.cube;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import cubiq.processing.MathUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class Cube {

    private final int cubeLayersCount;
    private Cubie[] cubies;
    private int totalCubies;
    private List<int[][]> colorScheme;
    int ROTATION_SPEED = 1;
    int currentCycle = 0;
    int totalAmount = 0;
    float lastAmount;

    public Cube(int cubeLayersCount, List<int[][]> colorScheme) {
        /*
        for (int i = 0; i < colorScheme.size(); i++) {
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    System.out.print(colorScheme.get(i)[x][y]);
                    if (x < 2) System.out.print(", ");
                }
                if (y < 2) System.out.print("\n");
            }
            if (i < 5) System.out.print("\n\n");
        }
        System.out.println();

         */
        this.cubeLayersCount = cubeLayersCount;
        this.colorScheme = colorScheme;
    }

    public void initCubies(GL3 gl, int[] vaoName, int[] vboName, int[] iboName) {
        if (colorScheme == null) {
        }
        totalCubies = (int)Math.pow(cubeLayersCount, 3);
        cubies = new Cubie[totalCubies];
        // Offset, to center the cube in the scene
        float cubePosOffset = (cubeLayersCount - 1) / 2f;
        for (int x = 0, c = 0; x < cubeLayersCount; x++) {
            for (int y = 0; y < cubeLayersCount; y++) {
                for (int z = 0; z < cubeLayersCount; z++, c++) {
                    cubies[c] = new Cubie(x - cubePosOffset, y - cubePosOffset, z - cubePosOffset);
                    gl.glBindVertexArray(vaoName[c]);
                    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vboName[c]);
                    gl.glBufferData(GL.GL_ARRAY_BUFFER, cubies[c].getVerticesPosColor().length * 4L,
                            FloatBuffer.wrap(cubies[c].getVerticesPosColor()), GL.GL_STATIC_DRAW);
                    gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, iboName[c]);
                    gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, cubies[c].getIndices().length * 4L,
                            IntBuffer.wrap(cubies[c].getIndices()), GL.GL_STATIC_DRAW);
                    gl.glEnableVertexAttribArray(0);
                    gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 6 * Float.BYTES, 0);
                    gl.glEnableVertexAttribArray(1);
                    gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES);
                }
            }
        }
    }

    public void updateVerticesBuffer(GL3 gl, int[] vboName) {
        for (int i = 0; i < totalCubies; i++) {
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vboName[i]);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, cubies[i].getVerticesPosColor().length * 4L,
                    FloatBuffer.wrap(cubies[i].getVerticesPosColor()), GL.GL_STATIC_DRAW);
        }
    }

    private List<int[][]> generateDefaultScheme() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {

            }
        }
        return null;
    }

    public void rotateLayer(String rotation) {
        String layer = rotation.substring(0, 1);
        float amount = (float)(Math.PI/2);
        if (rotation.contains("'"))
            amount *= -1f;
        if (rotation.contains("2"))
            amount *= 2;
        switch (layer) {
            case "U":
                animate(amount, new int[] {1, 1}, new float[] {0, 1, 0});
                break;
            case "D":
                animate(-amount, new int[] {1, -1}, new float[] {0, 1, 0});
                break;
            case "L":
                animate(-amount, new int[] {0, -1}, new float[] {1, 0, 0});
                break;
            case "R":
                animate(amount, new int[] {0, 1}, new float[] {1, 0, 0});
                break;
            case "F":
                animate(amount, new int[] {2, 1}, new float[] {0, 0, 1});
                break;
            case "B":
                animate(-amount, new int[] {2, -1}, new float[] {0, 0, 1});
                break;
        }
    }

    private void animate(float amount, int[] layer, float[] axis) {
        lastAmount = 0;
        currentCycle = 0;
        totalAmount = 0;
        List<Cubie> rotateCubiesList = new ArrayList<>();
        for (int i = 0; i < totalCubies; i++) {
            if (cubies[i].getLocalPosition()[layer[0]] == layer[1])
                rotateCubiesList.add(cubies[i]);
        }

        Timeline timeline = new Timeline();
        timeline.getKeyFrames().add(
                new KeyFrame(new Duration(5), e -> {
                    float frameAmount = MathUtils.easeInOut(currentCycle, 0, amount, 100);
                    for (Cubie cubie: rotateCubiesList) {
                        cubie.rotateAroundAxis(frameAmount - lastAmount, axis);
                    }
                    lastAmount = frameAmount;
                    currentCycle++;
                }));
        timeline.setCycleCount(100);
        timeline.play();
    }

    public float[] getCubieBoundingBox(int index) {
        return cubies[index].getBoundingBox();
    }

    public int[] getCubieIndices(int index) {
        return cubies[index].getIndices();
    }

    public int getCubeLayersCount() {
        return cubeLayersCount;
    }

    public float[] getCubiePosition(int qbIndex) {
        return cubies[qbIndex].getLocalPosition();
    }

    public int getTotalCubies() {
        return totalCubies;
    }
}
