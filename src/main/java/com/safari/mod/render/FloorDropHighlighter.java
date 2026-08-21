package com.safari.mod.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FloorDropHighlighter {

    private static final Set<BlockPos> blocksToHighlight = ConcurrentHashMap.newKeySet();

    private static final RenderPipeline highlighter = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(
                            Identifier.fromNamespaceAndPath(
                                    "safari-mod",
                                    "pipeline/floor_drop_highlight"))
                    .withDepthStencilState(Optional.empty())
                    .build());

    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);

    private static final Vector3f MODEL_OFFSET = new Vector3f();

    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private static BufferBuilder buffer;
    private static MappableRingBuffer vertexBuffer;

    private FloorDropHighlighter() {
    }

    public static void init() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(
                FloorDropHighlighter::render);
    }

    public static void highlight(BlockPos pos) {
        if (pos == null) {
            return;
        }

        blocksToHighlight.add(pos.immutable());
    }

    public static void remove(BlockPos pos) {
        if (pos == null) {
            return;
        }

        blocksToHighlight.remove(pos);
    }

    public static void clear() {
        blocksToHighlight.clear();
    }

    private static void render(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || blocksToHighlight.isEmpty()) {
            return;
        }

        PoseStack poseStack = context.poseStack();

        Vec3 camera = context.levelState().cameraRenderState.pos;

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        buffer = new BufferBuilder(
                ALLOCATOR,
                highlighter.getVertexFormatMode(),
                highlighter.getVertexFormat());

        for (BlockPos pos : blocksToHighlight) {
            renderFilledBox(
                    poseStack.last().pose(),
                    buffer,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    pos.getX() + 1,
                    pos.getY() + 1,
                    pos.getZ() + 1);
        }

        poseStack.popPose();

        draw(minecraft);
    }

    private static void renderFilledBox(
            Matrix4fc matrix,
            BufferBuilder buffer,
            float minX,
            float minY,
            float minZ,
            float maxX,
            float maxY,
            float maxZ) {
        float red = 0.0f;
        float green = 1.0f;
        float blue = 0.0f;
        float alpha = 0.35f;

        buffer.addVertex(matrix, minX, minY, maxZ)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, minY, maxZ)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, maxY, maxZ)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, minX, maxY, maxZ)
                .setColor(red, green, blue, alpha);

        buffer.addVertex(matrix, maxX, minY, minZ)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, minX, minY, minZ)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, minX, maxY, minZ)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, maxY, minZ)
                .setColor(red, green, blue, alpha);

        buffer.addVertex(matrix, minX, minY, minZ)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, minX, minY, maxZ)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, minX, maxY, maxZ)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, minX, maxY, minZ)
                .setColor(red, green, blue, alpha);

        buffer.addVertex(matrix, maxX, minY, maxZ)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, minY, minZ)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, maxY, minZ)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, maxY, maxZ)
                .setColor(red, green, blue, alpha);

        buffer.addVertex(matrix, minX, maxY, maxZ)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, maxY, maxZ)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, maxY, minZ)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, minX, maxY, minZ)
                .setColor(red, green, blue, alpha);

        buffer.addVertex(matrix, minX, minY, minZ)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, minY, minZ)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, maxX, minY, maxZ)
                .setColor(red, green, blue, alpha);
        buffer.addVertex(matrix, minX, minY, maxZ)
                .setColor(red, green, blue, alpha);
    }

    private static void draw(Minecraft minecraft) {
        MeshData mesh = buffer.buildOrThrow();

        MeshData.DrawState drawParameters = mesh.drawState();
        VertexFormat format = drawParameters.format();

        int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();

        if (vertexBuffer == null ||
                vertexBuffer.size() < vertexBufferSize) {

            if (vertexBuffer != null) {
                vertexBuffer.close();
            }

            vertexBuffer = new MappableRingBuffer(
                    () -> "safari-mod floor drop highlight",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                    vertexBufferSize);
        }

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();

        try (
                GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(
                        vertexBuffer.currentBuffer()
                                .slice(0, mesh.vertexBuffer().remaining()),
                        false,
                        true)) {
            MemoryUtil.memCopy(
                    mesh.vertexBuffer(),
                    mappedView.data());
        }

        GpuBuffer vertices = vertexBuffer.currentBuffer();

        GpuBuffer indices;
        VertexFormat.IndexType indexType;

        if (highlighter.getVertexFormatMode() == VertexFormat.Mode.QUADS) {

            mesh.sortQuads(
                    ALLOCATOR,
                    RenderSystem.getProjectionType().vertexSorting());

            indices = highlighter.getVertexFormat()
                    .uploadImmediateIndexBuffer(
                            mesh.indexBuffer());

            indexType = mesh.drawState().indexType();

        } else {

            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(
                    highlighter.getVertexFormatMode());

            indices = shapeIndexBuffer.getBuffer(
                    drawParameters.indexCount());

            indexType = shapeIndexBuffer.type();
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrix(),
                COLOR_MODULATOR,
                MODEL_OFFSET,
                TEXTURE_MATRIX);

        try (
                RenderPass renderPass = RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(
                                () -> "safariUtils floor drop highlight",
                                minecraft.getMainRenderTarget()
                                        .getColorTextureView(),
                                OptionalInt.empty(),
                                minecraft.getMainRenderTarget()
                                        .getDepthTextureView(),
                                OptionalDouble.empty())) {

            renderPass.setPipeline(highlighter);

            RenderSystem.bindDefaultUniforms(renderPass);

            renderPass.setUniform(
                    "DynamicTransforms",
                    dynamicTransforms);

            renderPass.setVertexBuffer(0, vertices);
            renderPass.setIndexBuffer(indices, indexType);

            renderPass.drawIndexed(
                    0,
                    0,
                    drawParameters.indexCount(),
                    1);
        }

        mesh.close();
        vertexBuffer.rotate();
        buffer = null;
    }
}