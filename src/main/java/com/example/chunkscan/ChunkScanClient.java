package com.example.chunkscan;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

import java.util.HashSet;
import java.util.Set;

public class ChunkScanClient implements ClientModInitializer {
    private static final int THRESHOLD = 15;
    private static final Set<ChunkPos> reported = new HashSet<>();
    private static boolean enabled = true;

    @Override
    public void onInitializeClient() {
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (!enabled) return;
            int count = 0;
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                        || be instanceof ShulkerBoxBlockEntity || be instanceof HopperBlockEntity) {
                    count++;
                }
            }
            ChunkPos pos = chunk.getPos();
            if (count >= THRESHOLD && reported.add(pos)) {
                int n = count;
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.sendMessage(Text.literal("[ChunkScan] " + n
                                + " storage blocks near x=" + pos.getStartX()
                                + " z=" + pos.getStartZ()), false);
                    }
                });
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("chunkscan").executes(ctx -> {
                    enabled = !enabled;
                    if (enabled) reported.clear();
                    ctx.getSource().sendFeedback(Text.literal("ChunkScan " + (enabled ? "ON" : "OFF")));
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
