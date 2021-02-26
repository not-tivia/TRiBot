package scripts.cluehuntercollector.gui;

import com.allatori.annotations.DoNotRename;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;

import javafx.scene.control.ChoiceBox;
import org.tribot.api.General;

import scripts.api.dax_api.shared.jsonSimple.JSONObject;



import java.io.*;
import java.net.URL;

import java.util.ResourceBundle;

import static scripts.cluehuntercollector.ClueHunterCollector.GUIPATH;

@DoNotRename
public class Controller extends AbstractGUIController {

    private static FileWriter file;

    private void saveSettings() {
            try {
                JSONObject settings = new JSONObject();
                settings.clear();
                settings.put("helm", String.valueOf(helm.isSelected()));
                settings.put("bootsandgloves", String.valueOf(bootsandgloves.isSelected()));
                settings.put("garb", String.valueOf(garb.isSelected()));
                settings.put("cloak", String.valueOf(cloak.isSelected()));
                settings.put("trousers", String.valueOf(trousers.isSelected()));
                try (Writer writer = new FileWriter("Output.json")){
                    Gson gson = new GsonBuilder().create();
                    gson.toJson(settings,writer);
                }
            } catch (Exception ex) {
                    System.out.println("Unable to save settings " + ex.toString());
            }

    }

    public void loadSettings() {   //we will call this externally
        try {

        } catch (Exception e2) {
            System.out.print("Unable to load settings");
            e2.printStackTrace();
        }
    }

    @Override
    public void initialize(URL arg0, ResourceBundle arg1){
    //ToDo
        //foodType.setItems(FXCollections.observableArrayList(Constants.Locations.values()));
        //reloadChoice.setItems(FXCollections.observableArrayList("Hybrid"));
        //worldType.setItems(FXCollections.observableArrayList(main.World.values()));

    }




    @FXML @DoNotRename
    private CheckBox helm;

    @FXML @DoNotRename
    private CheckBox bootsandgloves;

    @FXML @DoNotRename
    private CheckBox garb;

    @FXML @DoNotRename
    private CheckBox cloak;

    @FXML @DoNotRename
    private CheckBox trousers;

    @FXML @DoNotRename
    private CheckBox staminas;

    @FXML @DoNotRename
    private CheckBox food;

    @FXML @DoNotRename
    private ChoiceBox foodType;


    @FXML @DoNotRename
    private Button startScriptButton;

    /*
    @FXML
    public void setSetCannonTileButtonPressed(){
        Vars.get().CANNON_TILE = Player.getPosition();
    }

     */





    @FXML @DoNotRename
    public void startScriptPressed(){
        saveSettings();


        if (cloak.isSelected()){
            General.println("Clue hunter cloak added to task list.");
        }
        if (garb.isSelected()){
            General.println("Clue hunter garb added to task list.");
        }
        if (bootsandgloves.isSelected()){
            General.println("Clue hunter boots and gloves added to task list.");
        }
        if (trousers.isSelected()){
            General.println("Clue hunter trousers added to task list.");
        }
        if (helm.isSelected()){
            General.println("Clue hunter helm added to task list.");
        }
        if (startScriptButton.isPressed()){
            General.println("Starting script..");
        }



        this.getGUI().close();
    }





}
