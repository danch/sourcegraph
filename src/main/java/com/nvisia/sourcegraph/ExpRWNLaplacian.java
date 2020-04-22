package com.nvisia.sourcegraph;

import com.nvisia.sourcegraph.graph.Node;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;

public class ExpRWNLaplacian {
    static double adjacency[][] = new double[][] {
            {0.0, 1.0, 1.0, 0.0, 0.0},
            {0.0, 0.0, 0.0, 1.0, 0.0},
            {0.0, 0.0, 0.0, 1.0, 1.0},
            {0.0, 0.0, 1.0, 0.0, 0.0},
            {0.0, 0.0, 1.0, 0.0, 0.0}
    };
    static double nodeDegree[] = new double[] {2.0, 1.0, 2.0, 0.0, 1.0};
    static double nodeDegreeInv[] = new double[] {0.5, 1.0, 0.5, 0.0, 1.0};
    static double startLocation[] = new double[] {1.0, 0.0, 0.0, 0.0, 0.0};

    public static void main(String[] args) {
        var adjMatrix = MatrixUtils.createRealMatrix(adjacency);
        var degreeMatrix = MatrixUtils.createRealDiagonalMatrix(nodeDegree);
        var degreeInvert = MatrixUtils.createRealDiagonalMatrix(nodeDegreeInv);
        var initialLocation = new ArrayRealVector(startLocation);

        var laplacian = degreeMatrix.subtract(adjMatrix);
        dumpMatrix("Laplacian", laplacian);

        var rwnLaplac = degreeInvert.multiply(laplacian);
        dumpMatrix("RW Normalized", rwnLaplac);

        var eigenDecomp = new EigenDecomposition(rwnLaplac);
        var eigenValues = eigenDecomp.getRealEigenvalues();
        for (int i=0;i<eigenValues.length;i++) {
            var eigenVector = eigenDecomp.getEigenvector(i);
            System.out.println(Double.toString(eigenValues[i]));
        }

        dumpMatrix("Initial Value", MatrixUtils.createRowRealMatrix(initialLocation.toArray()));
        var afterOne = rwnLaplac.preMultiply(initialLocation).subtract(initialLocation);
        dumpMatrix("Probability After 1 step", MatrixUtils.createRowRealMatrix(afterOne.toArray()));

        //var afterTwo = (rwnLaplac.multiply(rwnLaplac)).preMultiply(initialLocation);
        var afterTwo = rwnLaplac.preMultiply(afterOne).subtract(afterOne);
        dumpMatrix("Probability After 2 steps", MatrixUtils.createRowRealMatrix(afterTwo.toArray()));

        //var afterThree = (rwnLaplac.multiply(rwnLaplac).multiply(rwnLaplac)).preMultiply(initialLocation);
        var afterThree = rwnLaplac.preMultiply(afterTwo).subtract(afterTwo);
        dumpMatrix("Probability After 3 steps", MatrixUtils.createRowRealMatrix(afterThree.toArray()));
    }

    private static void dumpMatrix(String title, RealMatrix m) {
        System.out.println(title);
        var rows = m.getRowDimension();
        var cols = m.getColumnDimension();
        for (int i=0;i<rows;i++) {
            var row = m.getRow(i);
            for (int j=0;j<cols;j++) {
                System.out.print(Double.toString(row[j])+"\t");
            }
            System.out.print("\n");
        }
        System.out.print("\n");
    }
}
