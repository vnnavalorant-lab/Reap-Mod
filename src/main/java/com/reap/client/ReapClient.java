package com.reap.client;

import com.reap.ReapData;
import com.reap.ReapMod;
import com.reap.ReapNet;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.block.Block;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.render.RenderLayer;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

import java.util.UUID;

public class ReapClient implements ClientModInitializer {
    public static KeyBinding REBIRTH_KEY;

    @Override
    public void onInitializeClient() {
        for (Block b : ReapMod.JELLY) BlockRenderLayerMap.INSTANCE.putBlock(b, RenderLayer.getTranslucent());
        for (Block b : ReapMod.TRAIN_VIS) BlockRenderLayerMap.INSTANCE.putBlock(b, RenderLayer.getTranslucent());

        BlockEntityRendererFactories.register(ReapMod.PAD_BE, ctx -> new ReturnPadRenderer(ctx.getTextRenderer()));
        BlockEntityRendererFactories.register(ReapMod.TRAIN_BE, ctx -> new TrainJellyRenderer(ctx.getTextRenderer()));

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
            int tier = buf.readByte();
            client.execute(() -> Fx.onCut(x, y, z, owner, amount, kind, tier));
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

        ClientPlayNetworking.registerGlobalReceiver(ReapNet.OPEN_REBIRTH, (client, handler, buf, sender) ->
                client.execute(() -> client.setScreen(new RebirthScreen())));

        REBIRTH_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.reap.rebirth", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.reap"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (REBIRTH_KEY.wasPressed()) {
                if (client.player != null && client.currentScreen == null) client.setScreen(new RebirthScreen());
            }
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
