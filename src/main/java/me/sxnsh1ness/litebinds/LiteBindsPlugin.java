package me.sxnsh1ness.litebinds;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import me.sxnsh1ness.litebinds.command.BindCommand;
import me.sxnsh1ness.litebinds.database.DatabaseManager;
import me.sxnsh1ness.litebinds.listeners.EventListener;
import me.sxnsh1ness.litebinds.menu.listeners.MenuListener;
import me.sxnsh1ness.litebinds.menu.menus.BindMenu;

import java.util.Objects;

@Getter
public final class LiteBindsPlugin extends JavaPlugin {
    @Getter
    private static LiteBindsPlugin instance;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        instance = this;
        setupDatabase();
        setupCommands();
        setupListeners();
    }

    private void setupDatabase() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        databaseManager = new DatabaseManager(this, getConfig());
    }

    private void setupCommands() {
        Objects.requireNonNull(getCommand("binds")).setExecutor(new BindCommand());
    }

    private void setupListeners() {
        new MenuListener().register();
        getServer().getPluginManager().registerEvents(new EventListener(), this);
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BindMenu) {
                player.closeInventory();
            }
        }
    }
}
