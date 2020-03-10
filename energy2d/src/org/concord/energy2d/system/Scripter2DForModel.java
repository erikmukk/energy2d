package org.concord.energy2d.system;

import static java.util.regex.Pattern.compile;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import org.concord.energy2d.event.ScriptEvent;
import org.concord.energy2d.event.ScriptListener;
import org.concord.energy2d.math.Blob2D;
import org.concord.energy2d.math.Polygon2D;
import org.concord.energy2d.model.*;
import org.concord.energy2d.util.ColorFill;
import org.concord.energy2d.util.MiscUtil;
import org.concord.energy2d.util.Scripter;
import org.concord.energy2d.util.XmlCharacterDecoder;
import org.concord.energy2d.view.Picture;
import org.concord.energy2d.view.TextBox;
import org.concord.energy2d.view.View2D;

/**
 * @author Charles Xie
 *
 */
class Scripter2DForModel extends Scripter {

    private final static Pattern MOVE_SUN = compile("(^(?i)movesun\\b){1}");
    private final static Pattern MESSAGE = compile("(^(?i)message\\b){1}");
    private final static Pattern RUNSTEPS = compile("(^(?i)runsteps\\b){1}");
    private final static Pattern PART = compile("(^(?i)part\\b){1}");
    private final static Pattern THERMOMETER = compile("(^(?i)thermometer\\b){1}");
    private final static Pattern ANEMOMETER = compile("(^(?i)anemometer\\b){1}");
    private final static Pattern HEAT_FLUX_SENSOR = compile("(^(?i)heatfluxsensor\\b){1}");
    private final static Pattern BOUNDARY = compile("(^(?i)boundary\\b){1}");
    private final static Pattern PART_FIELD = compile("^%?((?i)part){1}(\\[){1}" + REGEX_WHITESPACE + "*\\w+" + REGEX_WHITESPACE + "*(\\]){1}\\.");
    private final static Pattern PARTICLE_FIELD = compile("^%?((?i)particle){1}(\\[){1}" + REGEX_WHITESPACE + "*\\w+" + REGEX_WHITESPACE + "*(\\]){1}\\.");
    private final static Pattern PARTICLE_FEEDER_FIELD = compile("^%?((?i)particlefeeder){1}(\\[){1}" + REGEX_WHITESPACE + "*\\w+" + REGEX_WHITESPACE + "*(\\]){1}\\.");
    private final static Pattern FAN_FIELD = compile("^%?((?i)fan){1}(\\[){1}" + REGEX_WHITESPACE + "*\\w+" + REGEX_WHITESPACE + "*(\\]){1}\\.");
    private final static Pattern TASK_FIELD = compile("^%?((?i)task){1}(\\[){1}" + REGEX_WHITESPACE + "*\\w+" + REGEX_WHITESPACE + "*(\\]){1}\\.");
    private final static Pattern ANEMOMETER_FIELD = compile("^%?((?i)anemometer){1}(\\[){1}" + REGEX_WHITESPACE + "*\\w+" + REGEX_WHITESPACE + "*(\\]){1}\\.");
    private final static Pattern THERMOMETER_FIELD = compile("^%?((?i)thermometer){1}(\\[){1}" + REGEX_WHITESPACE + "*\\w+" + REGEX_WHITESPACE + "*(\\]){1}\\.");
    private final static Pattern HEAT_FLUX_SENSOR_FIELD = compile("^%?((?i)heatfluxsensor){1}(\\[){1}" + REGEX_WHITESPACE + "*\\w+" + REGEX_WHITESPACE + "*(\\]){1}\\.");
    private final static Pattern IMAGE_FIELD = compile("^%?((?i)image){1}(\\[){1}" + REGEX_WHITESPACE + "*" + REGEX_NONNEGATIVE_DECIMAL + REGEX_WHITESPACE + "*(\\]){1}\\.");
    private final static Pattern TEXT_FIELD = compile("^%?((?i)text){1}(\\[){1}" + REGEX_WHITESPACE + "*" + REGEX_NONNEGATIVE_DECIMAL + REGEX_WHITESPACE + "*(\\]){1}\\.");
    private final static Pattern BOUNDARY_FIELD = compile("^%?((?i)boundary){1}(\\[){1}" + REGEX_WHITESPACE + "*\\w+" + REGEX_WHITESPACE + "*(\\]){1}\\.");

