package com.safari.mod;

import com.safari.mod.render.TextDisplayManager;
import com.safari.mod.util.ModScanner;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

public final class CaptureDetector {

    private CaptureDetector() {
    }

    public static void init() {

        ClientReceiveMessageEvents.GAME.register(
                CaptureDetector::onGameMessage);
    }

    private static void onGameMessage(
            Component message,
            boolean overlay) {

        if (!SafariModClient.inSafari) {
            return;
        }

        String text = ModScanner.cleanText(
                message.getString()).trim();
        //CAPTURE! You found the Hideyho, and as a reward it gave you 6x Hideyho Shard!
        if (text.startsWith("CAPTURE!")) {

            String mob = extractCaptureMob(text);

            if (mob != null) {
                onMobCompleted(mob);
            }

            return;
        }

        if (text.startsWith("LOOT SHARE!")) {

            String mob = extractLootShareMob(text);

            if (mob != null) {
                onMobCompleted(mob);
            }
        }
    }

    private static String extractCaptureMob(
            String text) {
        //CAPTURE! You found the Hideyho, and as a reward it gave you 6x Hideyho Shard!
        String marker;
        if (text.contains("You caught a ")) {
            marker = "You caught a ";
        } else if (text.contains("You caught an ")) {
            marker = "You caught an ";
        } else if (text.contains("You found the ")) {
            marker = "You found the ";
            text.replace(", and ", " and gained ");
        } else {
            return null;
        }

        int start = text.indexOf(marker);

        if (start < 0) {
            return null;
        }

        start += marker.length();

        int end = text.indexOf(
                " and gained",
                start);

        if (end < 0) {
            return null;
        }

        String mob = text.substring(
                start,
                end).trim();

        if (mob.startsWith("SPARKLING ")) {
            mob = mob.substring("SPARKLING ".length()).trim();
        }

        return mob.isEmpty()
                ? null
                : mob;
    }

    private static String extractLootShareMob(
            String text) {

        String marker;
        if (text.contains("catching a ")) {
            marker = "catching a ";
        } else if (text.contains("catching an ")) {
            marker = "catching an ";
        } else if (text.contains("finding the ")) {
            marker = "finding the ";
        } else {
            return null;
        }

        int start = text.indexOf(marker);

        if (start < 0) {
            return null;
        }

        start += marker.length();

        String mob = text.substring(start).trim();

        if (mob.endsWith("!")) {
            mob = mob.substring(
                    0,
                    mob.length() - 1).trim();
        }
        if (mob.startsWith("SPARKLING ")) {
            mob = mob.substring("SPARKLING ".length()).trim();
        }

        return mob.isEmpty()
                ? null
                : mob;
    }

    private static void onMobCompleted(
            String mob) {

        mob = ModScanner.cleanText(
                mob).trim();

        TextDisplayManager.removeMob(mob);
    }
}