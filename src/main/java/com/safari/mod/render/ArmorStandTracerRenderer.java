package com.safari.mod.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import com.safari.mod.SafariModClient;

import java.util.UUID;

public final class ArmorStandTracerRenderer {

    private static boolean initialized = false;

    private ArmorStandTracerRenderer() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;

        LevelRenderEvents.END_MAIN.register(
                ArmorStandTracerRenderer::render
        );
    }

    private static void render(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null) {
            return;
        }

        Camera camera = mc.gameRenderer.getMainCamera();

        /*
         * IMPORTANT:
         *
         * Do NOT create a new PoseStack here.
         *
         * The PoseStack supplied by LevelRenderContext is already part
         * of Minecraft's world rendering transformation.
         */
        PoseStack poseStack = context.poseStack();

        MultiBufferSource.BufferSource bufferSource = context.bufferSource();

        VertexConsumer lines = bufferSource.getBuffer(RenderTypes.LINES);

        if (lines == null) {
            return;
        }

        Vec3 cameraPos = camera.position();

        /*
         * Player position for this render frame.
         *
         * Using the interpolated position avoids the visible jitter that
         * happens when mixing tick-position data with the render camera.
         */
        float partialTick =
                mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

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

        /*
         * Eye height at the current player position.
         */
        double eyeY = playerY + mc.player.getEyeHeight();

        /*
         * Camera forward direction.
         *
         * Unlike the old Camera.getLookVector() attempt, Minecraft's
         * rotation values can be converted directly into a direction.
         */
        Vec3 look = Vec3.directionFromRotation(
                camera.xRot(),
                camera.yRot()
        );

        /*
         * Start slightly in front of the player's eyes.
         *
         * 0.20 blocks is enough to make the line originate in front
         * of the face instead of visually clipping through it.
         */
        Vec3 startWorld = new Vec3(
                playerX,
                eyeY,
                playerZ
        ).add(look.scale(0.20));

        /*
         * Convert world coordinates into camera-relative coordinates.
         *
         * Because the context PoseStack is already the world-render
         * stack, these coordinates are relative to the camera.
         */
        float startX = (float) (startWorld.x - cameraPos.x);
        float startY = (float) (startWorld.y - cameraPos.y);
        float startZ = (float) (startWorld.z - cameraPos.z);

        for (Entity entity : mc.level.entitiesForRendering()) {

            if (!(entity instanceof ArmorStand armorStand)) {
                continue;
            }

            if (!armorStand.isAlive()) {
                continue;
            }

            UUID uuid = armorStand.getUUID();

            String name = armorStand.getCustomName() != null
                    ? armorStand.getCustomName().getString()
                    : "";

            boolean inHideyhoSet =
                    SafariModClient.hideyhoArmorStands.contains(uuid);

            boolean isSparkling =
                    name.contains(SafariModClient.TARGET_ARMOR_STAND_NAME);

            boolean isHideyhoName =
                    name.contains("Hideyho");

            if (!inHideyhoSet && !isSparkling && !isHideyhoName) {
                continue;
            }

            /*
             * Interpolate the armor stand position too.
             *
             * This is important because the camera is rendered between
             * ticks while entity coordinates can represent the previous
             * tick.
             */
            double targetX = lerp(
                    partialTick,
                    armorStand.xo,
                    armorStand.getX()
            );

            double targetY = lerp(
                    partialTick,
                    armorStand.yo,
                    armorStand.getY()
            );

            double targetZ = lerp(
                    partialTick,
                    armorStand.zo,
                    armorStand.getZ()
            );

            /*
             * Lower the target by exactly 1 block.
             */
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

            /*
             * Calculate a normal for the line.
             *
             * RenderTypes.LINES requires a normal. Using the direction
             * of the line gives the renderer a stable value rather than
             * constantly changing it based on the camera angle.
             */
            Vec3 lineDirection =
                    targetWorld.subtract(startWorld);

            double length = lineDirection.length();

            if (length < 0.0001) {
                continue;
            }

            Vec3 normal = lineDirection.normalize();

            /*
             * Add the two vertices making up the line.
             */
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
        return previous + (current - previous) * partialTick;
    }
}