    private Model2D model;
    private List<ScriptListener> listenerList;
    private boolean arrayUpdateRequested, temperatureInitializationRequested;
    private boolean notifySaveReminder = true;

    Scripter2DForModel(Model2D m2d) {
        this.model = m2d;
    }

    boolean shouldNotifySaveReminder() {
        return notifySaveReminder;
    }

    void addScriptListener(ScriptListener listener) {
        if (listenerList == null)
            listenerList = new CopyOnWriteArrayList<ScriptListener>();
        if (!listenerList.contains(listener))
            listenerList.add(listener);
    }

    void removeScriptListener(ScriptListener listener) {
        if (listenerList == null)
            return;
        listenerList.remove(listener);
    }

    void removeAllScriptListeners() {
        if (listenerList == null)
            return;
        listenerList.clear();
    }

    private void notifyScriptListener(ScriptEvent e) {
        if (listenerList == null)
            return;
        synchronized (listenerList) {
            for (ScriptListener l : listenerList) {
                l.outputScriptResult(e);
            }
        }
    }

    private void showException(String command, Exception e) {
        e.printStackTrace();
        out(ScriptEvent.FAILED, "Error in \'" + command + "\':" + e.getMessage());
    }

    private void showError(String command, String message) {
        out(ScriptEvent.FAILED, "Error in \'" + command + "\':" + message);
    }

    private void out(byte status, String description) {
        if (status == ScriptEvent.FAILED) {
            notifyScriptListener(new ScriptEvent(model, status, "Aborted: " + description));
        } else {
            notifyScriptListener(new ScriptEvent(model, status, description));
        }
    }

    public void executeScript(String script) {
        notifySaveReminder = true;
        super.executeScript(script);
        if (arrayUpdateRequested) {
            model.refreshPowerArray();
            model.refreshTemperatureBoundaryArray();
            model.refreshMaterialPropertyArrays();
            if (model.isRadiative()) {
                model.generateViewFactorMesh();
            }
            arrayUpdateRequested = false;
        }
        if (temperatureInitializationRequested) {
            model.setInitialTemperature();
            temperatureInitializationRequested = false;
        }
    }

    protected void evalCommand(String ci) {
        Matcher matcher = MOVE_SUN.matcher(ci);
        if (matcher.find()) {
            String s = ci.substring(matcher.end()).trim();
            if (s == null || s.equals("")) {
                model.moveSun(6, 18);
            } else {
                String[] s2 = s.split(REGEX_WHITESPACE);
                if (s2.length == 2) {
                    float sunrise = Float.valueOf(s2[0]);
                    float sunset = Float.valueOf(s2[1]);
                    if (sunrise > 0 && sunrise < 24 && sunset > 0 && sunset < 24) {
                        model.moveSun(sunrise, sunset);
                    } else {
                        showError(ci, "Sunrise and sunset times must be 0-24.");
                    }
                } else {
                    showError(ci, "Expect two parameters for dawn and dusk time.");
                }
            }
            notifySaveReminder = false;
            return;
        }
        out(ScriptEvent.HARMLESS, "Command not recognized");
    }

    private void setPartField(String str1, String str2, String str3) {
    }

    private void setParticleField(String str1, String str2, String str3) {
        Particle particle = null;
        int lb = str1.indexOf("[");
        int rb = str1.indexOf("]");
        String s = str1.substring(lb + 1, rb).trim();
        float z = Float.NaN;
        try {
            z = Float.parseFloat(s);
        } catch (Exception e) {
            z = Float.NaN;
        }
        particle = Float.isNaN(z) ? model.getParticle(s) : model.getParticle((int) Math.round(z));
        if (particle == null) {
            showError(str1, "Particle " + s + " not found");
            return;
        }
        s = str2.toLowerCase().intern();
        if (str3.startsWith("#")) {
            try {
                z = Integer.parseInt(str3.substring(1), 16);
            } catch (Exception e) {
                showException(str3, e);
                return;
            }
        } else if (str3.startsWith("0X") || str3.startsWith("0x")) {
            try {
                z = Integer.parseInt(str3.substring(2), 16);
            } catch (Exception e) {
                showException(str3, e);
                return;
            }
        } else if (str3.equalsIgnoreCase("true")) {
            z = 1;
        } else if (str3.equalsIgnoreCase("false")) {
            z = 0;
        } else {
            if (s == "label") {
                particle.setLabel(str3);
                return;
            }
            if (s == "uid") {
                particle.setUid(str3);
                return;
            }
            try {
                z = Float.parseFloat(str3);
            } catch (Exception e) {
                showException(str3, e);
                return;
            }
        }
        if (s == "rx") {
            particle.setRx(z);
        } else if (s == "ry") {
            particle.setRy(z);
        } else if (s == "vx") {
            particle.setVx(z);
        } else if (s == "vy") {
            particle.setVy(z);
        } else if (s == "mass") {
            particle.setMass(z);
        } else if (s == "radius") {
            particle.setRadius(z);
        } else if (s == "temperature") {
            particle.setTemperature(z);
        } else if (s == "color") {
            particle.setFillPattern(new ColorFill(new Color((int) z)));
        } else if (s == "movable") {
            particle.setMovable(z > 0);
        } else if (s == "draggable") {
            particle.setDraggable(z > 0);
        }
    }

