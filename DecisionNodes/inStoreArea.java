package scripts.warriorGuildv2.DecisionNodes;

import org.tribot.api2007.Player;
import org.tribot.api2007.types.RSArea;
import org.tribot.api2007.types.RSTile;
import scripts.warriorGuildv2.Tree_Framework.DecisionNode;

public class inStoreArea extends DecisionNode {


        private RSArea areafood = new RSArea( new RSTile( 2839, 3548, 0 ), new RSTile( 2843, 3555, 0 ) );

        @Override
        public boolean isValid() {
            return areafood.contains( Player.getRSPlayer().getPosition());
        }


}
