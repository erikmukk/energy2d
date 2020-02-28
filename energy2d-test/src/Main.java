import org.concord.energy2d.model.Model2D;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) throws IOException {
        Model modelRunnable = new Model();
        modelRunnable.loadModel("src/test-heating-sun-2.e2d");

        Model2D model2D = modelRunnable.getModel();
        model2D.stop();
        model2D.setTimeStep(1f);
        model2D.getThermostats().get(0).setDeadband(2);
        model2D.getThermostats().get(0).setSetPoint(20);
        System.out.println(model2D.getThermometers().size());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.execute(modelRunnable);
        int counter = 0;
        while (true) {
            try {
                Thread.sleep(1000);
                counter ++;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (counter > 5) {
                model2D.getThermostats().get(0).getPowerSource().setPower(200);
            }

            System.out.println(model2D.getTime() + " time");
            System.out.println(model2D.getThermostats().get(0).getThermometer().getCurrentData() + " thermometer");
        }
    }
}
