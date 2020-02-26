package org.concord.energy2d.system;

import com.apple.eawt.Application;
import com.apple.eawt.ApplicationAdapter;
import com.apple.eawt.ApplicationEvent;
import org.concord.energy2d.event.ManipulationEvent;
import org.concord.energy2d.event.ManipulationListener;
import org.concord.energy2d.model.*;
import org.concord.energy2d.util.MiscUtil;
import org.concord.energy2d.view.Picture;
import org.concord.energy2d.view.TextBox;
import org.concord.energy2d.view.View2D;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.swing.Timer;
import javax.swing.*;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;


/**
 * Deploy as an app (energy2d.jar) or an applet (energy2d-applet.jar). The applet has no menu bar and tool bar and doesn't include /models and /resources.
 * (Since applets are no longer supported by most browsers, deployment as an applet is no long a viable option.)
 *
 * @author Charles Xie
 */
public class System2DHeadless implements ManipulationListener {

    final static String BRAND_NAME = "Energy2D V3.0.4";

    Model2D model;
    View2D view;
    TaskManager taskManager;
    Task repaint, measure, control;
    private Scripter2D scripter;
    private ExecutorService threadService;
    private static boolean isApplet = true;

    private SAXParser saxParser;
    private DefaultHandler saxHandler;
    private XmlEncoder encoder;
    private File currentFile;
    private URL currentURL;
    private String currentModel;
    private boolean saved = true;
    private String nextSim, prevSim;

    Runnable clickRun, clickStop, clickReset, clickReload;
    private JButton buttonRun, buttonStop, buttonReset, buttonReload;
    private JLabel statusLabel;
    JToggleButton snapToggleButton;
    private ToolBarListener toolBarListener;
    private List<PropertyChangeListener> propertyChangeListeners;
    JFrame owner;
    private static System2DHeadless box;

    public View2D getView() {
        return view;
    }

    public Model2D getModel() {
        return model;
    }

    public void setOwner(JFrame owner) {
        this.owner = owner;
    }


    void executeInThreadService(Runnable r) {
        if (threadService == null)
            threadService = Executors.newFixedThreadPool(1);
        threadService.execute(r);
    }

    public void run() {
//        view.setRunToggle(true);
        executeInThreadService(() -> model.run());
    }

    public void stop() {
        model.stop();
//        view.setRunToggle(false);
    }

    public void reset() {
        model.reset();
//        view.reset();
//        view.repaint();
    }


    public void clear() {
        model.clear();
//        view.clear();
//        view.repaint();
    }

    void setSaved(boolean b) {
        saved = b;
        EventQueue.invokeLater(() -> setFrameTitle());
    }

    private void loadStateApp(Reader reader) throws IOException {
        // stop();
        reset();
        clear();
        if (reader == null)
            return;
        try {
            saxParser.parse(new InputSource(reader), saxHandler);
        } catch (SAXException e) {
            e.printStackTrace();
        } finally {
            reader.close();
        }
        EventQueue.invokeLater(() -> {
            if (buttonStop != null)
                callAction(buttonStop);
        });
        setSaved(true);
    }

    private void loadStateApp(InputStream is) throws IOException {
        // stop();
        reset();
        clear();
        loadState(is);
    }

    public void loadState(InputStream is) throws IOException {
        // stop();
        if (is == null)
            return;
        try {
            saxParser.parse(new InputSource(is), saxHandler);
        } catch (SAXException e) {
            e.printStackTrace();
        } finally {
            is.close();
        }
        EventQueue.invokeLater(() -> {
            if (buttonStop != null)
                callAction(buttonStop);
        });
        setSaved(true);
    }

