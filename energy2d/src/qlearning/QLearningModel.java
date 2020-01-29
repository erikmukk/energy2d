package qlearning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;

public class QLearningModel {

    //private HashMap<CustomPair<CustomPair<Boolean, Boolean>, CustomPair<Double, Double>>, double[]> qTable;
    private HashMap<Double, double[]> qTable;

    public QLearningModel() {
        this.makeQTable();
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    /*private void makeQTable() {
        ArrayList<Double> allTemps = new ArrayList<>();
        for (double i = -50.0 ; i <= 50.1 ; i += 0.1) {
            allTemps.add(round(i, 1));

        }
        boolean[] booleanArray = new boolean[]{true, false};
        HashMap<CustomPair<CustomPair<Boolean, Boolean>, CustomPair<Double, Double>>, double[]> qTable = new HashMap<>();
        for (Boolean b1 : booleanArray) {
            for (Boolean b2 : booleanArray) {
                for (Double f1 : allTemps) {
                    for (Double f2 : allTemps) {
                        CustomPair<Boolean, Boolean> p1 = new CustomPair<>(b1, b2);
                        CustomPair<Double, Double> p2 = new CustomPair<>(f1, f2);
                        CustomPair<CustomPair<Boolean, Boolean>, CustomPair<Double, Double>> tableKey = new CustomPair<>(p1, p2);
                        double[] qTableRow = new double[]{0, 0, 0};
                        qTable.put(tableKey, qTableRow);
                    }
                }
            }
        }
        this.qTable = qTable;
    }*/

    private void makeQTable() {
        ArrayList<Double> allTemps = new ArrayList<>();
        for (double i = -50.0 ; i <= 50.1 ; i += 0.1) {
            allTemps.add(round(i, 1));

        }
        boolean[] booleanArray = new boolean[]{true, false};
        HashMap<Double, double[]> qTable = new HashMap<>();
        for (Double f2 : allTemps) {
            double[] qTableRow = new double[]{0, 0, 0};
            qTable.put(f2, qTableRow);
        }
        this.qTable = qTable;
    }

    /*public HashMap<CustomPair<CustomPair<Boolean, Boolean>, CustomPair<Double, Double>>, double[]> getqTable() {
        return qTable;
    }*/
    public HashMap<Double, double[]> getqTable() {
        return qTable;
    }

    }
