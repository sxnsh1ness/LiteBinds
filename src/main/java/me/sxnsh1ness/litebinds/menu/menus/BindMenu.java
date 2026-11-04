package me.sxnsh1ness.litebinds.menu.menus;

import me.sxnsh1ness.litebinds.LiteBindsPlugin;
import me.sxnsh1ness.litebinds.database.ActionType;
import me.sxnsh1ness.litebinds.database.PlayerActions;
import me.sxnsh1ness.litebinds.menu.abstracts.AbstractMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BindMenu extends AbstractMenu {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    @Override
    public AbstractMenu compile() {
        try {
            // Десериализуем через MiniMessage и конвертируем в legacy-строку для инвентаря 1.16.5
            Component titleComp = MM.deserialize("<dark_gray>Горячие клавиши</dark_gray>");
            inventory = Bukkit.createInventory(this, 27, LEGACY.serialize(titleComp));

            Optional<PlayerActions> playerActionsOptional = LiteBindsPlugin.getInstance().getDatabaseManager().getPlayerActions(viewer.getName()).get();
            PlayerActions playerActions = playerActionsOptional.orElseGet(() -> new PlayerActions(
                    viewer.getName(),
                    ActionType.NONE,
                    ActionType.NONE,
                    ActionType.NONE
            ));

            int startSlot = 10;
            String[] displayNames = {
                    "<gradient:#00BCCC:#00EBFF>Ком снега</gradient>",
                    "<gradient:#C89400:#FB31B9>Светильник джейка</gradient>",
                    "<#C15BD3>Трапка",
                    "<gradient:#EFEFEF:#D3D3D3>Стан</gradient>",
                    "<gradient:#676A85:#8185A7>Взрывная трапка</gradient>",
                    "<gradient:#C82700:#FB3100>Взрывная штучка</gradient>",
                    "<gradient:#C30BA5:#F30DCE>Рюкзак</gradient> <white>(IV уровень)</white>"
            };

            ActionType[] actionTypes = {
                    ActionType.SNOWBALL,
                    ActionType.JAKE,
                    ActionType.ALTERNATIVE_TRAP,
                    ActionType.STAN,
                    ActionType.TRAP,
                    ActionType.EXPLOSIVE,
                    ActionType.BACKPACK,
            };

            Material[] materials = {
                    Material.SNOWBALL,
                    Material.JACK_O_LANTERN,
                    Material.POPPED_CHORUS_FRUIT,
                    Material.NETHER_STAR,
                    Material.PRISMARINE_SHARD,
                    Material.FIRE_CHARGE,
                    Material.MAGENTA_SHULKER_BOX
            };

            for (int i = 0; i < displayNames.length; i++) {
                String rawDisplayName = displayNames[i];
                ActionType actionType = actionTypes[i];

                ItemStack itemStack = new ItemStack(materials[i]);
                ItemMeta itemMeta = itemStack.getItemMeta();
                if (itemMeta == null) continue;

                // Установка названия через legacy-строку
                itemMeta.setDisplayName(LEGACY.serialize(MM.deserialize(rawDisplayName)));

                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add(LEGACY.serialize(MM.deserialize("<#E7E7E7>Действие:")));

                boolean isDrop = (actionType == playerActions.getActionDrop());
                boolean isSwap = (actionType == playerActions.getActionSwap());
                boolean isInteract = (actionType == playerActions.getActionInteract());
                boolean isNone = (!isDrop && !isSwap && !isInteract);

                lore.add(LEGACY.serialize(MM.deserialize(isNone ? "<gold>● Выключено</gold>" : "<#9CF9FF>● <#D5DBDC>Выключено")));
                lore.add(LEGACY.serialize(MM.deserialize(isDrop ? "<gold>● Выбросить предмет <gray>[Q]</gray></gold>" : "<#9CF9FF>● <#D5DBDC>Выбросить предмет <gray>[Q]</gray>")));
                lore.add(LEGACY.serialize(MM.deserialize(isSwap ? "<gold>● Вторая рука <gray>[F]</gray></gold>" : "<#9CF9FF>● <#D5DBDC>Вторая рука <gray>[F]</gray>")));
                lore.add(LEGACY.serialize(MM.deserialize(isInteract ? "<gold>● Использовать <gray>[ПКМ]</gray></gold>" : "<#9CF9FF>● <#D5DBDC>Использовать <gray>[ПКМ]</gray>")));

                lore.add("");

                String actionDescription;
                if (isDrop) {
                    actionDescription = "выбрасывании Незеритового меча";
                } else if (isSwap) {
                    actionDescription = "взятии во вторую руку Незеритового меча";
                } else if (isInteract) {
                    actionDescription = "нажатии ПКМ с Незеритовым мечем в руке";
                } else {
                    actionDescription = "ручной активации";
                }

                lore.add(LEGACY.serialize(MM.deserialize("<#00D8FF><b><u>▍</u></b><white> При " + actionDescription + ",</white>")));

                Component footerLine = MM.deserialize("<#00D8FF><b>▍</b><white> вы активируете предмет </white>")
                        .append(MM.deserialize(rawDisplayName));
                lore.add(LEGACY.serialize(footerLine));

                // Установка лора через List<String>
                itemMeta.setLore(lore);
                itemStack.setItemMeta(itemMeta);
                inventory.setItem(startSlot + i, itemStack);
            }

            ItemStack resetItem = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta resetMeta = resetItem.getItemMeta();
            if (resetMeta != null) {
                resetMeta.setDisplayName(LEGACY.serialize(MM.deserialize("<#05FB00>Выключить всё")));

                List<String> resetLore = new ArrayList<>();
                resetLore.add("");
                resetLore.add(LEGACY.serialize(MM.deserialize("<#05FB00>▶ </#05FB00><white>Нажмите, чтобы сбросить горячие клавиши</white>")));

                resetMeta.setLore(resetLore);
                resetMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                resetMeta.addEnchant(Enchantment.SOUL_SPEED, 1, true);
                resetItem.setItemMeta(resetMeta);
            }
            inventory.setItem(22, resetItem);

        } catch (Exception e) {
            throw new RuntimeException("Failed to compile Menu inventory for viewer: " + viewer.getName(), e);
        }
        return this;
    }
}