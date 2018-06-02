package scripts.warriorGuildv2;

import org.tribot.api.General;
import org.tribot.script.Script;
import org.tribot.script.ScriptManifest;
import org.tribot.script.interfaces.Painting;
import scripts.warriorGuildv2.DecisionNodes.*;
import scripts.warriorGuildv2.DecisionNodes.buyFood;
import scripts.warriorGuildv2.ProcessNodes.*;
import scripts.warriorGuildv2.Tree_Framework.DecisionNode;
import scripts.warriorGuildv2.Tree_Framework.DecisionTree;
import scripts.warriorGuildv2.Tree_Framework.INode;
import scripts.warriorGuildv2.Tree_Framework.ProcessNode;

import java.awt.*;

@ScriptManifest(name = "WarriorsGuild", authors = "adamhackz", category = "Combat", version = 1.0, description = "Start with Mithril armour set, 1 empty spot for tokens, and atleast 1000 coins")

public class main extends Script implements Painting{

    @Override
    public void run() {

        DecisionNode shouldBank = new shouldBank();
        DecisionNode hasArmour = new hasArmour();
        DecisionNode inRoom = new inanimationRoom();
        DecisionNode hasFood = new hasFood();

        DecisionNode haslowHealth = new haslowHealth();
        DecisionNode instoreArea = new inStoreArea();
        DecisionNode decisionpickupArmour = new DecisionpickupArmour();
        DecisionNode isAnimated = new isAnimated();
        DecisionNode underAttack = new armourisAttacking();
        DecisionNode storeisOpen = new storeisOpen();
        DecisionNode exitShop = new exitShop();
        DecisionNode hasCoins = new hasCoins();
        DecisionNode readytoAnimate = new readytoAnimate();
        DecisionNode buyFood = new buyFood();


        ProcessNode timetoAttack = new timetoAttack(),
        withdrawCoins = new withdrawCoins(), eatlowHealth = new eatlowHealth(),
                gotoStore = new gotoStore(), buysFood = new buysFood(), walktoRoom = new walktoRoom(), pickuparmour = new pickupArmour(),
        animateTime = new animateTime(), idle = new idle(), openStore = new openStore(), closeShop = new closeShop(), endScript = new endScript();

        haslowHealth.addOnTrueNode(hasFood);
        hasFood.addOnTrueNode( eatlowHealth );
        hasFood.addOnFalseNode(hasCoins);
        hasCoins.addOnTrueNode(instoreArea);
        hasCoins.addOnFalseNode(withdrawCoins);
        instoreArea.addOnFalseNode(gotoStore);
        instoreArea.addOnTrueNode(storeisOpen);
        storeisOpen.addOnFalseNode(openStore);
        storeisOpen.addOnTrueNode(exitShop);
        exitShop.addOnTrueNode( closeShop );
        exitShop.addOnFalseNode(buysFood);

        haslowHealth.addOnFalseNode(inRoom);
        inRoom.addOnTrueNode(hasArmour);
        inRoom.addOnFalseNode(walktoRoom);

        hasArmour.addOnTrueNode(readytoAnimate);
        hasArmour.addOnFalseNode(underAttack);

        underAttack.addOnTrueNode(isAnimated);
        underAttack.addOnFalseNode(isAnimated);

        isAnimated.addOnTrueNode( timetoAttack );
        isAnimated.addOnFalseNode(readytoAnimate);

        readytoAnimate.addOnTrueNode( animateTime );
        readytoAnimate.addOnFalseNode(decisionpickupArmour);

        decisionpickupArmour.addOnTrueNode(pickuparmour );
        decisionpickupArmour.addOnFalseNode(idle );




        ///////////////animate



        //armouronFloor.addOnFalseNode(STOPSCRIPT);



        DecisionTree tree = new DecisionTree(haslowHealth);


        String lastStatus = "";
        while(true)
        {
            INode node = tree.getValidNode();

            if(node != null)
            {
                String status = node.getStatus();
                if(!status.equals(lastStatus))
                    General.println(status);
                lastStatus = status;

                node.execute();
            }

            General.sleep(1,10);
        }
    }

    @Override
    public void onPaint(Graphics graphics)
    {

    }


}
