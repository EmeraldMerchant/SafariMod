package com.safari.mod;

import com.safari.mod.util.ModScanner;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KickedWarn {

    private KickedWarn() {
    }

    private static final int START_DELAY_TICKS = 10;

    private static final Pattern PLAYERS_PATTERN =
            Pattern.compile(
                    "Players\\s*\\((\\d+)\\)",
                    Pattern.CASE_INSENSITIVE
            );

    private static boolean safariInstanceStarted;
    private static boolean monitoring;
    private static boolean inventoryReady;

    private static int delayTicks;
    private static int lastAnnouncedCount = -1;

    public static void init() {

        ClientTickEvents.END_CLIENT_TICK.register(
                KickedWarn::tick
        );
    }

    public static void setSafari(
            boolean safari) {

        if (!safari) {

            safariInstanceStarted = false;
            monitoring = false;
            inventoryReady = false;

            delayTicks = 0;
            lastAnnouncedCount = -1;

            return;
        }

        if (!safariInstanceStarted) {

            safariInstanceStarted = true;

            monitoring = false;
            inventoryReady = false;

            delayTicks = 0;
            lastAnnouncedCount = -1;
        }
    }

    private static void tick(
            Minecraft minecraft) {

        if (!SafariModClient.inSafari) {
            return;
        }

        if (minecraft.level == null ||
                minecraft.player == null) {
            return;
        }

        if (!safariInstanceStarted) {
            setSafari(true);
        }

        /*
         * Once inventory slot 1 is no longer empty,
         * stop checking forever for this Safari instance.
         */
        if (!minecraft.player
                .getInventory()
                .getItem(0)
                .isEmpty()) {

            inventoryReady = true;
            monitoring = false;

            return;
        }

        if (inventoryReady) {
            return;
        }

        /*
         * Wait 0.5 seconds after world load.
         */
        if (!monitoring) {

            delayTicks++;

            if (delayTicks < START_DELAY_TICKS) {
                return;
            }

            monitoring = true;
        }

        int playerCount =
                getSafariPlayerCount(minecraft);

        if (playerCount < 0) {
            return;
        }

        /*
         * Don't spam the same count every tick.
         */
        if (playerCount == lastAnnouncedCount) {
            return;
        }

        lastAnnouncedCount = playerCount;

        announce(
                minecraft,
                playerCount
        );
    }

    private static int getSafariPlayerCount(
            Minecraft minecraft) {

        if (minecraft.getConnection() == null) {
            return -1;
        }

        for (var entry :
                minecraft.getConnection()
                        .getListedOnlinePlayers()) {

            Component displayName =
                    entry.getTabListDisplayName();

            if (displayName == null) {
                continue;
            }

            String text =
                    ModScanner.cleanText(
                            displayName.getString()
                    ).trim();

            int count =
                    findPlayerCount(text);

            if (count >= 0) {
                return count;
            }
        }

        return -1;
    }

    private static int findPlayerCount(
            String text) {

        if (text == null) {
            return -1;
        }

        text =
                ModScanner.cleanText(
                        text
                ).trim();

        Matcher matcher =
                PLAYERS_PATTERN.matcher(text);

        if (!matcher.find()) {
            return -1;
        }

        try {
            return Integer.parseInt(
                    matcher.group(1)
            );
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static void announce(
            Minecraft minecraft,
            int playerCount) {

        String title =
                "§c§l"
                        + playerCount
                        + "/4 Players";

        minecraft.gui.setTimes(
                0,
                40,
                10
        );

        minecraft.gui.setSubtitle(
                Component.empty()
        );

        minecraft.gui.setTitle(
                Component.literal(title)
        );

        if (minecraft.player != null) {

            minecraft.player.connection.sendCommand(
                    "pc [SafariUtils] "
                            + playerCount
                            + "/4 players in run!"
            );
        }
    }
}