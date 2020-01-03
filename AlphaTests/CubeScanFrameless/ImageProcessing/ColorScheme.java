package AlphaTests.CubeScanFrameless.ImageProcessing;

import java.util.ArrayList;
import java.util.List;

public class ColorScheme {

    List<int[][]> sortedScheme;

    public ColorScheme(List<int[][]> unsortedScheme) {
        this.sortedScheme = sortSides(unsortedScheme);
    }

    /**
     * Gives the 3x1 edge of the given side
     * @param sideIndex side index between 0 and 5
     * @param edgeIndex edge index between 0 and 3
     * @return The edge, consisting of three colors
     */
    public int[] getEdge(int sideIndex, int edgeIndex) {
        switch (edgeIndex) {
            case 0:
                return new int[] {
                        sortedScheme.get(sideIndex)[0][0],
                        sortedScheme.get(sideIndex)[1][0],
                        sortedScheme.get(sideIndex)[2][0]
                };
            case 1:
                return new int[] {
                        sortedScheme.get(sideIndex)[2][0],
                        sortedScheme.get(sideIndex)[2][1],
                        sortedScheme.get(sideIndex)[2][2]
                };
            case 2:
                return new int[] {
                        sortedScheme.get(sideIndex)[2][2],
                        sortedScheme.get(sideIndex)[1][2],
                        sortedScheme.get(sideIndex)[0][2]
                };
            case 3:
                return new int[] {
                        sortedScheme.get(sideIndex)[0][2],
                        sortedScheme.get(sideIndex)[0][1],
                        sortedScheme.get(sideIndex)[0][0]
                };
        }
        return null;
    }

    private List<int[][]> sortSides(List<int[][]> unsorted) {
        List<int[][]> sorted = new ArrayList<>();
        for (int i = 0; i < 6; i++)
            sorted.add(new int[3][3]);
        for (int[][] singleSide : unsorted) sorted.set(singleSide[1][1], singleSide);
        return sorted;
    }

    private List<int[][]> mirrorYellowSide(List<int[][]> scheme) {
        int[][] yellowSide = scheme.get(5);
        int[][] mirroredSide = new int[3][3];
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (x == 0) mirroredSide[x][y] = yellowSide[2][y];
                else if (x == 1) mirroredSide[x][y] = yellowSide[x][y];
                else mirroredSide[x][y] = yellowSide[0][y];
            }
        }
        scheme.set(5, mirroredSide);
        return scheme;
    }

    public int[][] get(int index) {
        return sortedScheme.get(index);
    }
}