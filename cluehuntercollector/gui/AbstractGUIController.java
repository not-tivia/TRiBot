package scripts.cluehuntercollector.gui;

import javafx.fxml.Initializable;
import scripts.cluehuntercollector.gui.GUI;


/**
 * @author Laniax
 */

public abstract class AbstractGUIController implements Initializable {


    private GUI gui = null;

    public void setGUI(GUI gui) {
        this.gui = gui;
    }

    public GUI getGUI() {
        return this.gui;
    }
}