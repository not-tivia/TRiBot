package scripts.cannonalcher.gui;

import com.allatori.annotations.DoNotRename;
import com.google.gson.Gson;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.tribot.api.General;
import org.tribot.api2007.Login;
import org.tribot.api2007.Player;
import org.tribot.api2007.types.RSArea;
import org.tribot.api2007.types.RSTile;
import org.tribot.util.Util;

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

    @FXML
    @DoNotRename
    private TextField alchItemTextField;


    @FXML @DoNotRename
    private CheckBox castAlchCheckBox;

    @FXML @DoNotRename
    private CheckBox telegrabLootCheckBox;

    //Both Start script buttons are just copy and pasted clones of eachother
    @FXML @DoNotRename
    public void startScriptPressed(){
        if (presetLocationChoiceBox.getValue().equals(Constants.Locations.CASTLE_WARS)){
            cannonArea = Constants.Locations.CASTLE_WARS.getArea();
            cannonTile = Constants.Locations.CASTLE_WARS.getMiddleTile();
        } else {
            if (presetLocationChoiceBox.getValue().equals(Constants.Locations.COMBAT_TRAINING_AREA)){
                cannonArea = Constants.Locations.COMBAT_TRAINING_AREA.getArea();
                cannonTile = Constants.Locations.COMBAT_TRAINING_AREA.getMiddleTile();
            }
        }
        if (cannonTile==null || cannonArea==null){
            General.println("You need to set a location");
            continueRunning = false;
        }
        if (castAlchCheckBox.isSelected()){
            alchName = (alchItemTextField.getText());
        }

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
        if (presetLocationChoiceBox.getValue().equals(Constants.Locations.CASTLE_WARS)){
            cannonArea = Constants.Locations.CASTLE_WARS.getArea();
            cannonTile = Constants.Locations.CASTLE_WARS.getMiddleTile();
        } else {
            if (presetLocationChoiceBox.getValue().equals(Constants.Locations.COMBAT_TRAINING_AREA)){
                cannonArea = Constants.Locations.COMBAT_TRAINING_AREA.getArea();
                cannonTile = Constants.Locations.COMBAT_TRAINING_AREA.getMiddleTile();
            }
        }
        if (castAlchCheckBox.isSelected()){
            alchName = (alchItemTextField.getText());
        }

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
                cannonTile = Player.getPosition();
                cannonArea = new RSArea(new RSTile(cannonTile.getX() + 1, cannonTile.getY() - 1), new RSTile(cannonTile.getX() - 1, cannonTile.getY() + 1));;

            }
        } else {
            General.println("We need to be logged in to set a custom tile.");
        }

    }
    @FXML @DoNotRename
    public void setCustomSafePressed(){
        if (inGame()){
            if (Player.getRSPlayer()!=null && Player.getPosition()!=null) {
                cannonTile = Player.getPosition();
                cannonArea = new RSArea(new RSTile(cannonTile.getX() + 1, cannonTile.getY() - 1), new RSTile(cannonTile.getX() - 1, cannonTile.getY() + 1));;

            }
        } else {
            General.println("We need to be logged in to set a custom tile.");
        }
    }





    private boolean inGame() {
        return Login.getLoginState() == Login.STATE.INGAME;
    }



}