    private void setParticleFeederField(String str1, String str2, String str3) {
        ParticleFeeder feeder = null;
        int lb = str1.indexOf("[");
        int rb = str1.indexOf("]");
        String s = str1.substring(lb + 1, rb).trim();
        float z = Float.NaN;
        try {
            z = Float.parseFloat(s);
        } catch (Exception e) {
            z = Float.NaN;
        }
        feeder = Float.isNaN(z) ? model.getParticleFeeder(s) : model.getParticleFeeder((int) Math.round(z));
        if (feeder == null) {
            showError(str1, "Particle feeder " + s + " not found");
            return;
        }
        s = str2.toLowerCase().intern();
        if (str3.startsWith("#")) {
            try {
                z = Integer.parseInt(str3.substring(1), 16);
            } catch (Exception e) {
                showException(str3, e);
                return;
            }
        } else if (str3.startsWith("0X") || str3.startsWith("0x")) {
            try {
                z = Integer.parseInt(str3.substring(2), 16);
            } catch (Exception e) {
                showException(str3, e);
                return;
            }
        } else if (str3.equalsIgnoreCase("true")) {
            z = 1;
        } else if (str3.equalsIgnoreCase("false")) {
            z = 0;
        } else {
            if (s == "label") {
                feeder.setLabel(str3);
                return;
            }
            if (s == "uid") {
                feeder.setUid(str3);
                return;
            }
            try {
                z = Float.parseFloat(str3);
            } catch (Exception e) {
                showException(str3, e);
                return;
            }
        }
        if (s == "x") {
            feeder.setX(z);
        } else if (s == "y") {
            feeder.setY(z);
        } else if (s == "period") {
            feeder.setPeriod(z);
        } else if (s == "maximum") {
            feeder.setMaximum(Math.round(z));
        } else if (s == "color") {
            feeder.setColor(new Color((int) z));
        } else if (s == "velocitycolor") {
            feeder.setVelocityColor(new Color((int) z));
        } else if (s == "draggable") {
            feeder.setDraggable(z > 0);
        }
    }

    private void setFanField(String str1, String str2, String str3) {
        Fan fan = null;
        int lb = str1.indexOf("[");
        int rb = str1.indexOf("]");
        String s = str1.substring(lb + 1, rb).trim();
        float z = Float.NaN;
        try {
            z = Float.parseFloat(s);
        } catch (Exception e) {
            z = Float.NaN;
        }
        fan = Float.isNaN(z) ? model.getFan(s) : model.getFan((int) Math.round(z));
        if (fan == null) {
            showError(str1, "Fan " + s + " not found");
            return;
        }
        s = str2.toLowerCase().intern();
        if (str3.equalsIgnoreCase("true")) {
            z = 1;
        } else if (str3.equalsIgnoreCase("false")) {
            z = 0;
        } else {
            if (s == "label") {
                fan.setLabel(str3);
                return;
            }
            if (s == "uid") {
                fan.setUid(str3);
                return;
            }
            try {
                z = Float.parseFloat(str3);
            } catch (Exception e) {
                showException(str3, e);
                return;
            }
        }
        if (s == "x") {
            Shape shape = fan.getShape();
            if (shape instanceof Rectangle2D.Float)
                ((Rectangle2D.Float) shape).x = z;
            arrayUpdateRequested = true;
        } else if (s == "y") {
            Shape shape = fan.getShape();
            if (shape instanceof Rectangle2D.Float)
                ((Rectangle2D.Float) shape).y = model.getLy() - z;
            arrayUpdateRequested = true;
        } else if (s == "width") {
            Shape shape = fan.getShape();
            if (shape instanceof Rectangle2D.Float)
                ((Rectangle2D.Float) shape).width = z;
            arrayUpdateRequested = true;
        } else if (s == "height") {
            Shape shape = fan.getShape();
            if (shape instanceof Rectangle2D.Float)
                ((Rectangle2D.Float) shape).height = z;
            arrayUpdateRequested = true;
        } else if (s == "speed") {
            fan.setSpeed(fan.getAngle() == 0 ? z : -z);
            arrayUpdateRequested = true;
        } else if (s == "angle") {
            fan.setAngle(z);
            arrayUpdateRequested = true;
        } else if (s == "draggable") {
            fan.setDraggable(z > 0);
        }
    }