    void loadFile(File file) {
        setReloadButtonEnabled(true);
        if (file == null)
            return;
        try {
            // loadStateApp(new FileInputStream(file)); this call doesn't work on some Mac
            loadStateApp(new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(JOptionPane.getFrameForComponent(view), e.getLocalizedMessage(), "File error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        notifyToolBarListener(new ToolBarEvent(ToolBarEvent.FILE_INPUT, this));
        currentFile = file;
        currentModel = null;
        currentURL = null;
        setFrameTitle();
        view.getUndoManager().die();
    }

    private void loadURL(URL url) throws IOException {
        setReloadButtonEnabled(true);
        if (url == null)
            return;
        if (!askSaveBeforeLoading())
            return;
        loadStateApp(url.openConnection().getInputStream());
        notifyToolBarListener(new ToolBarEvent(ToolBarEvent.FILE_INPUT, this));
        currentURL = url;
        currentFile = null;
        currentModel = null;
        setFrameTitle();
        view.getUndoManager().die();
    }

    void reload() {
        if (currentFile != null) {
            loadFile(currentFile);
            return;
        }
        if (currentURL != null) {
            try {
                loadURL(currentURL);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void setNextSimulation(String nextSim) {
        notifyPropertyChangeListeners("Next Simulation", this.nextSim, nextSim);
        this.nextSim = nextSim;
    }

    public void setPreviousSimulation(String prevSim) {
        notifyPropertyChangeListeners("Prev Simulation", this.prevSim, prevSim);
        this.prevSim = prevSim;
    }

    private void setReloadButtonEnabled(final boolean b) {
        if (buttonReload == null)
            return;
        EventQueue.invokeLater(() -> buttonReload.setEnabled(b));
    }

    int askSaveOption() {
        if (saved || owner == null || currentModel != null || currentURL != null)
            return JOptionPane.NO_OPTION;
        return JOptionPane.showConfirmDialog(owner, "Do you want to save the changes?", "Save", JOptionPane.YES_NO_CANCEL_OPTION);
    }

    boolean askSaveBeforeLoading() {
        if (owner == null) // not an application
            return true;
        switch (askSaveOption()) {
            case JOptionPane.YES_OPTION:
                Action a;
                if (currentFile != null) {
                    a = view.getActionMap().get("Save");
                } else {
                    a = view.getActionMap().get("SaveAs");
                }
                if (a != null)
                    a.actionPerformed(null);
                return true;
            case JOptionPane.NO_OPTION:
                return true;
            default:
                return false;
        }
    }

    String runNativeScript(String script) {
        return null;
    }

    public void manipulationOccured(ManipulationEvent e) {
        Object target = e.getTarget();
        switch (e.getType()) {
            case ManipulationEvent.REPAINT:
                view.repaint();
                break;
            case ManipulationEvent.PROPERTY_CHANGE:
                setSaved(false);
                break;
            case ManipulationEvent.TRANSLATE:
                setSaved(false);
                break;
            case ManipulationEvent.RESIZE:
                setSaved(false);
                break;
            case ManipulationEvent.OBJECT_ADDED:
                setSaved(false);
                break;
            case ManipulationEvent.SENSOR_ADDED:
                setSaved(false);
                break;
            case ManipulationEvent.DELETE:
                if (target instanceof Part)
                    model.removePart((Part) target);
                else if (target instanceof Particle)
                    model.removeParticle((Particle) target);
                else if (target instanceof ParticleFeeder)
                    view.removeParticleFeeder((ParticleFeeder) target);
                else if (target instanceof Anemometer)
                    model.removeAnemometer((Anemometer) target);
                else if (target instanceof Thermometer)
                    model.removeThermometer((Thermometer) target);
                else if (target instanceof HeatFluxSensor)
                    model.removeHeatFluxSensor((HeatFluxSensor) target);
                else if (target instanceof TextBox)
                    view.removeTextBox((TextBox) target);
                else if (target instanceof Picture)
                    view.removePicture((Picture) target);
                else if (target instanceof Cloud)
                    view.removeCloud((Cloud) target);
                else if (target instanceof Tree)
                    view.removeTree((Tree) target);
                else if (target instanceof Fan)
                    view.removeFan((Fan) target);
                else if (target instanceof Heliostat)
                    view.removeHeliostat((Heliostat) target);
                if (view.getSelectedManipulable() == target)
                    view.setSelectedManipulable(null);
                setSaved(false);
                break;
            case ManipulationEvent.RUN:
                if (clickRun != null) {
                    EventQueue.invokeLater(clickRun);
                } else {
                    run();
                }
                break;
            case ManipulationEvent.STOP:
                if (clickStop != null) {
                    EventQueue.invokeLater(clickStop);
                } else {
                    stop();
                }
                break;
            case ManipulationEvent.RESET:
                if (clickReset != null) {
                    EventQueue.invokeLater(clickReset);
                } else {
                    reset();
                }
                break;
            case ManipulationEvent.RELOAD:
                if (clickReload != null) {
                    EventQueue.invokeLater(clickReload);
                } else {
                    reload();
                }
                break;
            case ManipulationEvent.FATAL_ERROR_OCCURRED:
                view.repaint();
                EventQueue.invokeLater(() -> {
                    JOptionPane.showMessageDialog(JOptionPane.getFrameForComponent(view), "<html>The current time steplength is " + model.getTimeStep() + " s.<br>Reduce it in the Properties Window and then reset the simulation.<br>(Usually it should be less than 1 s for convection simulations.)", "Fatal error", JOptionPane.INFORMATION_MESSAGE);
                    Action propertyAction = view.getActionMap().get("Property");
                    if (propertyAction != null)
                        propertyAction.actionPerformed(null);
                });
                break;
            case ManipulationEvent.SUN_SHINE:
                model.setSunny(!model.isSunny());
                model.refreshPowerArray();
                setSaved(false);
                break;
            case ManipulationEvent.SUN_ANGLE_INCREASE:
                float a = model.getSunAngle() + (float) Math.PI / 18;
                model.setSunAngle(Math.min(a, (float) Math.PI));
                model.refreshPowerArray();
                setSaved(false);
                break;
            case ManipulationEvent.SUN_ANGLE_DECREASE:
                a = model.getSunAngle() - (float) Math.PI / 18;
                model.setSunAngle(Math.max(a, 0));
                model.refreshPowerArray();
                setSaved(false);
                break;
        }
        if (target instanceof Part) {
            Part p = (Part) target;
            model.refreshMaterialPropertyArrays();
            model.refreshPowerArray();
            model.refreshTemperatureBoundaryArray();
            if (p.getEmissivity() > 0)
                model.getPhotons().clear();
            if (model.isRadiative())
                model.generateViewFactorMesh();
            setSaved(false);
        } else if (target instanceof Fan) {
            model.refreshMaterialPropertyArrays();
        }
//        view.repaint();
    }

    // JButton.doClick() does not do anything if a button is disabled.
    private static void callAction(JButton button) {
        ActionListener[] a = button.getActionListeners();
        if (a == null || a.length == 0)
            return;
        for (ActionListener x : a)
            x.actionPerformed(null);
    }

    void notifyToolBarListener(ToolBarEvent e) {
        setFrameTitle();
        if (toolBarListener != null)
            toolBarListener.tableBarShouldChange(e);
    }

    private void notifyPropertyChangeListeners(String propertyName, Object oldValue, Object newValue) {
        if (propertyChangeListeners.isEmpty())
            return;
        PropertyChangeEvent e = new PropertyChangeEvent(this, propertyName, oldValue, newValue);
        for (PropertyChangeListener x : propertyChangeListeners)
            x.propertyChange(e);
    }

    void setFrameTitle() {
        if (owner == null)
            return;
        if (currentFile != null) {
            owner.setTitle(BRAND_NAME + ": " + currentFile + (saved ? "" : " *"));
        } else if (currentModel != null) {
            owner.setTitle(BRAND_NAME + ": " + currentModel);
        } else if (currentURL != null) {
            owner.setTitle(BRAND_NAME + ": " + currentURL);
        } else {
            owner.setTitle(BRAND_NAME);
        }
    }
    private static void setupSimulation() {
        Model2D model2D = new Model2D();
        model2D.setTimeStep(10f);
        model2D.loadModel("examples/test-heating-sun-2.e2d");
        model2D.run();

    }

    public static void main(final String[] args) {
        setupSimulation();
    }

    private static void start(final String[] args) {
    }

    private void run2() {
        taskManager.execute();
    }
}