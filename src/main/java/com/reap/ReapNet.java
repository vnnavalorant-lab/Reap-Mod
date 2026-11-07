package com.reap;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public final class ReapNet {
    public static final Identifier SYNC = new Identifier(ReapMod.ID, "sync");
    public static final Identifier CUT = new Identifier(ReapMod.ID, "cut");
    public static final Identifier LEVEL_UP = new Identifier(ReapMod.ID, "level_up");

    private ReapNet() {}

    public static void sendSync(MinecraftServer server, UUID id, ReapData d) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(id);
        d.write(buf);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, SYNC, PacketByteBufs.copy(buf));
        }
    }

    public static void sendSyncTo(ServerPlayerEntity to, UUID id, ReapData d) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(id);
        d.write(buf);
        ServerPlayNetworking.send(to, SYNC, buf);
    }

    /** kind 0 = gosma cortada, 1 = inimigo acertado. */
    public static void sendCut(ServerWorld w, Vec3d at, UUID owner, int amount, int kind) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeDouble(at.x);
        buf.writeDouble(at.y);
        buf.writeDouble(at.z);
        buf.writeUuid(owner);
        buf.writeVarInt(amount);
        buf.writeByte(kind);
        for (ServerPlayerEntity p : PlayerLookup.around(w, at, 64)) {
            ServerPlayNetworking.send(p, CUT, PacketByteBufs.copy(buf));
        }
    }

    public static void sendLevelUp(ServerPlayerEntity p, int level) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(level);
        ServerPlayNetworking.send(p, LEVEL_UP, buf);
    }
}
