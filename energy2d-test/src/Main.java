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
        System.out.println(model2D.getThermometers().size());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.execute(modelRunnable);

        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println(model2D.getTime() + " time");
            System.out.println(model2D.getThermostats().get(0).getThermometer().getCurrentDataInFahrenheit() + " thermometer");
        }
    }
}