    private void setBoundaryField(String str1, String str2, String str3) {
        int lb = str1.indexOf("[");
        int rb = str1.indexOf("]");
        String side = str1.substring(lb + 1, rb);
        if (!side.equalsIgnoreCase("LEFT") && !side.equalsIgnoreCase("RIGHT") && !side.equalsIgnoreCase("LOWER") && !side.equalsIgnoreCase("UPPER")) {
            showError(str1 + str2 + str3, "Side parameter of boundary not recognized: must be LEFT, RIGHT, UPPER, or LOWER.");
        }
        float z = 0;
        try {
            z = Float.parseFloat(str3);
        } catch (Exception e) {
            showException(str3, e);
            return;
        }
        String s = str2.toLowerCase().intern();
        if (s == "temperature") {
            ThermalBoundary b = model.getThermalBoundary();
            if (b instanceof DirichletThermalBoundary) {
                DirichletThermalBoundary db = (DirichletThermalBoundary) b;
                if (side.equalsIgnoreCase("LEFT")) {
                    db.setTemperatureAtBorder(Boundary.LEFT, z);
                } else if (side.equalsIgnoreCase("RIGHT")) {
                    db.setTemperatureAtBorder(Boundary.RIGHT, z);
                } else if (side.equalsIgnoreCase("LOWER")) {
                    db.setTemperatureAtBorder(Boundary.LOWER, z);
                } else if (side.equalsIgnoreCase("UPPER")) {
                    db.setTemperatureAtBorder(Boundary.UPPER, z);
                }
            }
        } else if (s == "flux") {
            ThermalBoundary b = model.getThermalBoundary();
            if (b instanceof NeumannThermalBoundary) {
                NeumannThermalBoundary db = (NeumannThermalBoundary) b;
                if (side.equalsIgnoreCase("LEFT")) {
                    db.setFluxAtBorder(Boundary.LEFT, z);
                } else if (side.equalsIgnoreCase("RIGHT")) {
                    db.setFluxAtBorder(Boundary.RIGHT, z);
                } else if (side.equalsIgnoreCase("LOWER")) {
                    db.setFluxAtBorder(Boundary.LOWER, z);
                } else if (side.equalsIgnoreCase("UPPER")) {
                    db.setFluxAtBorder(Boundary.UPPER, z);
                }
            }
        } else if (s == "mass_flow_type") {
            MassBoundary b = model.getMassBoundary();
            if (b instanceof SimpleMassBoundary) {
                byte z2 = (byte) z;
                if (z2 == MassBoundary.REFLECTIVE || z2 == MassBoundary.THROUGH) {
                    SimpleMassBoundary db = (SimpleMassBoundary) b;
                    if (side.equalsIgnoreCase("LEFT")) {
                        db.setFlowTypeAtBorder(Boundary.LEFT, z2);
                    } else if (side.equalsIgnoreCase("RIGHT")) {
                        db.setFlowTypeAtBorder(Boundary.RIGHT, z2);
                    } else if (side.equalsIgnoreCase("LOWER")) {
                        db.setFlowTypeAtBorder(Boundary.LOWER, z2);
                    } else if (side.equalsIgnoreCase("UPPER")) {
                        db.setFlowTypeAtBorder(Boundary.UPPER, z2);
                    }
                } else {
                    showError(str1 + str2 + str3, "Property value not recognized.");
                }
            }
        }
    }

