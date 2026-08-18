package com.safari.mod.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.safari.mod.SafariModClient;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArmorStandTracerRenderer {

    private static boolean initialized = false;

    private static final Set<UUID> entitiesToDraw =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private ArmorStandTracerRenderer() {}

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;

        LevelRenderEvents.END_MAIN.register(
                ArmorStandTracerRenderer::render
        );
    }

    public static void drawLineTo(Entity entity) {
        if (entity == null || !entity.isAlive()) {
            return;
        }

        entitiesToDraw.add(entity.getUUID());
    }

    public static void removeLineTo(Entity entity) {
        if (entity == null) {
            return;
        }

        entitiesToDraw.remove(entity.getUUID());
    }

    public static void clear() {
        entitiesToDraw.clear();
    }

    private static void render(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null) {
            entitiesToDraw.clear();
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        PoseStack poseStack = context.poseStack();
        MultiBufferSource.BufferSource bufferSource = context.bufferSource();

        VertexConsumer lines = bufferSource.getBuffer(RenderTypes.LINES);

        if (lines == null) {
            return;
        }

        float partialTick =
                mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        Vec3 cameraPos = camera.position();

        double playerX = lerp(
                partialTick,
                mc.player.xo,
                mc.player.getX()
        );

        double playerY = lerp(
                partialTick,
                mc.player.yo,
                mc.player.getY()
        );

        double playerZ = lerp(
                partialTick,
                mc.player.zo,
                mc.player.getZ()
        );

        double eyeY = playerY + mc.player.getEyeHeight();

        Vec3 look = Vec3.directionFromRotation(
                camera.xRot(),
                camera.yRot()
        );

        Vec3 startWorld = new Vec3(
                playerX,
                eyeY,
                playerZ
        ).add(look.scale(0.20));

        float startX =
                (float) (startWorld.x - cameraPos.x);

        float startY =
                (float) (startWorld.y - cameraPos.y);

        float startZ =
                (float) (startWorld.z - cameraPos.z);

        for (UUID uuid : entitiesToDraw) {

            Entity entity = mc.level.getEntity(uuid);

            if (entity == null ||
                    entity.isRemoved() ||
                    !entity.isAlive()) {
                continue;
            }

            double targetX = lerp(
                    partialTick,
                    entity.xo,
                    entity.getX()
            );

            double targetY = lerp(
                    partialTick,
                    entity.yo,
                    entity.getY()
            );

            double targetZ = lerp(
                    partialTick,
                    entity.zo,
                    entity.getZ()
            );

            targetY -= 1.0;

            Vec3 targetWorld = new Vec3(
                    targetX,
                    targetY,
                    targetZ
            );

            float targetRelX =
                    (float) (targetWorld.x - cameraPos.x);

            float targetRelY =
                    (float) (targetWorld.y - cameraPos.y);

            float targetRelZ =
                    (float) (targetWorld.z - cameraPos.z);

            Vec3 lineDirection =
                    targetWorld.subtract(startWorld);

            double length = lineDirection.length();

            if (length < 0.0001) {
                continue;
            }

            Vec3 normal = lineDirection.normalize();

            lines.addVertex(
                    poseStack.last(),
                    startX,
                    startY,
                    startZ
            )
                    .setColor(0, 255, 255, 255)
                    .setNormal(
                            poseStack.last(),
                            (float) normal.x,
                            (float) normal.y,
                            (float) normal.z
                    )
                    .setLineWidth(2.0F);

            lines.addVertex(
                    poseStack.last(),
                    targetRelX,
                    targetRelY,
                    targetRelZ
            )
                    .setColor(0, 255, 255, 255)
                    .setNormal(
                            poseStack.last(),
                            (float) normal.x,
                            (float) normal.y,
                            (float) normal.z
                    )
                    .setLineWidth(2.0F);
        }
    }

    private static double lerp(
            float partialTick,
            double previous,
            double current
    ) {
        return previous +
                (current - previous) * partialTick;
    }
}