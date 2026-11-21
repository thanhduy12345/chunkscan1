package com.example.chunkscan;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkHighlight {
    public static final Set<ChunkPos> FLAGGED = ConcurrentHashMap.newKeySet();

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("chunkscan", "pipeline/chunk_green"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build());

    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static BufferBuilder buffer;
    private static MappableRingBuffer vertexBuffer;

    public static void register() {
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(ChunkHighlight::onRender);
    }

    private static void onRender(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || FLAGGED.isEmpty()) return;

        PoseStack matrices = context.matrices();
        Vec3 camera = context.worldState().cameraRenderState.pos;
        float y = (float) client.player.getY() + 0.03f;

        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        for (ChunkPos pos : FLAGGED) {
            float x0 = pos.getMinBlockX();
            float z0 = pos.getMinBlockZ();
            if (Math.abs(x0 + 8 - camera.x) > 160 || Math.abs(z0 + 8 - camera.z) > 160) continue;
            if (buffer == null) {
                buffer = new BufferBuilder(ALLOCATOR, PIPELINE.getVertexFormatMode(), PIPELINE.getVertexFormat());
            }
            quad(matrices.last().pose(), x0, y, z0, x0 + 16, z0 + 16);
        }
        matrices.popPose();

        if (buffer != null) draw(client);
    }

    private static void quad(Matrix4fc m, float x0, float y, float z0, float x1, float z1) {
        float r = 0f, g = 1f, b = 0f, a = 0.35f;
        buffer.addVertex(m, x0, y, z1).setColor(r, g, b, a);
        buffer.addVertex(m, x1, y, z1).setColor(r, g, b, a);
        buffer.addVertex(m, x1, y, z0).setColor(r, g, b, a);
        buffer.addVertex(m, x0, y, z0).setColor(r, g, b, a);
        buffer.addVertex(m, x0, y, z0).setColor(r, g, b, a);
        buffer.addVertex(m, x1, y, z0).setColor(r, g, b, a);
        buffer.addVertex(m, x1, y, z1).setColor(r, g, b, a);
        buffer.addVertex(m, x0, y, z1).setColor(r, g, b, a);
    }

    private static void draw(Minecraft client) {
        MeshData built = buffer.buildOrThrow();
        MeshData.DrawState params = built.drawState();
        VertexFormat format = params.format();

        int size = params.vertexCount() * format.getVertexSize();
        if (vertexBuffer == null || vertexBuffer.size() < size) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(() -> "chunkscan vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(
                vertexBuffer.currentBuffer().slice(0, built.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(built.vertexBuffer(), view.data());
        }
        GpuBuffer vertices = vertexBuffer.currentBuffer();

        built.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
        GpuBuffer indices = PIPELINE.getVertexFormat().uploadImmediateIndexBuffer(built.indexBuffer());
        VertexFormat.IndexType indexType = built.drawState().indexType();

        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "chunkscan render", client.getMainRenderTarget().getColorTextureView(),
                OptionalInt.empty(), client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, indexType);
            pass.drawIndexed(0, 0, pa
