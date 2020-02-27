import org.xml.sax.InputSource;
import org.concord.energy2d.model.Model2D;
import org.concord.energy2d.system.XmlDecoderHeadlessForModelExport;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class Model implements Runnable {

    Model2D model;
    boolean isRunning;

    public Model() {
        this.model = new Model2D();
    }

    public void loadModel(String modelE2DFileName) throws IOException {
        InputStream is = new FileInputStream(modelE2DFileName);
        DefaultHandler saxHandler = new XmlDecoderHeadlessForModelExport(model);
        try {
            SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
            saxParser.parse(new InputSource(is), saxHandler);
        } catch (SAXException | ParserConfigurationException e) {
            e.printStackTrace();
        }
    }

    public void runModel() {
        this.isRunning = true;
        this.model.run();
    }
    public void stopModel() {
        this.isRunning = false;
    }

    public Model2D getModel() {
        return model;
    }

    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void run() {
        runModel();
    }
}
