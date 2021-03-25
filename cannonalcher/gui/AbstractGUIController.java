package scripts.cannonalcher.gui;

import javafx.fxml.Initializable;



/**
 * @author Laniax
 */

public abstract class AbstractGUIController implements Initializable {


    private GUI gui = null;

    public GUI getGUI() {
        return this.gui;
    }

    public void setGUI(GUI gui) {
        this.gui = gui;
    }
}