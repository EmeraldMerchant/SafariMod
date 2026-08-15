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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.sound.sampled.TargetDataLine;

public class SafariModClient implements ClientModInitializer {
    public static final String TARGET_ARMOR_STAND_NAME = "SPARKLING";
    public static final String TARGET_CP = "Skeleton Master Chestplate";
    public static final long TITLE_VISIBLE_DURATION_MS = 125L; 
    public static final long TITLE_HIDDEN_DURATION_MS = 25L;

    private final Set<UUID> matchedArmorStands = new HashSet<>();
    public static final Set<UUID> hideyhoArmorStands = new HashSet<>();
    private final Map<UUID, ActiveArmorStandNotice> activeArmorStands = new HashMap<>();
    private net.minecraft.client.multiplayer.ClientLevel trackedLevel;
    private final Queue<ArmorStand> todo1 = new ConcurrentLinkedQueue<>();
    private final Queue<ArmorStand> todo2 = new ConcurrentLinkedQueue<>();

    private int tickCounter = 0;
    //private static final String critter = "";
    private static final int INTERVAL_TICKS = 4;
    private volatile boolean inSafari;
    private volatile boolean inM7;
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
            matchedArmorStands.clear();
            hideyhoArmorStands.clear(); // ADD THIS
            activeArmorStands.clear();
            minecraft.gui.clearTitles();
            return;
        }

        // Reset if dimension/level changes
        if (trackedLevel != minecraft.level) {
            trackedLevel = minecraft.level;
            hasScannedWorld = false;
            inSafari = false;
            inM7 = false;
            matchedArmorStands.clear();
            hideyhoArmorStands.clear(); // ADD THIS
            activeArmorStands.clear();
            minecraft.gui.clearTitles();
        }

        if (!hasScannedWorld) {
            checkSafariOnce(minecraft);
        }

        tickArmorStandTitles(minecraft);
    }

    private void onEntityLoad(Entity entity, ClientLevel level) {
        if (entity instanceof ArmorStand armorStand && (inSafari || inM7)) {
            todo1.add(armorStand);
        }
    }

    private void onEntityUnload(Entity entity, net.minecraft.client.multiplayer.ClientLevel level) {
        UUID uuid = entity.getUUID();
        if (activeArmorStands.remove(uuid) != null) {
            showNextActiveArmorStandTitle();
        }
    }

    private void stepFunction() {
        int todo2Size = todo2.size();
        for (int i = 0; i < todo2Size; i++) {
            ArmorStand armorStand = todo2.poll(); // Removes item from todo2
            if (armorStand == null) continue;

            // Check if armorstand is still valid (not removed/dead)
            if (!armorStand.isRemoved() && armorStand.isAlive()) {
                processArmorStand(armorStand);
            }
        }

        int todo1Size = todo1.size();
        for (int i = 0; i < todo1Size; i++) {
            ArmorStand armorStand = todo1.poll(); // Removes item from todo1
            if (armorStand == null) continue;

            // Check if armorstand is still valid
            if (!armorStand.isRemoved() && armorStand.isAlive()) {
                todo2.add(armorStand);
            }
        }
    }

    private void processArmorStand(ArmorStand armorStand) {
        Component customName = armorStand.getCustomName();
        if (customName == null) return;
        
        String name = customName.getString();
        Minecraft minecraft = Minecraft.getInstance();
        /* 
        if (minecraft.player != null && name.contains(critter)) {
            minecraft.player.sendSystemMessage(Component.literal(name));
        }*/

        boolean isTarget = name.contains(TARGET_ARMOR_STAND_NAME);
        boolean isCP = name.contains(TARGET_CP);
        boolean isHideyho = name.contains("Hideyho");

        if (((isTarget || isHideyho) && inSafari) || (isCP && inM7)) {
            UUID uuid = armorStand.getUUID();
            if (!matchedArmorStands.add(uuid)) return;

            if (isHideyho) {
                hideyhoArmorStands.add(uuid);
                return;
            }

            activeArmorStands.put(uuid, new ActiveArmorStandNotice(name, Util.getMillis(), true));

            if (minecraft.player != null) {
                minecraft.player.playSound(
                    net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                    0.5F,
                    1.0F
                );
            }
            showArmorStandTitle(name);
        }
    }

    private void checkSafariOnce(Minecraft minecraft) {
        Scoreboard scoreboard = minecraft.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return; // Scoreboard not loaded yet
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
            return; // Keep trying on future ticks until valid content loads
        }

        /*
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("Scoreboard Line 5: " + fullLine));
        }*/
        
        // Perform check and mark scan as completed now that real text has loaded
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
    private void tickArmorStandTitles(Minecraft minecraft) {
        if (activeArmorStands.isEmpty()) {
            return;
        }

        long now = Util.getMillis();
        boolean needsRefresh = false;
        for (ActiveArmorStandNotice notice : activeArmorStands.values()) {
            if (now >= notice.nextToggleAtMillis) {
                notice.visible = !notice.visible;
                
                // Use different durations depending on whether it just turned ON or OFF
                long duration = notice.visible ? TITLE_VISIBLE_DURATION_MS : TITLE_HIDDEN_DURATION_MS;
                notice.nextToggleAtMillis = now + duration;
                
                needsRefresh = true;
            }
        }

        if (needsRefresh) {
            showNextActiveArmorStandTitle();
        }
    }

    private void showNextActiveArmorStandTitle() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        List<ActiveArmorStandNotice> notices = new ArrayList<>(activeArmorStands.values());
        if (notices.isEmpty()) {
            minecraft.gui.clearTitles();
            return;
        }

        for (ActiveArmorStandNotice notice : notices) {
            if (notice.visible) {
                showArmorStandTitle(notice.name);
                return;
            }
        }

        minecraft.gui.clearTitles();
    }

    private void showArmorStandTitle(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        name = ModScanner.cleanText(name).trim();
        minecraft.gui.setSubtitle(Component.empty());
        minecraft.gui.setTitle(Component.literal("§6" + name));
    }

    private static final class ActiveArmorStandNotice {
        private final String name;
        private long nextToggleAtMillis;
        private boolean visible;

        private ActiveArmorStandNotice(String name, long now, boolean visible) {
            this.name = name;
            this.visible = visible;
            // Set initial duration based on starting state
            long duration = visible ? TITLE_VISIBLE_DURATION_MS : TITLE_HIDDEN_DURATION_MS;
            this.nextToggleAtMillis = now + duration;
        }
    }
}
