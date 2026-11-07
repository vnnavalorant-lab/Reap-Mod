package com.reap;

import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.*;

/** Lógica das plataformas de retorno: junta as plataformas encostadas, dá os wins e manda o jogador ao spawn. */
public final class ReturnPads {
    /** Tamanho máximo de um grupo (limite de segurança). */
    public static final int MAX_GROUP = 400;
    /** Ticks até a mesma pessoa poder ativar de novo. */
    public static final int COOLDOWN_TICKS = 20;

    /**
     * Grupo de plataformas conectadas. Funciona como UM bloco só: o texto e os wins pertencem ao bloco "âncora"
     * (o do canto: menor X e, em empate, menor Z). Os wins do grupo NÃO são somados.
     * cx/cz = centro do grupo (onde o letreiro é desenhado).
     */
    public record Group(int wins, int size, BlockPos anchor, double cx, double cz) {}

    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    private ReturnPads() {}

    /** Procura todas as plataformas ligadas (lados) à posição dada, no mesmo nível. */
    public static Group group(World w, BlockPos start) {
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(start);
        seen.add(start.asLong());
        int size = 0, anchorWins = 0;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        BlockPos anchor = null;
        while (!queue.isEmpty() && size < MAX_GROUP) {
            BlockPos p = queue.poll();
            if (!(w.getBlockState(p).getBlock() instanceof ReturnPadBlock pad)) continue;
            size++;
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
            if (anchor == null || p.getX() < anchor.getX()
                    || (p.getX() == anchor.getX() && p.getZ() < anchor.getZ())) {
                anchor = p;
                anchorWins = pad.wins;
            }
            for (Direction d : Direction.Type.HORIZONTAL) {
                BlockPos n = p.offset(d);
                if (seen.add(n.asLong())) queue.add(n);
            }
        }
        if (size == 0) return new Group(0, 0, start, start.getX() + 0.5, start.getZ() + 0.5);
        return new Group(anchorWins, size, anchor, (minX + maxX + 1) / 2.0, (minZ + maxZ + 1) / 2.0);
    }

    public static String label(int wins) {
        return "+" + wins + (wins == 1 ? " WIN" : " WINS");
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTicks();
        ReapState st = null;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || !p.isAlive() || !p.isOnGround()) continue;
            Long cd = COOLDOWN.get(p.getUuid());
            if (cd != null && now < cd) continue;

            ServerWorld w = p.getServerWorld();
            BlockPos feet = p.getBlockPos();
            BlockState bs = w.getBlockState(feet);
            if (!(bs.getBlock() instanceof ReturnPadBlock)) continue;

            Group g = group(w, feet);
            int wins = g.wins(); // wins do bloco-âncora do grupo (sem soma)
            if (wins <= 0) continue;

            if (st == null) st = ReapState.get(server);
            ReapData d = st.get(p.getUuid());
            d.wins += wins;
            ReapMod.markSync(server, p.getUuid());
            COOLDOWN.put(p.getUuid(), now + COOLDOWN_TICKS);

            p.sendMessage(Text.literal(label(wins)).formatted(Formatting.GOLD, Formatting.BOLD), true);
            w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP,
                    SoundCategory.PLAYERS, 0.8f, 1.3f);
            returnToSpawn(server, p);
        }
    }

    /** Spawn inicial do mundo (para trocar pelo spawn pessoal/cama, use p.getSpawnPointPosition()). */
    public static void returnToSpawn(MinecraftServer server, ServerPlayerEntity p) {
        ServerWorld ow = server.getOverworld();
        BlockPos sp = ow.getSpawnPos();
        int y = ow.getTopY(Heightmap.Type.MOTION_BLOCKING, sp.getX(), sp.getZ());
        p.teleport(ow, sp.getX() + 0.5, y, sp.getZ() + 0.5, p.getYaw(), p.getPitch());
        p.fallDistance = 0;
        ow.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.PLAYERS, 0.6f, 1.2f);
    }
}
