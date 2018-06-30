package scripts.progressiveFletcher2.DecisionNodes;

import scripts.progressiveFletcher2.Tree_Framework.DecisionNode;

import java.util.HashMap;

public class containsmagic extends DecisionNode {
    String args;



    public void passArguments (HashMap< String, String > hashMap){
        if (hashMap.containsKey("custom_input")) {
            args = hashMap.get("custom_input");
        } else if (hashMap.containsKey("autostart")) {
            args = hashMap.get("autostart");
        }
    }
    @Override
    public boolean isValid() {
        return args.contains( "magic" );
    }
}