    private void setThermometerField(String str1, String str2, String str3) {
        Thermometer sensor = null;
        int lb = str1.indexOf("[");
        int rb = str1.indexOf("]");
        String s = str1.substring(lb + 1, rb).trim();
        float z = Float.NaN;
        try {
            z = Float.parseFloat(s);
        } catch (Exception e) {
            z = Float.NaN;
        }
        sensor = Float.isNaN(z) ? model.getThermometer(s) : model.getThermometer((int) Math.round(z));
        if (sensor == null) {
            showError(str1, "Thermometer " + s + " not found");
            return;
        }
        s = str2.toLowerCase().intern();
        if (s == "label") {
            sensor.setLabel(str3);
            return;
        }
        if (s == "uid") {
            sensor.setUid(str3);
            return;
        }
        try {
            z = Float.parseFloat(str3);
        } catch (Exception e) {
            showException(str3, e);
            return;
        }
        if (s == "x") {
            sensor.setX(z);
        } else if (s == "y") {
            sensor.setY(convertVerticalCoordinate(z));
        }
    }

    private void setAnemometerField(String str1, String str2, String str3) {
        Anemometer sensor = null;
        int lb = str1.indexOf("[");
        int rb = str1.indexOf("]");
        String s = str1.substring(lb + 1, rb).trim();
        float z = Float.NaN;
        try {
            z = Float.parseFloat(s);
        } catch (Exception e) {
            z = Float.NaN;
        }
        sensor = Float.isNaN(z) ? model.getAnemometer(s) : model.getAnemometer((int) Math.round(z));
        if (sensor == null) {
            showError(str1, "Anemometer " + s + " not found");
            return;
        }
        s = str2.toLowerCase().intern();
        if (s == "label") {
            sensor.setLabel(str3);
            return;
        }
        if (s == "uid") {
            sensor.setUid(str3);
            return;
        }
        try {
            z = Float.parseFloat(str3);
        } catch (Exception e) {
            showException(str3, e);
            return;
        }
        if (s == "x") {
            sensor.setX(z);
        } else if (s == "y") {
            sensor.setY(convertVerticalCoordinate(z));
        }
    }

    private void setHeatFluxSensorField(String str1, String str2, String str3) {
        HeatFluxSensor sensor = null;
        int lb = str1.indexOf("[");
        int rb = str1.indexOf("]");
        String s = str1.substring(lb + 1, rb).trim();
        float z = Float.NaN;
        try {
            z = Float.parseFloat(s);
        } catch (Exception e) {
            z = Float.NaN;
        }
        sensor = Float.isNaN(z) ? model.getHeatFluxSensor(s) : model.getHeatFluxSensor(((int) Math.round(z)));
        if (sensor == null) {
            showError(str1, "Heat flux sensor " + s + " not found");
            return;
        }
        s = str2.toLowerCase().intern();
        if (s == "label") {
            sensor.setLabel(str3);
            return;
        }
        if (s == "uid") {
            sensor.setUid(str3);
            return;
        }
        try {
            z = Float.parseFloat(str3);
        } catch (Exception e) {
            showException(str3, e);
            return;
        }
        if (s == "x") {
            sensor.setX(z);
        } else if (s == "y") {
            sensor.setY(convertVerticalCoordinate(z));
        } else if (s == "angle") {
            sensor.setAngle((float) Math.toRadians(z));
        }
    }

    private void setTaskField(String str1, String str2, String str3) {
        int lb = str1.indexOf("[");
        int rb = str1.indexOf("]");
        String s = str1.substring(lb + 1, rb).trim();
        Task task = model.taskManager.getTaskByUid(s);
        if (task == null) {
            showError(str1, "Task " + s + " not found");
            return;
        }
        s = str2.toLowerCase().intern();
        if (s == "enabled") {
            task.setEnabled("true".equalsIgnoreCase(str3));
            return;
        } else if (s == "interval") {
            int z = 0;
            try {
                z = Integer.parseInt(str3);
            } catch (Exception e) {
                showException(str3, e);
                return;
            }
            if (z > 0)
                task.setInterval(z);
            return;
        }
    }

    private void setTextField(String str1, String str2, String str3) {
    }

    private void setImageField(String str1, String str2, String str3) {

    }

    /*
     * in the rendering system, the origin is at the upper-left corner. In the user's coordinate system, the origin needs to be changed to the lower-left corner.
     */
    private float convertVerticalCoordinate(float y) {
        return model.getLy() - y;
    }

}
