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

    /*
    Start
     */

    private void saveSettings() {
        if (!directory.exists())
            directory.mkdirs();

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
    private ChoiceBox foodchoice;


    @FXML @DoNotRename
    private Button startScriptButton;

    @FXML @DoNotRename
    private Menu menusave;




    @FXML@DoNotRename
    public void startScriptPressed() {
        saveSettings();

        if (cloak.isSelected()) {
            General.println("Clue hunter cloak added to task list.");
        }
        if (garb.isSelected()) {
            General.println("Clue hunter garb added to task list.");
        }
        if (bootsandgloves.isSelected()) {
            General.println("Clue hunter boots and gloves added to task list.");
        }
        if (trousers.isSelected()) {
            General.println("Clue hunter trousers added to task list.");
        }
        if (helm.isSelected()) {
            General.println("Clue hunter helm added to task list.");
        }
        if (startScriptButton.isPressed()) {
            General.println("Starting script..");
        }

        this.getGUI().close();
    }

    @FXML
    public void menuSaveOpen(Stage stage){
        ImageView imgView = new ImageView("UIControls/Save.png");
        imgView.setFitWidth(20);
        imgView.setFitHeight(20);
        Menu file = new Menu("File");
        MenuItem item = new MenuItem("Save", imgView);
        file.getItems().addAll(item);
        //Creating a File chooser
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save");
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("All Files", "*.*"));
        //Adding action on the menu item
        item.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent event) {
                //Opening a dialog box
                fileChooser.showSaveDialog(stage);
            }
        });
        //Creating a menu bar and adding menu to it.
        MenuBar menuBar = new MenuBar(file);
        Group root = new Group(menuBar);
        Scene scene = new Scene(root, 595, 355, Color.BEIGE);
        stage.setTitle("File Chooser Example");
        stage.setScene(scene);
        stage.show();
    }


    @Override
    public void initialize(URL arg0, ResourceBundle arg1){
        //ToDo
            foodchoice.setItems(FXCollections.observableArrayList(foodChoiceArray));


        //reloadChoice.setItems(FXCollections.observableArrayList("Hybrid"));
        //worldType.setItems(FXCollections.observableArrayList(main.World.values()));

    }








}
