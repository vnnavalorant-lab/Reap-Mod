package com.reap;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
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

    /** Ticks até a gelatina voltar depois de devorada (+ 40 por tier). */
    public static final int GOO_REGEN_TICKS = 200;
    /** Ticks sem ninguém comendo até a gelatina começar a se recuperar sozinha. */
    public static final int CHEW_IDLE_TICKS = 50;
    /** Fração do HP recuperada por tick quando ninguém está comendo (0.04 = ~1,2 s para curar tudo na fase 1).
     *  Fases mais altas se curam mais devagar: o valor é dividido por (1 + 0.5 * fase). */
    public static final float HEAL_PER_TICK = 0.04f;

    /**
     * As 9 gelatinas, da mais fácil para a mais difícil.
     * id = nome do bloco (precisa bater com tools/gen_assets.py), hp = "vida" da gelatina,
     * reward = moedas/XP base ao devorar (multiplicadas pelo multiplicador do jogador).
     */
    public record JellyType(String id, int rgb, int hp, int reward, MapColor map) {}

    public static final JellyType[] TYPES = {
            new JellyType("goo",        0xFF54BC,    14, 1, MapColor.PINK),     // rosa (a original)
            new JellyType("goo_red",    0xE8243A,   50, 4, MapColor.RED),
            new JellyType("goo_orange", 0xFF8A1C,   150, 12, MapColor.ORANGE),
            new JellyType("goo_yellow", 0xFFD628,   420, 35, MapColor.YELLOW),
            new JellyType("goo_green",  0x40D648,  1100, 100, MapColor.LIME),
            new JellyType("goo_cyan",   0x2CD6EC,  3000, 300, MapColor.CYAN),
            new JellyType("goo_blue",   0x3A62FF,  8500, 900, MapColor.BLUE),
            new JellyType("goo_purple", 0x9442EA, 24000, 2700, MapColor.PURPLE),
            new JellyType("goo_black",  0x342E54, 70000, 8000, MapColor.BLACK),
    };

    public static final Block[] JELLY = new Block[TYPES.length];

    /**
     * Plataformas de retorno, uma por fase: a de índice i tem a cor da gelatina TYPES[i].
     * Quantidade de wins de cada uma (precisa bater com PAD_WINS em tools/gen_assets.py).
     */
    public static final int[] PAD_WINS = {1, 3, 8, 20, 50, 120, 300, 750, 900};
    public static final Block[] PADS = new Block[PAD_WINS.length];
    public static BlockEntityType<ReturnPadBlockEntity> PAD_BE;

    /**
     * Gelatinas de treino (inquebráveis, ficam no lobby): dão XP quando as colheres encostam.
     * req = rebirths necessários, mult = multiplicador de XP. Precisa bater com TRAIN em tools/gen_assets.py.
     */
    public record TrainType(String id, String label, int req, float mult, int rgb) {}

    public static final TrainType[] TRAIN = {
            new TrainType("train_starter", "Starter",   0, 1f,   0x50D7FF),
            new TrainType("train_r1",      "Rebirth 1", 1, 1.5f, 0x5ADC50),
            new TrainType("train_r3",      "Rebirth 3", 3, 2f,   0xFFE25A),
            new TrainType("train_r5",      "Rebirth 5", 5, 3f,   0xFF3CC8),
            new TrainType("train_r6",      "Rebirth 6", 6, 9f,   0xBEEEFF),
    };
    /** Blocos colocáveis (controlador da estrutura 3x3x3). */
    public static final Block[] TRAIN_BLOCKS = new Block[TRAIN.length];
    /** Blocos "visuais" escondidos (sem item): só fornecem o modelo grande para o renderer. */
    public static final Block[] TRAIN_VIS = new Block[TRAIN.length];
    /** Parte invisível da estrutura (colisão). */
    public static Block TRAIN_PART;
    public static BlockEntityType<TrainJellyBlockEntity> TRAIN_BE;

    /** XP por contato = (1 + nível / 3) * multiplicador da gelatina. Aumente/diminua para acelerar o treino. */
    public static final float TRAIN_XP_BASE = 1f;
    public static final float TRAIN_XP_PER_LEVEL = 1f / 3f;

    public static String multLabel(float m) {
        return m == Math.floor(m) ? "x" + (int) m : "x" + m;
    }

    private record Regen(RegistryKey<World> dim, BlockPos pos, long at, int tier) {}
    private record Key(RegistryKey<World> dim, long pos) {}
    /** Dano acumulado numa gelatina que está sendo devorada. */
    private static final class Chew { float dmg; long last; }

    private static final List<Regen> REGEN = new ArrayList<>();
    private static final Map<Key, Chew> CHEW = new HashMap<>();
    private static final Set<UUID> DIRTY = new HashSet<>();
    private static final Random RNG = new Random();

    /** XP ganho treinando, acumulado para mostrar um único "+N XP" a cada meio segundo. */
    private static final class Acc { long xp; long next; ServerWorld w; Vec3d at; int idx; }
    private static final Map<UUID, Acc> TRAIN_ACC = new HashMap<>();
    private static final Map<UUID, Long> LOCK_MSG = new HashMap<>();

    @Override
    public void onInitialize() {
        for (int i = 0; i < TYPES.length; i++) {
            JellyType t = TYPES[i];
            Block b = Registry.register(Registries.BLOCK, new Identifier(ID, t.id()),
                    new JellyBlock(AbstractBlock.Settings.create()
                            .mapColor(t.map())
                            .strength(0.4f)
                            .sounds(BlockSoundGroup.SLIME)
                            .nonOpaque()
                            .ticksRandomly()
                            .suffocates((s, w, p) -> false)
                            .blockVision((s, w, p) -> false), i));
            Registry.register(Registries.ITEM, new Identifier(ID, t.id()), new BlockItem(b, new Item.Settings()));
            JELLY[i] = b;
        }
        for (int i = 0; i < PAD_WINS.length; i++) {
            String id = "return_" + PAD_WINS[i];
            Block b = Registry.register(Registries.BLOCK, new Identifier(ID, id),
                    new ReturnPadBlock(AbstractBlock.Settings.create()
                            .mapColor(MapColor.ORANGE)
                            .strength(0.5f)
                            .sounds(BlockSoundGroup.STONE)
                            .nonOpaque(), PAD_WINS[i]));
            Registry.register(Registries.ITEM, new Identifier(ID, id), new BlockItem(b, new Item.Settings()));
            PADS[i] = b;
        }
        PAD_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, new Identifier(ID, "return_pad"),
                FabricBlockEntityTypeBuilder.create(ReturnPadBlockEntity::new, PADS).build());

        for (int i = 0; i < TRAIN.length; i++) {
            TrainType t = TRAIN[i];
            Block b = Registry.register(Registries.BLOCK, new Identifier(ID, t.id()),
                    new TrainJellyBlock(AbstractBlock.Settings.create()
                            .mapColor(MapColor.CYAN)
                            .strength(-1.0f, 3600000.0f) // inquebrável (como bedrock)
                            .sounds(BlockSoundGroup.SLIME)
                            .nonOpaque()
                            .dropsNothing(), i));
            Registry.register(Registries.ITEM, new Identifier(ID, t.id()), new BlockItem(b, new Item.Settings()));
            TRAIN_BLOCKS[i] = b;
            TRAIN_VIS[i] = Registry.register(Registries.BLOCK, new Identifier(ID, t.id() + "_big"),
                    new Block(AbstractBlock.Settings.create().nonOpaque().strength(-1.0f, 3600000.0f).dropsNothing()));
        }
        TRAIN_PART = Registry.register(Registries.BLOCK, new Identifier(ID, "train_part"),
                new TrainJellyPartBlock(AbstractBlock.Settings.create()
                        .mapColor(MapColor.CYAN)
                        .strength(-1.0f, 3600000.0f)
                        .sounds(BlockSoundGroup.SLIME)
                        .nonOpaque()
                        .dropsNothing()
                        .suffocates((s, w, p) -> false)
                        .blockVision((s, w, p) -> false)));
        TRAIN_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, new Identifier(ID, "train_jelly"),
                FabricBlockEntityTypeBuilder.create(TrainJellyBlockEntity::new, TRAIN_BLOCKS).build());

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(e -> {
            for (Block b : JELLY) e.add(b);
            for (Block b : PADS) e.add(b);
            for (Block b : TRAIN_BLOCKS) e.add(b);
        });

        ServerTickEvents.END_SERVER_TICK.register(ReapMod::tick);
        ServerTickEvents.END_SERVER_TICK.register(ReturnPads::tick);

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

    /** Alguém está comendo essa gelatina agora? (usado pelo random tick para não resetar no meio da mordida) */
    public static boolean isChewing(ServerWorld w, BlockPos pos) {
        return CHEW.containsKey(new Key(w.getRegistryKey(), pos.asLong()));
    }

    // ------------------------------------------------------------------ vida da gelatina

    /** 0 = intacta; 1..7 = quanto já foi comida. */
    private static int stageOf(float dmg, int hp) {
        if (dmg <= 0f) return 0;
        return Math.min(7, 1 + (int) (dmg / hp * 7f));
    }

    private static float damageOfStage(int stage, int hp) {
        return stage <= 0 ? 0f : hp * ((stage - 1) + 0.5f) / 7f;
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

        // gelatinas que ninguém está comendo se recuperam aos poucos
        Iterator<Map.Entry<Key, Chew>> ci = CHEW.entrySet().iterator();
        while (ci.hasNext()) {
            Map.Entry<Key, Chew> e = ci.next();
            Chew c = e.getValue();
            if (now - c.last <= CHEW_IDLE_TICKS) continue;
            ServerWorld w = server.getWorld(e.getKey().dim());
            BlockPos bp = BlockPos.fromLong(e.getKey().pos());
            if (w == null || w.getChunkManager().getWorldChunk(bp.getX() >> 4, bp.getZ() >> 4) == null) {
                ci.remove();
                continue;
            }
            BlockState bs = w.getBlockState(bp);
            if (!(bs.getBlock() instanceof JellyBlock jb)) {
                ci.remove();
                continue;
            }
            int hp = TYPES[jb.tier].hp();
            c.dmg -= hp * HEAL_PER_TICK / (1f + 0.5f * jb.tier);
            int stage = stageOf(c.dmg, hp);
            if (c.dmg <= 0f) ci.remove();
            if (stage != bs.get(JellyBlock.BITES)) {
                w.setBlockState(bp, bs.with(JellyBlock.BITES, stage), Block.NOTIFY_ALL);
            }
        }

        // feedback do treino: "+N XP" a cada 10 ticks
        for (Iterator<Map.Entry<UUID, Acc>> ai = TRAIN_ACC.entrySet().iterator(); ai.hasNext(); ) {
            Map.Entry<UUID, Acc> e = ai.next();
            Acc a = e.getValue();
            if (now >= a.next) {
                if (a.xp > 0) {
                    ReapNet.sendCut(a.w, a.at, e.getKey(), (int) Math.min(a.xp, Integer.MAX_VALUE), 3, a.idx);
                    a.w.playSound(null, BlockPos.ofFloored(a.at), SoundEvents.BLOCK_SLIME_BLOCK_HIT,
                            SoundCategory.BLOCKS, 0.45f, 0.9f + RNG.nextFloat() * 0.3f);
                    a.xp = 0;
                    a.next = now + 10;
                } else if (now > a.next + 100) {
                    ai.remove();
                }
            }
        }

        // gelatinas devoradas voltam
        Iterator<Regen> it = REGEN.iterator();
        while (it.hasNext()) {
            Regen r = it.next();
            if (now < r.at()) continue;
            ServerWorld w = server.getWorld(r.dim());
            if (w != null && w.getBlockState(r.pos()).isAir()) {
                w.setBlockState(r.pos(), JELLY[r.tier()].getDefaultState());
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

        // quantas colheres encostaram em cada gelatina neste tick
        Map<Long, Integer> contacts = new HashMap<>();

        for (int i = 0; i < n; i++) {
            Set<Long> touched = new HashSet<>();
            for (int s = 0; s < 2; s++) { // 2 amostras por tick: não "atravessa" alvos
                double a = SpoonLogic.angle(time, 0f, i, n) - s * SpoonLogic.ORBIT_SPEED * 0.5;
                double y = c.y + SpoonLogic.bob(time, 0f, i);
                double dx = Math.cos(a), dz = Math.sin(a);
                for (double r = SpoonLogic.HANDLE_R + 0.8; r <= SpoonLogic.HANDLE_R + SpoonLogic.LENGTH; r += 0.5) {
                    Vec3d pt = new Vec3d(c.x + dx * r, y, c.z + dz * r);
                    hitEntities(server, st, w, p, d, pt, dmg);
                    touchJelly(w, pt, touched);
                }
            }
            for (long l : touched) contacts.merge(l, 1, Integer::sum);
        }

        if (!contacts.isEmpty()) {
            float power = d.bitePower();
            for (Map.Entry<Long, Integer> e : contacts.entrySet()) {
                chew(server, st, w, p, d, BlockPos.fromLong(e.getKey()), e.getValue(), power);
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
                reward(server, st, w, p, d, d.multiplier() * 2, e.getPos().add(0, e.getHeight() * 0.6, 0), 1, 0);
            }
        }
    }

    private static void touchJelly(ServerWorld w, Vec3d pt, Set<Long> out) {
        BlockPos base = BlockPos.ofFloored(pt);
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos bp = base.up(dy);
            BlockState bst = w.getBlockState(bp);
            Block bl = bst.getBlock();
            if (bl instanceof JellyBlock || bl instanceof TrainJellyBlock) {
                out.add(bp.asLong());
            } else if (bl instanceof TrainJellyPartBlock) {
                // partes da gelatina grande contam como o controlador (um contato por estrutura)
                BlockPos c = TrainJellyPartBlock.findController(w, bp, bst.get(TrainJellyPartBlock.LAYER));
                if (c != null) out.add(c.asLong());
            }
        }
    }

    /** Uma colher encostou: tira um pedaço. Quando o HP acaba a gelatina some e dá a recompensa. */
    private static void chew(MinecraftServer server, ReapState st, ServerWorld w, ServerPlayerEntity p,
                             ReapData d, BlockPos bp, int hits, float power) {
        BlockState bs = w.getBlockState(bp);
        if (bs.getBlock() instanceof TrainJellyBlock tb) {
            train(server, st, w, p, d, bp, tb, hits);
            return;
        }
        if (!(bs.getBlock() instanceof JellyBlock jb)) return;
        float dmg = hits * power;
        JellyType t = TYPES[jb.tier];
        Key key = new Key(w.getRegistryKey(), bp.asLong());
        long now = server.getTicks();

        Chew c = CHEW.get(key);
        if (c == null) {
            c = new Chew();
            c.dmg = damageOfStage(bs.get(JellyBlock.BITES), t.hp());
            CHEW.put(key, c);
        }
        c.dmg += dmg;
        c.last = now;
        Vec3d center = Vec3d.ofCenter(bp);

        if (c.dmg >= t.hp()) {
            CHEW.remove(key);
            w.setBlockState(bp, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            // evento vanilla 2001 = partículas de quebra do bloco + som de quebra
            w.syncWorldEvent(2001, bp, Block.getRawIdFromState(bs));
            REGEN.add(new Regen(w.getRegistryKey(), bp.toImmutable(), now + GOO_REGEN_TICKS + jb.tier * 40L, jb.tier));
            reward(server, st, w, p, d, d.multiplier() * t.reward(), center, 0, jb.tier);
            return;
        }

        int stage = stageOf(c.dmg, t.hp());
        boolean changed = stage != bs.get(JellyBlock.BITES);
        if (changed) {
            w.setBlockState(bp, bs.with(JellyBlock.BITES, stage), Block.NOTIFY_ALL);
            w.playSound(null, bp, SoundEvents.BLOCK_SLIME_BLOCK_HIT, SoundCategory.BLOCKS,
                    0.7f, 0.9f + stage * 0.06f + RNG.nextFloat() * 0.15f);
        }
        if (changed || RNG.nextInt(4) == 0) { // respingos pequenos a cada mordida
            ReapNet.sendCut(w, center, p.getUuid(), 0, 2, jb.tier);
        }
    }

    /** Gelatina de treino: nunca quebra; dá XP (se o jogador tiver os rebirths exigidos). */
    private static void train(MinecraftServer server, ReapState st, ServerWorld w, ServerPlayerEntity p,
                              ReapData d, BlockPos bp, TrainJellyBlock tb, int hits) {
        TrainType t = TRAIN[tb.index];
        long now = server.getTicks();
        if (d.rebirths < t.req()) {
            Long last = LOCK_MSG.get(p.getUuid());
            if (last == null || now - last > 40) {
                LOCK_MSG.put(p.getUuid(), now);
                p.sendMessage(Text.literal("Requer " + t.label()).formatted(Formatting.RED, Formatting.BOLD), true);
            }
            return;
        }
        long xp = Math.max(1L, Math.round((TRAIN_XP_BASE + d.level * TRAIN_XP_PER_LEVEL) * t.mult() * hits));
        addXp(server, p, d, xp);
        Acc a = TRAIN_ACC.computeIfAbsent(p.getUuid(), k -> new Acc());
        a.xp += xp;
        a.w = w;
        // ponto na superfície da gelatina (~1,75 bloco do centro), do lado do jogador
        double cx = bp.getX() + 0.5, cz = bp.getZ() + 0.5;
        double dx = p.getX() - cx, dz = p.getZ() - cz;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) { dx = 1; dz = 0; len = 1; }
        double ey = Math.max(bp.getY() + 0.4, Math.min(p.getY() + 1.0, bp.getY() + 2.5));
        a.at = new Vec3d(cx + dx / len * 1.75, ey, cz + dz / len * 1.75);
        a.idx = tb.index;
    }

    /** Soma XP, sobe de nível e avisa o cliente. */
    private static void addXp(MinecraftServer server, ServerPlayerEntity p, ReapData d, long xp) {
        int spoonsBefore = d.spoons();
        d.xp += xp;
        boolean up = false;
        while (d.xp >= ReapData.xpNeeded(d.level)) {
            d.xp -= ReapData.xpNeeded(d.level);
            d.level++;
            up = true;
        }
        markSync(server, p.getUuid());
        if (up) {
            ReapNet.sendLevelUp(p, d.level);
            if (d.spoons() > spoonsBefore) {
                p.sendMessage(Text.literal("Nova colher desbloqueada!").formatted(Formatting.AQUA, Formatting.BOLD), true);
            }
        }
    }

    private static void reward(MinecraftServer server, ReapState st, ServerWorld w, ServerPlayerEntity p,
                               ReapData d, int amount, Vec3d at, int kind, int tier) {
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
        ReapNet.sendCut(w, at, p.getUuid(), amount, kind, tier);
        if (up) {
            ReapNet.sendLevelUp(p, d.level);
            if (d.spoons() > spoonsBefore) {
                p.sendMessage(Text.literal("Nova colher desbloqueada!").formatted(Formatting.AQUA, Formatting.BOLD), true);
            }
        }
    }
}
