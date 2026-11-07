package com.reap;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class ReapMod implements ModInitializer {
    public static final String ID = "reap";
    public static final int REBIRTH_LEVEL = 50;
    public static final int GOO_REGEN_TICKS = 200;

    public static Block GOO;

    private record Regen(RegistryKey<World> dim, BlockPos pos, long at) {}

    private static final List<Regen> REGEN = new ArrayList<>();
    private static final Set<UUID> DIRTY = new HashSet<>();
    private static final Random RNG = new Random();

    @Override
    public void onInitialize() {
        GOO = Registry.register(Registries.BLOCK, new Identifier(ID, "goo"),
                new GlassBlock(AbstractBlock.Settings.create()
                        .mapColor(MapColor.PINK)
                        .strength(0.4f)
                        .sounds(BlockSoundGroup.SLIME)
                        .nonOpaque()
                        .suffocates((s, w, p) -> false)
                        .blockVision((s, w, p) -> false)));
        Registry.register(Registries.ITEM, new Identifier(ID, "goo"), new BlockItem(GOO, new Item.Settings()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(e -> e.add(GOO));

        ServerTickEvents.END_SERVER_TICK.register(ReapMod::tick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ReapState st = ReapState.get(server);
            for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
                ReapNet.sendSyncTo(handler.player, other.getUuid(), st.get(other.getUuid()));
            }
            ReapNet.sendSync(server, handler.player.getUuid(), st.get(handler.player.getUuid()));
        });

        CommandRegistrationCallback.EVENT.register(ReapCommands::register);
    }

    public static void markSync(MinecraftServer server, UUID id) {
        ReapState.get(server).markDirty();
        DIRTY.add(id);
    }

    // ------------------------------------------------------------------ tick

    private static void tick(MinecraftServer server) {
        ReapState st = ReapState.get(server);

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            ReapData d = st.get(p.getUuid());
            if (d.enabled) spoons(server, st, p, d);
        }

        long now = server.getTicks();
        Iterator<Regen> it = REGEN.iterator();
        while (it.hasNext()) {
            Regen r = it.next();
            if (now < r.at()) continue;
            ServerWorld w = server.getWorld(r.dim());
            if (w != null && w.getBlockState(r.pos()).isAir()) {
                w.setBlockState(r.pos(), GOO.getDefaultState());
                w.playSound(null, r.pos(), SoundEvents.BLOCK_SLIME_BLOCK_PLACE, SoundCategory.BLOCKS, 0.4f, 1.3f);
            }
            it.remove();
        }

        if (!DIRTY.isEmpty()) {
            for (UUID id : DIRTY) ReapNet.sendSync(server, id, st.get(id));
            DIRTY.clear();
        }
    }

    private static void spoons(MinecraftServer server, ReapState st, ServerPlayerEntity p, ReapData d) {
        ServerWorld w = p.getServerWorld();
        long time = w.getTime();
        int n = d.spoons();
        Vec3d c = p.getPos().add(0, SpoonLogic.CENTER_Y, 0);
        float dmg = 3f + d.level * 0.2f + d.rebirths * 1.5f;

        for (int i = 0; i < n; i++) {
            for (int s = 0; s < 2; s++) { // 2 amostras por tick: não "atravessa" alvos
                double a = SpoonLogic.angle(time, 0f, i, n) - s * SpoonLogic.ORBIT_SPEED * 0.5;
                double y = c.y + SpoonLogic.bob(time, 0f, i);
                double dx = Math.cos(a), dz = Math.sin(a);
                for (double r = SpoonLogic.HANDLE_R + 0.8; r <= SpoonLogic.HANDLE_R + SpoonLogic.LENGTH; r += 0.5) {
                    Vec3d pt = new Vec3d(c.x + dx * r, y, c.z + dz * r);
                    hitEntities(server, st, w, p, d, pt, dmg);
                    hitGoo(server, st, w, p, d, pt);
                }
            }
        }
    }

    private static void hitEntities(MinecraftServer server, ReapState st, ServerWorld w, ServerPlayerEntity p,
                                    ReapData d, Vec3d pt, float dmg) {
        Box box = Box.of(pt, 1.1, 1.0, 1.1);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box,
                x -> x != p && x.isAlive() && !(x instanceof ArmorStandEntity))) {
            if (e instanceof PlayerEntity && !server.isPvpEnabled()) continue;
            if (e.damage(w.getDamageSources().playerAttack(p), dmg)) {
                reward(server, st, w, p, d, d.multiplier() * 2, e.getPos().add(0, e.getHeight() * 0.6, 0), 1);
            }
        }
    }

    private static void hitGoo(MinecraftServer server, ReapState st, ServerWorld w, ServerPlayerEntity p,
                               ReapData d, Vec3d pt) {
        BlockPos base = BlockPos.ofFloored(pt);
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos bp = base.up(dy);
            if (!w.getBlockState(bp).isOf(GOO)) continue;
            w.setBlockState(bp, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            w.playSound(null, bp, SoundEvents.BLOCK_SLIME_BLOCK_BREAK, SoundCategory.PLAYERS,
                    0.7f, 0.8f + RNG.nextFloat() * 0.5f);
            REGEN.add(new Regen(w.getRegistryKey(), bp.toImmutable(), server.getTicks() + GOO_REGEN_TICKS));
            reward(server, st, w, p, d, d.multiplier(), Vec3d.ofCenter(bp), 0);
        }
    }

    private static void reward(MinecraftServer server, ReapState st, ServerWorld w, ServerPlayerEntity p,
                               ReapData d, int amount, Vec3d at, int kind) {
        int spoonsBefore = d.spoons();
        d.coins += amount;
        d.xp += amount;
        boolean up = false;
        while (d.xp >= ReapData.xpNeeded(d.level)) {
            d.xp -= ReapData.xpNeeded(d.level);
            d.level++;
            up = true;
        }
        markSync(server, p.getUuid());
        ReapNet.sendCut(w, at, p.getUuid(), amount, kind);
        if (up) {
            ReapNet.sendLevelUp(p, d.level);
            if (d.spoons() > spoonsBefore) {
                p.sendMessage(Text.literal("Nova colher desbloqueada!").formatted(Formatting.AQUA, Formatting.BOLD), true);
            }
        }
    }
}
