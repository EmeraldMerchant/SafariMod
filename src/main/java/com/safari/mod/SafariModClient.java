package com.safari.mod;

import com.safari.mod.render.ArmorStandTracerRenderer;
import com.safari.mod.render.FloorDropHighlighter;

import com.safari.mod.util.ModScanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.util.Util;
import net.minecraft.world.scores.PlayerTeam;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SafariModClient implements ClientModInitializer {
    public static final String TARGET_ARMOR_STAND_NAME = "SPARKLING";
    public static final String TARGET_CP = "Skeleton Master Chestplate";
    public static final String TARGET_Hideyho = "Hideyho";
    public static final String TARGET_DUPLICO = "Duplico";
    private static final String ROCKMITE_MOUND = "ewogICJ0aW1lc3RhbXAiIDogMTc4MjgzMjk3Nzg1MSwKICAicHJvZmlsZUlkIiA6ICI3MDYwMDk0OTgyZDc0MTczYTNjZjg1Zjc1NjQ3MGE5YiIsCiAgInByb2ZpbGVOYW1lIiA6ICJpbmV4YWN0b3N0ZW50YXQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWRiYWFiNzRkMWFjZDBhYmU5ZDA0YWJlOTkyODcyNWRlNWQ0NDk1ZmNiNjNiNjQ3MjI4Y2FmNjk0NGMyMDgwMCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9";

    public static final Set<ArmorStand> armorStandsToAlert = new HashSet<>();
    private net.minecraft.client.multiplayer.ClientLevel trackedLevel;
    private final Queue<ArmorStand> todo1 = new ConcurrentLinkedQueue<>();
    private final Queue<ArmorStand> todo2 = new ConcurrentLinkedQueue<>();

    private int tickCounter = 0;
    private static final String critter = "";
    private static final int INTERVAL_TICKS = 4;
    public static volatile boolean inSafari;
    public static volatile boolean inM7;
    private boolean hasScannedWorld = false;

    private static final Map<BlockPos, Long> floorDropLastConfirmed = new HashMap<>();
    private static final long FLOOR_DROP_TIMEOUT_MS = 5000L;

    @Override
    public void onInitializeClient() {
        ArmorStandTracerRenderer.init();
        FloorDropHighlighter.init();
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
        ClientEntityEvents.ENTITY_LOAD.register(this::onEntityLoad);
        ClientEntityEvents.ENTITY_UNLOAD.register(this::onEntityUnload);

        // Reset detection flag whenever the player connects/joins a new world
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            hasScannedWorld = false;
            inSafari = false;
            inM7 = false;
            FloorDropHighlighter.clear();
            floorDropLastConfirmed.clear();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickCounter++;
            if (tickCounter >= INTERVAL_TICKS) {
                tickCounter = 0;
                stepFunction();
            }

            if (client.level != null && client.level.getGameTime() % 20 == 0) {
                updateFloorDrops();
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
            FloorDropHighlighter.clear();
            floorDropLastConfirmed.clear();
            armorStandsToAlert.clear();
            minecraft.gui.clearTitles();
            return;
        }
        ArmorStandTracerRenderer.cleanup();
        // Reset if dimension/level changes
        if (trackedLevel != minecraft.level) {
            trackedLevel = minecraft.level;
            hasScannedWorld = false;
            inSafari = false;
            inM7 = false;
            ArmorStandTracerRenderer.clear();
            FloorDropHighlighter.clear();
            floorDropLastConfirmed.clear();
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

    private void onEntityLoad(
            Entity entity,
            ClientLevel level) {
        if (!inSafari && !inM7) {
            return;
        }

        if (entity instanceof ArmorStand armorStand) {
            todo1.add(armorStand);
        }
    }

    private void onEntityUnload(
            Entity entity,
            net.minecraft.client.multiplayer.ClientLevel level) {
        ArmorStandTracerRenderer.removeGlow(entity);
        armorStandsToAlert.remove(entity);
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
        scanMounds();
    }

    private void processArmorStand(ArmorStand armorStand) {
        Component customName = armorStand.getCustomName();

        if (customName == null) {
            return;
        }

        String name = customName.getString();
        boolean isSparkling = name.contains(TARGET_ARMOR_STAND_NAME);
        boolean isHideon = name.contains("Hideon");
        boolean isCP = name.contains(TARGET_CP);
        boolean isHideyho = name.contains(TARGET_Hideyho);

        if (inSafari) {
            if (isSparkling) {
                glowSparkling(armorStand);
            }

            if (isHideon) {
                glowHideon(armorStand);
            }

            if (isHideyho) {
                glowHideyho(armorStand);
            }

            if (name.contains(TARGET_DUPLICO)) {
                glowDuplico(armorStand);
            }
        }

        if ((inSafari && isSparkling) ||
                (inM7 && isCP)) {

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

    private void glowHideyho(ArmorStand hideyhoNameTag) {

        Player target = findClosestEntity(
                hideyhoNameTag,
                Player.class,
                player -> player != Minecraft.getInstance().player);

        if (target != null) {
            ArmorStandTracerRenderer.glow(
                    target,
                    0,
                    255,
                    255);
        }
    }

    private void scanMounds() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null ||
                minecraft.player == null ||
                !inSafari) {
            return;
        }

        for (Display.ItemDisplay display : minecraft.level.getEntitiesOfClass(
                Display.ItemDisplay.class,
                minecraft.player.getBoundingBox().inflate(32.0),
                entity -> entity.isAlive() && !entity.isRemoved())) {

            ItemStack stack = display.itemRenderState() != null
                    ? display.itemRenderState().itemStack()
                    : ItemStack.EMPTY;

            if (stack.isEmpty() || !stack.is(Items.PLAYER_HEAD)) {
                continue;
            }

            String texture = getHeadTexture(stack);

            if (ROCKMITE_MOUND.equals(texture)) {
                ArmorStandTracerRenderer.glow(
                        display,
                        255,
                        165,
                        0);
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
         * chat("Scoreboard Line 5: " + fullLine));
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
        if (name.contains(critter)) {
            String[] parts = name.split("\\s+");
            if (parts.length >= 3) {
                name = "§l§6SPARKLING§r" + String.join(" ", java.util.Arrays.copyOfRange(parts, 2, parts.length));
            }
        }

        String displayName = titleDecor(name);
        minecraft.gui.setTimes(0, 2000, 0);
        minecraft.gui.setSubtitle(Component.empty());
        minecraft.gui.setTitle(Component.literal(displayName));
    }

    private static int countStringItemDisplays(BlockPos pos) {
        ClientLevel world = Minecraft.getInstance().level;

        if (world == null) {
            return 0;
        }

        List<Display.ItemDisplay> entities = world.getEntitiesOfClass(
                Display.ItemDisplay.class,
                AABB.ofSize(Vec3.atCenterOf(pos), 1.0, 1.0, 1.0),
                _ -> true);

        return (int) entities.stream()
                .filter(entity -> {
                    var state = entity.itemRenderState();

                    if (state == null) {
                        return false;
                    }

                    ItemStack stack = state.itemStack();

                    return !stack.isEmpty()
                            && stack.getItem().equals(Items.STRING);
                })
                .count();
    }

    public static void onParticlePacket(ClientboundLevelParticlesPacket packet) {
        if (!inSafari) {
            return;
        }

        ParticleType<?> particleType = packet.getParticle().getType();

        if (!ParticleTypes.HAPPY_VILLAGER.getType().equals(particleType)) {
            return;
        }

        double x = packet.getX();
        double y = packet.getY() - 1.0;
        double z = packet.getZ();

        BlockPos pos = BlockPos.containing(x, y, z);

        checkFloorDrop(pos);
    }

    private static void checkFloorDrop(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return;
        }

        int stringItemCount = countStringItemDisplays(pos);

        if (stringItemCount == 3) {
            floorDropLastConfirmed.put(pos, Util.getMillis());
            FloorDropHighlighter.highlight(pos);
        }
    }

    private static void updateFloorDrops() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || !inSafari) {
            FloorDropHighlighter.clear();
            floorDropLastConfirmed.clear();
            return;
        }

        long now = Util.getMillis();

        Iterator<Map.Entry<BlockPos, Long>> iterator = floorDropLastConfirmed.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();

            BlockPos pos = entry.getKey();
            long lastConfirmed = entry.getValue();

            if (countStringItemDisplays(pos) == 3) {
                entry.setValue(now);
                FloorDropHighlighter.highlight(pos);
            } else if (now - lastConfirmed > FLOOR_DROP_TIMEOUT_MS) {
                FloorDropHighlighter.remove(pos);
                iterator.remove();
            }
        }
    }

    private <T extends Entity> T findClosestEntity(
            Entity source,
            Class<T> entityType,
            java.util.function.Predicate<T> filter) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || source == null) {
            return null;
        }

        AABB searchBox = source.getBoundingBox().inflate(3.0);

        Vec3 sourcePos = source.position();

        return minecraft.level
                .getEntitiesOfClass(
                        entityType,
                        searchBox,
                        entity -> {
                            if (entity == source) {
                                return false;
                            }

                            if (entity.isRemoved() ||
                                    !entity.isAlive()) {
                                return false;
                            }

                            return filter == null ||
                                    filter.test(entity);
                        })
                .stream()
                .min(
                        Comparator.comparingDouble(
                                entity -> entity.distanceToSqr(sourcePos)))
                .orElse(null);
    }

    private void glowHideon(ArmorStand hideonNameTag) {

        Entity target = findClosestEntity(
                hideonNameTag,
                Entity.class,
                entity -> entity instanceof Shulker ||
                        entity instanceof Display);

        if (target != null) {
            ArmorStandTracerRenderer.glow(
                    target,
                    255,
                    0,
                    255);
        }
    }

    private void glowSparkling(ArmorStand sparklingNameTag) {

        Entity target = findClosestEntity(
                sparklingNameTag,
                Entity.class,
                entity -> !(entity instanceof ArmorStand) &&
                        !(entity instanceof Display));

        if (target != null) {
            ArmorStandTracerRenderer.glow(
                    target,
                    255,
                    255,
                    140);
        }
    }

    private void glowDuplico(ArmorStand duplicoNameTag) {

        Display.ItemDisplay display = findClosestEntity(
                duplicoNameTag,
                Display.ItemDisplay.class,
                null);

        if (display != null) {
            ArmorStandTracerRenderer.glow(
                    display,
                    255,
                    0,
                    0);
        }
    }

    private static String getHeadTexture(ItemStack stack) {
        if (!stack.is(Items.PLAYER_HEAD)) {
            return "";
        }

        ResolvableProfile profile = stack.get(DataComponents.PROFILE);

        if (profile == null) {
            return "";
        }

        return profile.partialProfile().properties().get("textures").stream()
                .filter(java.util.Objects::nonNull)
                .map(com.mojang.authlib.properties.Property::value)
                .findFirst()
                .orElse("");
    }

    private void chat(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal(text));
        }
    }

    public String titleDecor(String text) {
        return "§b[§6§k0§r§b]§r §6" + text + " §b[§6§k0§r§b]§r";
    }
}
