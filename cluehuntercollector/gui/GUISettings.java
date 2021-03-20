package scripts.cluehuntercollector.gui;

public class GUISettings {

    private boolean helm;
    private boolean bootsandgloves;
    private boolean garb;
    private boolean trousers;
    private boolean cloak;
    private boolean stamina;
    private boolean food;
    private String foodName;


    public boolean isFood() {
        return food;
    }

    public void setFood(boolean food) {
        this.food = food;
    }

    public String isFoodName() {
        return foodName;
    }

    public String setFoodName(String foodName) {
        return this.foodName = foodName;
    }


    public boolean isStamina() {
        return stamina;
    }

    public void setStamina(boolean stamina) {
        this.stamina = stamina;
    }

    public boolean isCloak() {
        return this.cloak;
    }

    public void setCloak(boolean cloak) {
        this.cloak = cloak;
    }

    public boolean isTrousers() {
        return this.trousers;
    }

    public void setTrousers(boolean trousers) {
        this.trousers = trousers;
    }

    public boolean isHelm() {
        return this.helm;
    }

    public void setHelm(boolean helm) {
        this.helm = helm;
    }

    public boolean isBootsandgloves() {
        return this.bootsandgloves;
    }

    public void setBootsandgloves(boolean bootsandgloves) {
        this.bootsandgloves = bootsandgloves;
    }

    public boolean isGarb() {
        return this.garb;
    }

    public void setGarb(boolean garb) {
        this.garb = garb;
    }
}
