package me.sxnsh1ness.litebinds.menu.abstracts;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.java.JavaPlugin;
import me.sxnsh1ness.litebinds.LiteBindsPlugin;

public abstract class AbstractListener implements Listener {
    @EventHandler
    public abstract void onClick(InventoryClickEvent event);

    @EventHandler
    public void onClose(InventoryCloseEvent event) { }

    private void register(JavaPlugin plugin){
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void register(){
        this.register(LiteBindsPlugin.getInstance());
    }
}