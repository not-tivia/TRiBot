package scripts.cluehuntercollector.gui;

import com.allatori.annotations.DoNotRename;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;

import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.tribot.api.General;

import org.tribot.util.Util;
import scripts.api.dax_api.shared.jsonSimple.JSONObject;
import scripts.cluehuntercollector.data.Constants;
import scripts.cluehuntercollector.data.Vars;


import java.io.*;
import java.net.URL;

import java.util.ArrayList;
import java.util.ResourceBundle;

import static scripts.cluehuntercollector.data.Constants.foodChoiceArray;


@DoNotRename
public class Controller extends AbstractGUIController {

    private File directory = new File(Util.getWorkingDirectory()  + File.separator + "adamhackz" + File.separator + "cluehuntercollector");

    /*
    Food
     */

    /*
    Load
     */


    /*
    Save
     */


    private String[] getSaveFiles() {
        ArrayList<String> settings = new ArrayList<String>();
        if (!directory.exists())
            return new String[] {"No Saved Settings Found"};
        else {
            for (File f : directory.listFiles()) {
                if (f.isFile())
                    settings.add(f.getName());
            }
            return settings.toArray(new String[settings.size()]);
        }
    }

    /*
    Start
     */





    @FXML @DoNotRename
    private CheckBox helmcheckbox;

    @FXML @DoNotRename
    private CheckBox bootsandglovescheckbox;

    @FXML @DoNotRename
    private CheckBox garbcheckbox;

    @FXML @DoNotRename
    private CheckBox cloakcheckbox;

    @FXML @DoNotRename
    private CheckBox trouserscheckbox;

    @FXML @DoNotRename
    private CheckBox staminacheckbox;

    @FXML @DoNotRename
    private CheckBox foodcheckbox;

    @FXML @DoNotRename
    private ChoiceBox foodchoice;

    @FXML @DoNotRename
    private MenuItem menusave;


    @FXML @DoNotRename
    private Button startbutton;

    @FXML @DoNotRename
    private MenuItem menusaveas;

    @FXML @DoNotRename
    public void menunewpressed(){

    }

    @FXML @DoNotRename
    public void menuopenpressed(){

    }

    @FXML @DoNotRename
    public void menusaveaspressed(){
        if (!directory.exists())
            directory.mkdirs();
        try {
            JSONObject settings = new JSONObject();
            settings.clear();
            settings.put("helm", String.valueOf(helmcheckbox.isSelected()));
            settings.put("bootsandgloves", String.valueOf(bootsandglovescheckbox.isSelected()));
            settings.put("garb", String.valueOf(garbcheckbox.isSelected()));
            settings.put("cloak", String.valueOf(cloakcheckbox.isSelected()));
            settings.put("trousers", String.valueOf(trouserscheckbox.isSelected()));
            try (Writer writer = new FileWriter("Output.json")){
                Gson gson = new GsonBuilder().create();
                gson.toJson(settings,writer);
                General.println(gson.toJson(settings));

            }
            General.println("Settings saved successfully.");
        } catch (IOException e) {
            General.println("Error attempting to save settings.");
            e.printStackTrace();
        }
    }

    @FXML @DoNotRename
    public void startscriptpressed() {
        if (foodcheckbox.isSelected()){
            General.println("We are using food.");
        } else {
            General.println("We are not using food.");
        }

        if (cloakcheckbox.isSelected()){
            General.println("Clue hunter cloak added to task list.");
        }
        if (garbcheckbox.isSelected()){
            General.println("Clue hunter garb added to task list.");

        }
        if (bootsandglovescheckbox.isSelected()){
            General.println("Clue hunter boots and gloves added to task list.");

        }
        if (trouserscheckbox.isSelected()){
            General.println("Clue hunter trousers added to task list.");

        }
        if (helmcheckbox.isSelected()){
            General.println("Clue hunter helm added to task list.");

        }
        if (startbutton.isPressed()){
            General.println("Startscript button has been pressed.");
        }

//save settings


        this.getGUI().close();
    }




    @Override
    public void initialize(URL arg0, ResourceBundle arg1){
        //ToDo
            foodchoice.setItems(FXCollections.observableArrayList(foodChoiceArray));


        //reloadChoice.setItems(FXCollections.observableArrayList("Hybrid"));
        //worldType.setItems(FXCollections.observableArrayList(main.World.values()));

    }








}
