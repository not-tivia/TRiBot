package scripts.cluehuntercollector.gui;

import com.allatori.annotations.DoNotRename;
import com.google.gson.Gson;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.MenuItem;
import org.tribot.api.General;
import org.tribot.util.Util;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import static java.nio.file.Files.readString;
import static java.nio.file.Files.writeString;
import static scripts.cluehuntercollector.data.Constants.foodChoiceArray;
import static scripts.cluehuntercollector.data.Vars.*;


@DoNotRename
public class Controller extends AbstractGUIController {

    private final File directory = new File(Util.getWorkingDirectory() + File.separator + "adamhackz" + File.separator + "cluehuntercollector" + File.separator + "saves");

    @FXML
    @DoNotRename
    private CheckBox helmcheckbox;

    @FXML
    @DoNotRename
    private CheckBox bootsandglovescheckbox;

    @FXML
    @DoNotRename
    private CheckBox garbcheckbox;

    @FXML
    @DoNotRename
    private CheckBox cloakcheckbox;

    @FXML
    @DoNotRename
    private CheckBox trouserscheckbox;

    @FXML
    @DoNotRename
    private CheckBox staminacheckbox;

    @FXML
    @DoNotRename
    private CheckBox foodcheckbox;

    @FXML
    @DoNotRename
    private ChoiceBox<String> foodchoice;

    @FXML
    @DoNotRename
    private MenuItem menusave;

    @FXML
    @DoNotRename
    private MenuItem menunew;


    @FXML
    @DoNotRename
    private Button startbutton;


    @FXML
    @DoNotRename
    private MenuItem menuopen;
    private GUISettings settings = new GUISettings();

    @FXML
    @DoNotRename
    public void menunewpressed() {

    }

    @FXML
    @DoNotRename
    public void menuopenpressed() {
        //String saveFilePath = directory + File.separator + "last.json";
        if (directory.exists()) {
            General.println("Opening our last save from: " + directory);
            try {
                // Read the settings
                String s = readString(new File(directory, "last.json").toPath());
                settings = new Gson().fromJson(s, GUISettings.class);
                // Gear
                bootsandglovescheckbox.setSelected(settings.isBootsandgloves());
                helmcheckbox.setSelected(settings.isHelm());
                garbcheckbox.setSelected(settings.isGarb());
                cloakcheckbox.setSelected(settings.isCloak());
                trouserscheckbox.setSelected(settings.isTrousers());

                if (settings.isFood()) {
                    foodcheckbox.setSelected(true);
                    foodchoice.setValue(settings.isFoodName());


                    //foodchoice.setItems( settings.getFoodName((String) foodchoice.getValue()));

                } else {
                    foodcheckbox.setSelected(false);
                }

                staminacheckbox.setSelected(settings.isStamina());


            } catch (Exception e) {
                e.printStackTrace();
            }
        }


    }

    @FXML
    @DoNotRename
    public void menusavepressed() {
        if (!directory.exists())
            directory.mkdirs();
        try {

            if (trouserscheckbox.isSelected()) {
                settings.setTrousers(true);
            }
            if (bootsandglovescheckbox.isSelected()) {
                settings.setBootsandgloves(true);
            }
            if (helmcheckbox.isSelected()) {
                settings.setHelm(true);
            }
            if (garbcheckbox.isSelected()) {
                settings.setGarb(true);
            }
            if (cloakcheckbox.isSelected()) {
                settings.setCloak(true);
            }
            if (foodcheckbox.isSelected()) {
                settings.setFood(true);
                settings.setFoodName(foodchoice.getValue());
            }
            if (staminacheckbox.isSelected()) {
                settings.setStamina(true);
            }


            // Write the settings
            String s = new Gson().toJson(settings);
            writeString(new File(directory, "last.json").toPath(), s);
            General.println("Settings saved successfully to: " + directory);
        } catch (IOException e) {
            General.println("Error attempting to save settings.");
            e.printStackTrace();
        }
    }

    @FXML
    @DoNotRename
    public void startscriptpressed() {

        //Stamina
        if (staminacheckbox.isSelected()) {
            General.println("We are using stamina potions.");
            usingStamina = true;
            requiredInventorySpace = requiredInventorySpace + 1;
        }

        //Food


        if (foodcheckbox.isSelected()) {
            usingFood = true;
            foodCount = General.random(4, 10);
            requiredInventorySpace = requiredInventorySpace + foodCount;
            General.println("We are using " + foodCount + " " + foodchoice.getValue());
            foodName = foodchoice.getValue();
        } else {
            General.println("We are not using food.");
        }

        // Armour
        if (cloakcheckbox.isSelected()) {
            requiredInventorySpace = requiredInventorySpace + 1;
            tasks.add("cloak");
            General.println("Clue hunter cloak added to task list.");
        }
        if (garbcheckbox.isSelected()) {
            requiredInventorySpace = requiredInventorySpace + 1;
            tasks.add("garb");
            General.println("Clue hunter garb added to task list.");
        }
        if (bootsandglovescheckbox.isSelected()) {
            requiredInventorySpace = requiredInventorySpace + 2;
            tasks.add("glovesandboots");
            General.println("Clue hunter boots and gloves added to task list.");
        }
        if (trouserscheckbox.isSelected()) {
            requiredInventorySpace = requiredInventorySpace + 1;
            tasks.add("trousers");
            General.println("Clue hunter trousers added to task list.");
        }
        if (helmcheckbox.isSelected()) {
            requiredInventorySpace = requiredInventorySpace + 4;
            tasks.add("helm");
            General.println("Clue hunter helm added to task list.");
            collectingHelm = true;
        }

        General.println("We will need " + requiredInventorySpace + " free spaces." + "Tasks: " + tasks.toString());


        this.getGUI().close();
    }


    @Override
    public void initialize(URL arg0, ResourceBundle arg1) {
        //ToDo
        foodchoice.setItems(FXCollections.observableArrayList(foodChoiceArray));
        foodchoice.disableProperty().bind(foodcheckbox.selectedProperty().not());

    }


}
