package com.reap.client;

import com.reap.ReapData;
import com.reap.ReapMod;
import com.reap.ReapNet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

import java.util.UUID;

public class ReapClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(ReapMod.GOO, RenderLayer.getTranslucent());

        ClientPlayNetworking.registerGlobalReceiver(ReapNet.SYNC, (client, handler, buf, sender) -> {
            UUID id = buf.readUuid();
            ReapData d = ReapData.read(buf);
            client.execute(() -> ClientData.MAP.put(id, d));
        });

        ClientPlayNetworking.registerGlobalReceiver(ReapNet.CUT, (client, handler, buf, sender) -> {
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            UUID owner = buf.readUuid();
            int amount = buf.readVarInt();
            int kind = buf.readByte();
            client.execute(() -> Fx.onCut(x, y, z, owner, amount, kind));
        });

        ClientPlayNetworking.registerGlobalReceiver(ReapNet.LEVEL_UP, (client, handler, buf, sender) -> {
            int level = buf.readVarInt();
            client.execute(() -> {
                ClientData.levelUpAt = System.currentTimeMillis();
                ClientData.levelUpLevel = level;
                client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f));
                Fx.onLevelUp();
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientData.MAP.clear();
            Fx.clear();
        });

        WorldRenderEvents.AFTER_ENTITIES.register(SpoonRenderer::render);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(Fx::render);
        HudRenderCallback.EVENT.register(Hud::render);
    }
}
