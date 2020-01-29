package qlearning;

import java.util.Random;

public class Environment {

    boolean humanPresence;
    double insideTemp;
    double outsideTemp;
    boolean isHeating;
    public final int DO_NOTHING = 0;
    public final int HEAT = 1;
    public final int STOP_HEATING = 2;
    private final int[] actionSpace = new int[]{HEAT, STOP_HEATING, DO_NOTHING};

    public int[] getActionSpace() {
        return actionSpace;
    }

    public boolean isHumanPresence() {
        return humanPresence;
    }

    public void setHumanPresence(boolean humanPresence) {
        this.humanPresence = humanPresence;
    }

    public double getInsideTemp() {
        return insideTemp;
    }

    public void setInsideTemp(double insideTemp) {
        this.insideTemp = insideTemp;
    }

    public double getOutsideTemp() {
        return outsideTemp;
    }

    public void setOutsideTemp(double outsideTemp) {
        this.outsideTemp = outsideTemp;
    }

    public boolean isHeating() {
        return isHeating;
    }

    public void setHeating(boolean heating) {
        isHeating = heating;
    }

    public Environment(double outsideTemp, double insideTemp) {
        this.humanPresence = getRandomBoolean();
        this.isHeating = getRandomBoolean();
        this.insideTemp = insideTemp;
        this.outsideTemp = outsideTemp;
    }

    private static boolean getRandomBoolean() {
        return new Random().nextBoolean();
    }

    public void takeAction(int choice) {
        if (choice == DO_NOTHING) {
        } else if (choice == HEAT) {
            this.isHeating = true;
        } else if (choice == STOP_HEATING) {
            this.isHeating = false;
        }
    }

    @Override
    public String toString() {
        return String.format("Is heating: %b." +
                        " Human present: %b." +
                        " Inside temp: %f. " +
                        " Outside temp: %f.",
                this.isHeating,
                this.humanPresence,
                this.insideTemp,
                this.outsideTemp);
    }
}
