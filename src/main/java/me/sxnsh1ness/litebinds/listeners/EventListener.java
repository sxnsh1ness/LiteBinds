package me.sxnsh1ness.litebinds.listeners;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import me.sxnsh1ness.litebinds.LiteBindsPlugin;
import me.sxnsh1ness.litebinds.database.ActionType;
import me.sxnsh1ness.litebinds.database.PlayerActions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EventListener implements Listener {

    // Кэш в памяти: избавляет от лагов и запросов к БД в миллисекунду события
    private static final Map<String, PlayerActions> ACTIONS_CACHE = new ConcurrentHashMap<>();

    public static void setCachedActions(String playerName, PlayerActions actions) {
        ACTIONS_CACHE.put(playerName, actions);
    }

    public static void removeCachedActions(String playerName) {
        ACTIONS_CACHE.remove(playerName);
    }

    private PlayerActions getPlayerActions(String playerName) {
        return ACTIONS_CACHE.computeIfAbsent(playerName, name -> {
            // Если по какой-то причине в кэше нет (например, зашел до инициализации),
            // возвращаем дефолтные, а в фоновом режиме запрашиваем из БД
            LiteBindsPlugin.getInstance().getDatabaseManager().getPlayerActions(name).thenAccept(opt ->
                    opt.ifPresent(actions -> ACTIONS_CACHE.put(name, actions))
            );
            return new PlayerActions(name, ActionType.NONE, ActionType.NONE, ActionType.NONE);
        });
    }

    @EventHandler
    public void on(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack droppedItem = event.getItemDrop().getItemStack();

        // Проверяем напрямую по выброшенному предмету без тяжелых таймеров
        if (droppedItem.getType() == Material.NETHERITE_SWORD) {
            PlayerActions actions = getPlayerActions(player.getName());
            trigger(event, player, actions.getActionDrop());
        }
    }

    @EventHandler
    public void on(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack offHand = event.getOffHandItem();

        if (offHand != null && offHand.getType() == Material.NETHERITE_SWORD) {
            PlayerActions actions = getPlayerActions(player.getName());
            trigger(event, player, actions.getActionSwap());
        }
    }

    @EventHandler
    public void on(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.NETHERITE_SWORD) {
            PlayerActions actions = getPlayerActions(player.getName());
            trigger(event, player, actions.getActionInteract());
        }
    }

    private void trigger(Cancellable event, Player player, ActionType actionType) {
        if (actionType == null || actionType == ActionType.NONE) {
            return;
        }

        switch (actionType) {
            case SNOWBALL -> handleSnowball(event, player);
            case JAKE -> handleHolyItem(event, player, "item-type", "jake");
            case ALTERNATIVE_TRAP -> handleHolyItem(event, player, "item-type", "alternative_trap");
            case STAN -> handleHolyItem(event, player, "item-type", "stan");
            case TRAP -> handleHolyItem(event, player, "item-type", "explosive_trap");
            case EXPLOSIVE -> handleHolyItem(event, player, "item-type", "explosive_stuff");
            case BACKPACK -> handleBackpack(event, player);
        }
    }

    // Универсальный обработчик для HolyLiteItems
    private void handleHolyItem(Cancellable event, Player player, String key, String expectedValue) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("HolyLiteItems");
        if (plugin == null) return;

        NamespacedKey nsKey = new NamespacedKey(plugin, key);
        for (ItemStack itemStack : player.getInventory()) {
            if (itemStack != null && itemStack.hasItemMeta()) {
                String val = itemStack.getItemMeta().getPersistentDataContainer().getOrDefault(nsKey, PersistentDataType.STRING, "none");
                if (val.equalsIgnoreCase(expectedValue)) {
                    event.setCancelled(true);
                    Bukkit.getServer().getPluginManager().callEvent(new PlayerInteractEvent(
                            player, Action.RIGHT_CLICK_AIR, itemStack, null, BlockFace.DOWN
                    ));
                    return;
                }
            }
        }
    }

    // Обработчик снежка (выделен, т.к. логика с Projectile отличается)
    private void handleSnowball(Cancellable event, Player player) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("HolyLiteItems");
        if (plugin == null) return;

        NamespacedKey nsKey = new NamespacedKey(plugin, "item-type");
        for (ItemStack itemStack : player.getInventory()) {
            if (itemStack != null && itemStack.hasItemMeta()) {
                String val = itemStack.getItemMeta().getPersistentDataContainer().getOrDefault(nsKey, PersistentDataType.STRING, "none");
                if (val.equalsIgnoreCase("snowball")) {
                    event.setCancelled(true);
                    Snowball snowball = player.launchProjectile(Snowball.class);
                    snowball.setItem(itemStack);
                    snowball.setShooter(player);

                    PlayerLaunchProjectileEvent ev = new PlayerLaunchProjectileEvent(player, itemStack, snowball);
                    Bukkit.getServer().getPluginManager().callEvent(ev);

                    if (!ev.isCancelled()) {
                        itemStack.setAmount(itemStack.getAmount() - 1);
                    }
                    return;
                }
            }
        }
    }

    // Обработчик рюкзака (проверка Integer ключа)
    private void handleBackpack(Cancellable event, Player player) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("HolyBackPack");
        if (plugin == null) return;

        NamespacedKey nsKey = new NamespacedKey(plugin, "backpack-level");
        for (ItemStack itemStack : player.getInventory()) {
            if (itemStack != null && itemStack.hasItemMeta()) {
                int level = itemStack.getItemMeta().getPersistentDataContainer().getOrDefault(nsKey, PersistentDataType.INTEGER, -1);
                if (level == 4) {
                    event.setCancelled(true);
                    Bukkit.getServer().getPluginManager().callEvent(new PlayerInteractEvent(
                            player, Action.RIGHT_CLICK_AIR, itemStack, null, BlockFace.DOWN
                    ));
                    return;
                }
            }
        }
    }
}