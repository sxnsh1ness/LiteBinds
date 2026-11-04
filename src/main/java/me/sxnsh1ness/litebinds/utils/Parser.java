package me.sxnsh1ness.litebinds.utils;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

@UtilityClass
public class Parser {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /**
     * Основной метод: преобразует MiniMessage строку в Adventure Component.
     */
    public static Component parse(String message) {
        if (message == null) return Component.empty();
        return MM.deserialize(message);
    }

    /**
     * Совместимость со старым кодом: если где-то в плагине всё еще
     * требуется строка (String), этот метод конвертирует MiniMessage в Legacy-строку.
     */
    public static String color(String message) {
        if (message == null) return "";
        Component component = MM.deserialize(message);
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    /**
     * Отправка кликабельного сообщения с URL и текстом при наведении через Adventure API.
     */
    public static void sendClickableMessage(Player player, List<String> lines, String url, String hoverText) {
        Component hoverComponent = MM.deserialize(hoverText);

        for (String line : lines) {
            Component messageComponent = MM.deserialize(line)
                    .clickEvent(ClickEvent.openUrl(url))
                    .hoverEvent(HoverEvent.showText(hoverComponent));

            player.sendMessage(messageComponent);
        }
    }

    /**
     * Полное удаление форматирования из MiniMessage строки.
     */
    public static String stripColor(String text) {
        if (text == null) return "";
        Component component = MM.deserialize(text);
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String formatTime(Timestamp timestamp) {
        if (timestamp == null) return "Не указано";
        SimpleDateFormat sdf = new SimpleDateFormat("d MMM HH:mm:ss", new Locale("ru", "RU"));
        String formattedDate = sdf.format(timestamp);

        formattedDate = formattedDate.replaceFirst("([А-Я])", "$1".toLowerCase());

        formattedDate = formattedDate
                .replaceAll("янв\\.?", "янв.")
                .replaceAll("фев\\.?", "фев.")
                .replaceAll("мар\\.?", "мар.")
                .replaceAll("апр\\.?", "апр.")
                .replaceAll("май\\.?", "май.")
                .replaceAll("июн\\.?", "июн.")
                .replaceAll("июл\\.?", "июл.")
                .replaceAll("авг\\.?", "авг.")
                .replaceAll("сен\\.?", "сен.")
                .replaceAll("окт\\.?", "окт.")
                .replaceAll("ноя\\.?", "ноя.")
                .replaceAll("дек\\.?", "дек.");

        return formattedDate;
    }

    public static String formatNumber(int number) {
        return String.format(Locale.US, "%,d", number);
    }
}