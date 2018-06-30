package scripts.progressiveFletcher2;

import org.tribot.api.General;
import org.tribot.api2007.Banking;
import org.tribot.script.Script;
import org.tribot.script.interfaces.Painting;
import scripts.progressiveFletcher2.DecisionNodes.*;
import scripts.progressiveFletcher2.ProcessNodes.*;
import scripts.progressiveFletcher2.Tree_Framework.DecisionNode;
import scripts.progressiveFletcher2.Tree_Framework.DecisionTree;
import scripts.progressiveFletcher2.Tree_Framework.INode;
import scripts.progressiveFletcher2.Tree_Framework.ProcessNode;

import java.awt.*;
import java.util.HashMap;

public class main extends Script implements Painting {


    @Override
    public void run() {

        DecisionNode hasyewbows = new hasyewbows(), hasmagicsandknife = new hasmagicsandknife(), hasyewsandknife = new hasyewsandknife(), lessthanlevel85 = new lessthanlevel85(), haswillowbows = new haswillowbows(), hasmaplebows = new hasmaplebows(), hasmaplesandknife = new hasmaplesandknife(), levellessthan65 = new levellessthan65(), haswillowlogs = new haswillowlogs(), hasoakbows = new hasoakbows(), haswillowsandknife = new haswillowsandknife(),hasarrowshafts = new hasarrowshafts(),hasoaklogs = new hasoaklogs(), hasoakandknife = new hasoaksandknife(), levellessthan35 = new levellessthan35(), levellessthan20 = new levellessthan20(), haslogs = new haslogs(),haslogsandknife = new haslogsandknife(), isinventoryfull = new isinventoryfull(),hasbowstrings = new hasbowstrings(), hasunstrungs = new hasunstrungs(),hasstringmats = new hasstringmats(),containsmagic = new containsmagic(),hasmagiclogs = new hasmagiclogs(), hasyewlogs = new hasyewlogs(),containsyew = new containsyew(),progressive = new progressive(), containsbows = new containsbow(),
                hasknife = new hasknife(), hasmaplelogs = new hasmaplelogs(), isbankopen = new isbankopen(), string = new string(), containsmaple = new containsmaple(), isanimating = new isanimating(), levellessthan40 = new levellessthan40();

        ProcessNode  withdrawwillowlogs = new withdrawwillowlogs(),useknifeonwillows = new useknifeonwillows(), withdrawoaklogs = new withdrawoaklogs(),depositallbutknife = new depositallbutknife(),useknifeonoaks = new useknifeonoaks(),useknifeonlogs = new useknifeonlogs(), withdrawlogs = new withdrawlogs(),closebank = new closebank(), withdrawbowstrings = new withdrawbowstrings(),withdrawunstrungs = new withdrawunstrungs(),stringcombine = new stringcombine(), useknifeonmagic = new useknifeonmagic(),withdrawmagiclogs = new withdrawmagiclogs(), useknifeonyew = new useknifeonyew(), withdrawyewlogs = new withdrawyewlogs(), useknifeonmaple = new useknifeonmaple(),idle = new idle(), openbank = new openbank(), withdrawknife = new withdrawknife(), withdrawmaplelogs = new withdrawmaplelogs();

        ////1-19
        progressive.addOnTrueNode(levellessthan20);
        levellessthan20.addOnTrueNode( isanimating );
        isanimating.addOnTrueNode( idle );
        isanimating.addOnFalseNode(haslogsandknife );
        haslogsandknife.addOnFalseNode(isbankopen);
        haslogsandknife.addOnTrueNode(useknifeonlogs);
        isbankopen.addOnFalseNode(openbank);
        isbankopen.addOnTrueNode(hasknife);
        hasknife.addOnFalseNode( withdrawknife );
        hasknife.addOnTrueNode(haslogs);
        haslogs.addOnFalseNode(isinventoryfull );
        haslogs.addOnTrueNode(isinventoryfull);
        isinventoryfull.addOnFalseNode( withdrawlogs );
        isinventoryfull.addOnFalseNode( closebank );

        //20-34
       levellessthan20.addOnFalseNode(levellessthan35);
       levellessthan35.addOnTrueNode( isanimating );
       isanimating.addOnTrueNode( idle );
       isanimating.addOnFalseNode( hasoakandknife );
       hasoakandknife.addOnFalseNode(isbankopen );
       hasoakandknife.addOnTrueNode( useknifeonoaks );
       isbankopen.addOnFalseNode( openbank );
       isbankopen.addOnTrueNode( hasknife );
       hasknife.addOnFalseNode( withdrawknife );
       hasknife.addOnTrueNode(hasarrowshafts );
       hasarrowshafts.addOnTrueNode(depositallbutknife );
       hasarrowshafts.addOnFalseNode(hasoaklogs);
       hasoaklogs.addOnFalseNode(isinventoryfull);
       hasoaklogs.addOnTrueNode(isinventoryfull);
       isinventoryfull.addOnTrueNode(closebank);
       isinventoryfull.addOnFalseNode(withdrawoaklogs);


       //35-49
        levellessthan35.addOnFalseNode(levellessthan40);
        levellessthan40.addOnTrueNode( isanimating );
        isanimating.addOnTrueNode( idle );
        isanimating.addOnFalseNode(haswillowsandknife );
        haswillowsandknife.addOnFalseNode( isbankopen );
        haswillowsandknife.addOnTrueNode( useknifeonwillows );
        isbankopen.addOnTrueNode( hasknife );
        isbankopen.addOnFalseNode( openbank );
        hasknife.addOnFalseNode( withdrawknife );
        hasknife.addOnTrueNode( hasoakbows );
        hasoakbows.addOnFalseNode( haswillowlogs );
        hasoakbows.addOnTrueNode( depositallbutknife );
        haswillowlogs.addOnFalseNode( isinventoryfull );
        haswillowlogs.addOnTrueNode( isinventoryfull );
        isinventoryfull.addOnTrueNode( closebank );
        isinventoryfull.addOnFalseNode( withdrawwillowlogs );


        //50 - 65

        levellessthan40.addOnFalseNode( levellessthan65);
        levellessthan65.addOnTrueNode( isanimating );
        isanimating.addOnTrueNode( idle );
        isanimating.addOnFalseNode( hasmaplesandknife );
        hasmaplesandknife.addOnFalseNode( isbankopen );
        hasmaplesandknife.addOnTrueNode( useknifeonmaple );
        isbankopen.addOnTrueNode( hasknife );
        isbankopen.addOnFalseNode( openbank );
        hasknife.addOnFalseNode( withdrawknife );
        hasknife.addOnTrueNode( haswillowbows);
        haswillowbows.addOnFalseNode( hasmaplelogs );
        haswillowbows.addOnTrueNode( depositallbutknife );
        hasmaplelogs.addOnTrueNode( isinventoryfull );
        hasmaplelogs.addOnFalseNode( isinventoryfull );
        isinventoryfull.addOnFalseNode( withdrawmaplelogs );
        isinventoryfull.addOnTrueNode( closebank );

        //65 - 85

        levellessthan65.addOnFalseNode( lessthanlevel85 );
        lessthanlevel85.addOnTrueNode( isanimating );
        isanimating.addOnTrueNode( idle );
        isanimating.addOnFalseNode( hasyewsandknife );
        hasyewsandknife.addOnFalseNode( isbankopen );
        hasyewsandknife.addOnTrueNode( useknifeonyew );
        isbankopen.addOnTrueNode( hasknife );
        isbankopen.addOnFalseNode( openbank );
        hasknife.addOnFalseNode( withdrawknife );
        hasknife.addOnTrueNode( hasmaplebows );
        hasmaplebows.addOnTrueNode( depositallbutknife );
        hasmaplebows.addOnFalseNode(  hasyewlogs);
        hasyewlogs.addOnFalseNode( isinventoryfull );
        hasyewlogs.addOnTrueNode( isinventoryfull );
        isinventoryfull.addOnFalseNode( withdrawyewlogs );
        isinventoryfull.addOnTrueNode( closebank );

        //85 -99

        lessthanlevel85.addOnFalseNode( isanimating );
        isanimating.addOnTrueNode( idle );
        isanimating.addOnFalseNode( hasmagicsandknife );
        hasmagicsandknife.addOnFalseNode( isbankopen );
        hasmagicsandknife.addOnTrueNode( useknifeonmagic );
        isbankopen.addOnTrueNode( hasknife );
        isbankopen.addOnFalseNode( openbank );
        hasknife.addOnFalseNode( withdrawknife );
        hasknife.addOnTrueNode( hasyewbows );
        hasyewbows.addOnTrueNode( depositallbutknife );
        hasyewbows.addOnFalseNode( hasmagiclogs );
        hasmagiclogs.addOnFalseNode( isinventoryfull );
        hasmagiclogs.addOnTrueNode( isinventoryfull );
        isinventoryfull.addOnTrueNode( closebank );
        isinventoryfull.addOnFalseNode( withdrawmagiclogs );






        // Stringing
        progressive.addOnFalseNode( string );
        string.addOnTrueNode(hasstringmats);
        hasstringmats.addOnTrueNode(isanimating);
        isanimating.addOnTrueNode(idle);
        isanimating.addOnFalseNode(stringcombine );
        hasstringmats.addOnFalseNode(hasunstrungs);
        hasunstrungs.addOnTrueNode(hasbowstrings);
        hasunstrungs.addOnFalseNode(isbankopen);
        isbankopen.addOnTrueNode(withdrawunstrungs);
        isbankopen.addOnFalseNode(openbank);
        hasbowstrings.addOnFalseNode(isbankopen);
        isbankopen.addOnTrueNode(withdrawbowstrings);
        isbankopen.addOnFalseNode( openbank );
        hasbowstrings.addOnTrueNode(stringcombine);

        //maple bows
        string.addOnFalseNode( containsbows );
        containsbows.addOnTrueNode(hasknife);
        hasknife.addOnFalseNode(isbankopen);
        isbankopen.addOnFalseNode(openbank);
        isbankopen.addOnTrueNode(withdrawknife);

        hasknife.addOnTrueNode(containsmaple);
        containsmaple.addOnTrueNode(hasmaplelogs);
        hasmaplelogs.addOnFalseNode(isbankopen);
        isbankopen.addOnFalseNode( openbank );
        isbankopen.addOnTrueNode( withdrawmaplelogs );


        hasmaplelogs.addOnTrueNode(isanimating);
        isanimating.addOnTrueNode(idle);
        isanimating.addOnFalseNode(useknifeonmaple);



        //yew bows
        containsmaple.addOnFalseNode(containsyew);
        containsyew.addOnTrueNode(hasyewlogs);
        hasyewlogs.addOnFalseNode(isbankopen);
        isbankopen.addOnFalseNode(openbank);
        isbankopen.addOnTrueNode(withdrawyewlogs);
        hasyewlogs.addOnTrueNode( isanimating );
        isanimating.addOnTrueNode( idle );
        isanimating.addOnFalseNode( useknifeonyew );



        // magic bows
        containsyew.addOnFalseNode(containsmagic);
        containsmagic.addOnTrueNode( hasmagiclogs );
        hasmagiclogs.addOnFalseNode(isbankopen);
        isbankopen.addOnFalseNode( openbank );
        isbankopen.addOnTrueNode( withdrawmagiclogs );
        hasmagiclogs.addOnTrueNode(isanimating );
        isanimating.addOnTrueNode( idle );
        isanimating.addOnFalseNode(useknifeonmagic);






        DecisionTree tree = new DecisionTree(progressive);


        String lastStatus = "";
        while (true) {
            INode node = tree.getValidNode();

            if (node != null) {
                String status = node.getStatus();
                if (!status.equals( lastStatus ))
                    General.println( status );
                lastStatus = status;

                node.execute();
            }

            General.sleep( 1, 10 );

        }



    }

    @Override
    public void onPaint(Graphics g)
    {

    }



}
