package com.safari.mod;

import com.safari.mod.render.ArmorStandTracerRenderer;
import com.safari.mod.util.ModScanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.util.Util;
import net.minecraft.world.scores.PlayerTeam;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.sound.sampled.TargetDataLine;

public class SafariModClient implements ClientModInitializer {
    public static final String TARGET_ARMOR_STAND_NAME = "SPARKLING";
    public static final String TARGET_CP = "Skeleton Master Chestplate";
    public static final String TARGET_Hideyho = "Hideyho";

    public static final Set<ArmorStand> armorStandsToAlert = new HashSet<>();
    private net.minecraft.client.multiplayer.ClientLevel trackedLevel;
    private final Queue<ArmorStand> todo1 = new ConcurrentLinkedQueue<>();
    private final Queue<ArmorStand> todo2 = new ConcurrentLinkedQueue<>();

    private int tickCounter = 0;
    // private static final String critter = "";
    public static final String RANDOM_SYMBOL = "§b[§6§k0§r§b]§r";
    private static final int INTERVAL_TICKS = 4;
    public static volatile boolean inSafari;
    public static volatile boolean inM7;
    private boolean hasScannedWorld = false;

    @Override
    public void onInitializeClient() {
        ArmorStandTracerRenderer.init();
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
        ClientEntityEvents.ENTITY_LOAD.register(this::onEntityLoad);
        ClientEntityEvents.ENTITY_UNLOAD.register(this::onEntityUnload);

        // Reset detection flag whenever the player connects/joins a new world
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            hasScannedWorld = false;
            inSafari = false;
            inM7 = false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickCounter++;
            if (tickCounter >= INTERVAL_TICKS) {
                tickCounter = 0;
                stepFunction();
            }
        });
    }

    private void onEndClientTick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            inSafari = false;
            inM7 = false;
            hasScannedWorld = false;
            trackedLevel = null;
            ArmorStandTracerRenderer.clear();
            armorStandsToAlert.clear();
            minecraft.gui.clearTitles();
            return;
        }

        // Reset if dimension/level changes
        if (trackedLevel != minecraft.level) {
            trackedLevel = minecraft.level;
            hasScannedWorld = false;
            inSafari = false;
            inM7 = false;
            ArmorStandTracerRenderer.clear();
            armorStandsToAlert.clear();
            minecraft.gui.clearTitles();
        }

        if (!hasScannedWorld) {
            checkSafariOnce(minecraft);
        }

        showAlertTitles(minecraft);
    }

    private void showAlertTitles(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }

        if (armorStandsToAlert.isEmpty()) {
            minecraft.gui.clearTitles();
            return;
        }

        /*
         * Just display the name of the first Armor Stand
         * currently in the alert set.
         */
        ArmorStand armorStand = armorStandsToAlert.iterator().next();

        if (armorStand.isRemoved() ||
                !armorStand.isAlive() ||
                armorStand.getCustomName() == null) {

            armorStandsToAlert.remove(armorStand);
            minecraft.gui.clearTitles();
            return;
        }

        showArmorStandTitle(
                armorStand.getCustomName().getString());
    }

    private void onEntityLoad(Entity entity, ClientLevel level) {
        if (entity instanceof ArmorStand armorStand && (inSafari || inM7)) {
            todo1.add(armorStand);
        }
    }

    private void onEntityUnload(
            Entity entity,
            net.minecraft.client.multiplayer.ClientLevel level) {

        if (entity instanceof ArmorStand armorStand) {
            ArmorStandTracerRenderer.removeLineTo(armorStand);
            armorStandsToAlert.remove(armorStand);
        }
    }

    private void stepFunction() {
        int todo2Size = todo2.size();
        for (int i = 0; i < todo2Size; i++) {
            ArmorStand armorStand = todo2.poll(); // Removes item from todo2
            if (armorStand == null)
                continue;

            // Check if armorstand is still valid (not removed/dead)
            if (!armorStand.isRemoved() && armorStand.isAlive()) {
                processArmorStand(armorStand);
            }
        }

        int todo1Size = todo1.size();
        for (int i = 0; i < todo1Size; i++) {
            ArmorStand armorStand = todo1.poll(); // Removes item from todo1
            if (armorStand == null)
                continue;

            // Check if armorstand is still valid
            if (!armorStand.isRemoved() && armorStand.isAlive()) {
                todo2.add(armorStand);
            }
        }
    }

    private void processArmorStand(ArmorStand armorStand) {
        Component customName = armorStand.getCustomName();

        if (customName == null) {
            return;
        }

        String name = customName.getString();

        boolean isTarget = name.contains(TARGET_ARMOR_STAND_NAME);
        boolean isCP = name.contains(TARGET_CP);
        boolean isHideyho = name.contains(TARGET_Hideyho);

        /*
         * Things that need a tracer: sparkling & hideyho in safari & master cp in m7
         */
        if (inSafari && (isTarget || isHideyho)) {
            ArmorStandTracerRenderer.drawLineTo(armorStand);
        }

        if (inM7 && isCP) {
            ArmorStandTracerRenderer.drawLineTo(armorStand);
        }

        /*
         * Things that need the flashing alert: sparkling in safari & master cp in m7
         */
        if ((inSafari && isTarget) || (inM7 && isCP)) {
            if (armorStandsToAlert.add(armorStand)) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null) {
                    minecraft.player.playSound(
                            net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                            0.5F,
                            1.0F);
                }
            }
        }
    }

    private void checkSafariOnce(Minecraft minecraft) {
        Scoreboard scoreboard = minecraft.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return;
        }

        List<PlayerScoreEntry> sortedScores = scoreboard.listPlayerScores(sidebar)
                .stream()
                .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed())
                .toList();

        // Ensure Line 5 (index 4) exists before checking
        if (sortedScores.size() < 5) {
            return;
        }

        // Fetch Line 5
        PlayerScoreEntry entry = sortedScores.get(4);
        String owner = entry.ownerName().getString();
        PlayerTeam team = scoreboard.getPlayersTeam(owner);

        String fullLine = owner;
        if (team != null) {
            fullLine = team.getPlayerPrefix().getString() + owner + team.getPlayerSuffix().getString();
        }

        // Ignore placeholder lines, blank lines, or lines containing only spaces
        String cleanedLine = ModScanner.cleanText(fullLine).trim();
        if (cleanedLine.isEmpty() || cleanedLine.contains("None")) {
            return;
        }

        /*
         * if (minecraft.player != null) {
         * minecraft.player.sendSystemMessage(Component.literal("Scoreboard Line 5: " +
         * fullLine));
         * }
         */

        inSafari = containsSafari(fullLine);
        inM7 = containsM7(fullLine);
        hasScannedWorld = true;
    }

    private boolean containsSafari(String text) {
        return ModScanner.cleanText(text).endsWith("Critter Safari");
    }

    private boolean containsM7(String text) {
        return ModScanner.cleanText(text).contains("(M7)");
    }

    private void showArmorStandTitle(String name) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null)
            return;
        name = ModScanner.cleanText(name).trim();

        /*
         * Remove the mob-type prefix from Critter names.
         */
        String[] parts = name.split("\\s+");
        if (parts.length >= 3) {
            name = String.join(" ", java.util.Arrays.copyOfRange(parts, 2, parts.length));
        }

        /*
         * Convert & formatting codes to Minecraft's § codes.
         */
        String displayName = RANDOM_SYMBOL + " §6" + name + " " + RANDOM_SYMBOL;
        minecraft.gui.setSubtitle(Component.empty());
        minecraft.gui.setTitle(Component.literal(displayName));
    }

}
