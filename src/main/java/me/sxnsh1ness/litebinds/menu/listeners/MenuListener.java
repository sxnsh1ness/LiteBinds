package me.sxnsh1ness.litebinds.menu.listeners;

import me.sxnsh1ness.litebinds.LiteBindsPlugin;
import me.sxnsh1ness.litebinds.database.ActionType;
import me.sxnsh1ness.litebinds.database.DatabaseManager;
import me.sxnsh1ness.litebinds.database.PlayerActions;
import me.sxnsh1ness.litebinds.menu.abstracts.AbstractListener;
import me.sxnsh1ness.litebinds.menu.menus.BindMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.concurrent.CompletableFuture;

public class MenuListener extends AbstractListener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory inventory = event.getView().getTopInventory();
        if (!(inventory.getHolder() instanceof BindMenu menu)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null || event.getClickedInventory() != inventory) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        DatabaseManager dbm = LiteBindsPlugin.getInstance().getDatabaseManager();

        // Сброс всех горячих клавиш (Слот 22)
        if (slot == 22) {
            CompletableFuture.allOf(
                    dbm.updateActionDrop(player.getName(), ActionType.NONE),
                    dbm.updateActionSwap(player.getName(), ActionType.NONE),
                    dbm.updateActionInteract(player.getName(), ActionType.NONE)
            ).thenRun(() -> refreshMenu(menu, player));
            return;
        }

        // Маппинг слотов
        ActionType actionType = switch (slot) {
            case 10 -> ActionType.SNOWBALL;
            case 11 -> ActionType.JAKE;
            case 12 -> ActionType.ALTERNATIVE_TRAP;
            case 13 -> ActionType.STAN;
            case 14 -> ActionType.TRAP;
            case 15 -> ActionType.EXPLOSIVE;
            case 16 -> ActionType.BACKPACK;
            default -> null;
        };

        // Если кликнули не по предмету действия — игнорируем
        if (actionType == null) {
            return;
        }

        boolean isNext = event.isLeftClick();
        boolean isPrevious = event.isRightClick();

        if (!isNext && !isPrevious) {
            return;
        }

        // Переключение бинда и обновление
        updateActionState(player, actionType, isNext)
                .thenRun(() -> refreshMenu(menu, player));
    }

    private CompletableFuture<Void> updateActionState(Player player, ActionType actionType, boolean next) {
        DatabaseManager dbm = LiteBindsPlugin.getInstance().getDatabaseManager();

        return dbm.getPlayerActions(player.getName()).thenCompose(optional -> {
            PlayerActions actions = optional.orElseGet(() -> new PlayerActions(
                    player.getName(), ActionType.NONE, ActionType.NONE, ActionType.NONE
            ));

            // Циклическое смещение слотов взаимодействия
            cycleAction(actions, actionType, next);

            return dbm.savePlayerActions(actions);
        });
    }

    private void cycleAction(PlayerActions actions, ActionType target, boolean next) {
        ActionType currentDrop = actions.getActionDrop();
        ActionType currentSwap = actions.getActionSwap();
        ActionType currentInteract = actions.getActionInteract();

        if (next) {
            if (target == currentDrop) {
                actions.setActionDrop(ActionType.NONE);
                if (currentSwap == ActionType.NONE) actions.setActionSwap(target);
                else if (currentInteract == ActionType.NONE) actions.setActionInteract(target);
            } else if (target == currentSwap) {
                actions.setActionSwap(ActionType.NONE);
                if (currentInteract == ActionType.NONE) actions.setActionInteract(target);
            } else if (target == currentInteract) {
                actions.setActionInteract(ActionType.NONE);
            } else {
                if (currentDrop == ActionType.NONE) actions.setActionDrop(target);
                else if (currentSwap == ActionType.NONE) actions.setActionSwap(target);
                else if (currentInteract == ActionType.NONE) actions.setActionInteract(target);
            }
        } else {
            if (target == currentDrop) {
                actions.setActionDrop(ActionType.NONE);
            } else if (target == currentSwap) {
                actions.setActionSwap(ActionType.NONE);
                if (currentDrop == ActionType.NONE) actions.setActionDrop(target);
            } else if (target == currentInteract) {
                actions.setActionInteract(ActionType.NONE);
                if (currentSwap == ActionType.NONE) actions.setActionSwap(target);
                else if (currentDrop == ActionType.NONE) actions.setActionDrop(target);
            } else {
                if (currentInteract == ActionType.NONE) actions.setActionInteract(target);
                else if (currentSwap == ActionType.NONE) actions.setActionSwap(target);
                else if (currentDrop == ActionType.NONE) actions.setActionDrop(target);
            }
        }
    }

    private void refreshMenu(BindMenu menu, Player player) {
        Bukkit.getScheduler().runTask(LiteBindsPlugin.getInstance(), () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BindMenu) {
                menu.compile().open();
            }
        });
    }
}