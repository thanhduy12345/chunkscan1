package com.example.chunkscan;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

public class ChunkScanClient implements ClientModInitializer {
    private static final int THRESHOLD = 15;
    private static boolean enabled = true;

    @Override
    public void onInitializeClient() {
        ChunkHighlight.register();

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
            if (count >= THRESHOLD && ChunkHighlight.FLAGGED.add(pos)) {
                int n = count;
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    SystemToast.add(mc.getToastManager(),
                            SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                            Component.literal("ChunkFinder"),
                            Component.literal("X: " + pos.getMinBlockX() + " Z: " + pos.getMinBlockZ()));
                    if (mc.player != null) {
                        mc.player.displayClientMessage(Component.literal("[ChunkScan] " + n
                                + " storage blocks near x=" + pos.getMinBlockX()
                                + " z=" + pos.getMinBlockZ()), false);
                    }
                });
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("chunkscan").executes(ctx -> {
                    enabled = !enabled;
                    if (enabled) ChunkHighlight.FLAGGED.clear();
                    ctx.getSource().sendFeedback(Component.literal("ChunkScan " + (enabled ? "ON" : "OFF")));
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
