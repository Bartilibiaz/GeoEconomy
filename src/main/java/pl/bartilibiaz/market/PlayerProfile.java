package pl.bartilibiaz.market;

import org.bukkit.Material;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerProfile {
    private final UUID uuid;
    private final Map<Material, Integer> wallet = new HashMap<>();

    public PlayerProfile(UUID uuid) {
        this.uuid = uuid;
    }

    public void addItem(Material mat, int amount) {
        wallet.put(mat, wallet.getOrDefault(mat, 0) + amount);
    }

    public void removeItem(Material mat, int amount) {
        int current = wallet.getOrDefault(mat, 0);
        if (current - amount <= 0) {
            wallet.remove(mat);
        } else {
            wallet.put(mat, current - amount);
        }
    }

    // Ta metoda jest wymagana przez ProfileGUI!
    public void setItem(Material mat, int amount) {
        wallet.put(mat, amount);
    }

    public int getAmount(Material mat) {
        return wallet.getOrDefault(mat, 0);
    }

    public Map<Material, Integer> getWallet() {
        return wallet;
    }

    public UUID getUuid() {
        return uuid;
    }
}