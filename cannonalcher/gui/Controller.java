package scripts.cannonalcher.gui;

import com.allatori.annotations.DoNotRename;
import com.google.gson.Gson;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.MenuItem;
import org.tribot.api.General;
import org.tribot.api2007.Login;
import org.tribot.api2007.Player;
import org.tribot.util.Util;
import scripts.cannonClicker.Data.Vars;
import scripts.cannonalcher.data.Constants;
import scripts.cannonalcher.data.Variables;


import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import static java.nio.file.Files.readString;
import static java.nio.file.Files.writeString;
import static scripts.cannonalcher.data.Variables.*;


@DoNotRename
public class Controller extends AbstractGUIController {

    private final File directory = new File(Util.getWorkingDirectory() + File.separator + "adamhackz" + File.separator + "cluehuntercollector" + File.separator + "saves");


    @Override
    public void initialize(URL arg0, ResourceBundle arg1) {
        //ToDo
        presetLocationChoiceBox.setItems(FXCollections.observableArrayList(Constants.Locations.values()));

    }

    @FXML
    @DoNotRename
    private Button startScriptButton;

    @FXML
    @DoNotRename
    private Button startScriptTwo;

    @FXML
    @DoNotRename
    private Button setCustomCannonButton;

    @FXML
    @DoNotRename
    private Button setCustomSafeButton;

    @FXML
    @DoNotRename
    private ChoiceBox presetLocationChoiceBox;

    @FXML
    @DoNotRename
    private CheckBox useGraniteBallsCheckBox;

    @FXML
    @DoNotRename
    private CheckBox lootItemsCheckBox;


    @FXML @DoNotRename
    private CheckBox castAlchCheckBox;

    @FXML @DoNotRename
    private CheckBox telegrabLootCheckBox;

    //Both Start script buttons are just copy and pasted clones of eachother
    @FXML @DoNotRename
    public void startScriptPressed(){
        if (useGraniteBallsCheckBox.isSelected()){
            cannonballName = "Granite cannonball";
        } else {
            cannonballName = "Cannonball";
        }
        if (lootItemsCheckBox.isSelected()){
            lootingEnabled = true;
        } else {
            lootingEnabled = false;
        }
        if (castAlchCheckBox.isSelected()){
            alchingEnabled = true;
        } else {
            alchingEnabled = false;
        }
        if (telegrabLootCheckBox.isSelected()){
            telegrabbingLoot = true;
        } else {
            telegrabbingLoot = false;
        }
        this.getGUI().close();
    }

    @FXML @DoNotRename
    public void startScriptTwoPressed(){
        if (useGraniteBallsCheckBox.isSelected()){
            cannonballName = "Granite cannonball";
        } else {
            cannonballName = "Cannonball";
        }
        if (lootItemsCheckBox.isSelected()){
            lootingEnabled = true;
        } else {
            lootingEnabled = false;
        }
        if (castAlchCheckBox.isSelected()){
            alchingEnabled = true;
        } else {
            alchingEnabled = false;
        }
        if (telegrabLootCheckBox.isSelected()){
            telegrabbingLoot = true;
        } else {
            telegrabbingLoot = false;
        }
        this.getGUI().close();
    }



    //Custom tab
    @FXML @DoNotRename
    public void setCustomCannonPressed(){
        if (inGame()){
            if (Player.getRSPlayer()!=null && Player.getPosition()!=null) {
                customCannonTile = Player.getPosition();
            }
        } else {
            General.println("We need to be logged in to set a custom tile.");
        }

    }
    @FXML @DoNotRename
    public void setCustomSafePressed(){
        if (inGame()){
            if (Player.getRSPlayer()!=null && Player.getPosition()!=null) {
                customSafeTile = Player.getPosition();
            }
        } else {
            General.println("We need to be logged in to set a custom tile.");
        }
    }


    private GUISettings settings = new GUISettings();



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


                if (settings.isFood()) {


                    //foodchoice.setItems( settings.getFoodName((String) foodchoice.getValue()));

                } else {

                }


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

            // Write the settings
            String s = new Gson().toJson(settings);
            writeString(new File(directory, "last.json").toPath(), s);
            General.println("Settings saved successfully to: " + directory);
        } catch (IOException e) {
            General.println("Error attempting to save settings.");
            e.printStackTrace();
        }
    }




    private boolean inGame() {
        return Login.getLoginState() == Login.STATE.INGAME;
    }



}
