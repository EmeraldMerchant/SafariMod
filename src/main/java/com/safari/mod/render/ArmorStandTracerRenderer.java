package com.safari.mod.render;

import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArmorStandTracerRenderer {

    private static boolean initialized = false;
    private static final Map<UUID, Integer> entitiesToDraw =
            new ConcurrentHashMap<>();

    private ArmorStandTracerRenderer() {}

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
    }

    public static void drawLineTo(Entity entity) {
        glow(entity, 0, 255, 255);
    }

    public static void glow(Entity entity, int red, int green, int blue) {
        if (entity == null ||
                entity.isRemoved() ||
                !entity.isAlive()) {
            return;
        }

        red = clamp(red);
        green = clamp(green);
        blue = clamp(blue);

        int rgb =
                (red << 16) |
                (green << 8) |
                blue;

        entitiesToDraw.put(entity.getUUID(), rgb);
    }

    public static void glow(Entity entity, int rgb) {
        if (entity == null ||
                entity.isRemoved() ||
                !entity.isAlive()) {
            return;
        }

        entitiesToDraw.put(
                entity.getUUID(),
                rgb & 0xFFFFFF
        );
    }

    public static void setColor(
            Entity entity,
            int red,
            int green,
            int blue
    ) {
        glow(entity, red, green, blue);
    }

    public static void removeLineTo(Entity entity) {
        removeGlow(entity);
    }

    public static void removeGlow(Entity entity) {
        if (entity == null) {
            return;
        }

        entitiesToDraw.remove(entity.getUUID());
    }

    public static void clear() {
        entitiesToDraw.clear();
    }

    public static boolean hasGlow(Entity entity) {
        return entity != null &&
                entitiesToDraw.containsKey(entity.getUUID());
    }

    public static int getGlowColor(Entity entity) {
        if (entity == null) {
            return -1;
        }

        return entitiesToDraw.getOrDefault(
                entity.getUUID(),
                -1
        );
    }

    public static int getGlowColor(UUID uuid) {
        return entitiesToDraw.getOrDefault(
                uuid,
                -1
        );
    }

    /**
     * Remove stale entities.
     */
    public static void cleanup() {
        if (entitiesToDraw.isEmpty()) {
            return;
        }

        var mc = net.minecraft.client.Minecraft.getInstance();

        if (mc.level == null) {
            entitiesToDraw.clear();
            return;
        }

        entitiesToDraw.keySet().removeIf(uuid -> {
            Entity entity = mc.level.getEntity(uuid);

            return entity == null ||
                    entity.isRemoved() ||
                    !entity.isAlive();
        });
    }

    public static Set<UUID> getGlowingEntities() {
        return Collections.unmodifiableSet(
                entitiesToDraw.keySet()
        );
    }

    private static int clamp(int value) {
        return Math.max(
                0,
                Math.min(255, value)
        );
    }
}