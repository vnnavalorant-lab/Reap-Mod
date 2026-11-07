package com.reap.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.reap.JellyBlock;
import com.reap.ReapData;
import com.reap.SpoonLogic;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.LightType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * Efeitos visuais customizados (sem partículas vanilla):
 *  - rastro em fita contínuo atrás de cada colher (faixa horizontal + bandas verticais + núcleo branco)
 *  - disco de movimento girando + anel no chão (constantes enquanto as colheres existirem)
 *  - destruição da gelatina: pedaços macios que balançam/esticam, fios que arrebentam, quicam, grudam na parede
 *    e escorrem ou viram poças brilhantes; mais spray fino de gotas
 *  - onda de choque, flash, textos "+N" flutuantes
 */
public final class Fx {
    private static final Identifier T_GLOW = new Identifier("reap", "textures/fx/glow.png");
    private static final Identifier T_TRAIL = new Identifier("reap", "textures/fx/trail.png");
    private static final Identifier T_SWIRL = new Identifier("reap", "textures/fx/swirl.png");
    private static final Identifier T_RING = new Identifier("reap", "textures/fx/ring.png");
    private static final Identifier T_DROP = new Identifier("reap", "textures/fx/drop.png");
    private static final Identifier T_BLOB = new Identifier("reap", "textures/fx/jblob.png");
    private static final Identifier T_BLOB_SPEC = new Identifier("reap", "textures/fx/jblob_spec.png");
    private static final Identifier T_PUDDLE = new Identifier("reap", "textures/fx/puddle.png");
    private static final Identifier T_PUDDLE_SPEC = new Identifier("reap", "textures/fx/puddle_spec.png");

    private static final double TAU = Math.PI * 2;
    private static final long TRAIL_NS = 320_000_000L;
    private static final double R_OUT = SpoonLogic.HANDLE_R + SpoonLogic.LENGTH - 0.05;

    private static final float[] WHITE = {1f, 1f, 1f};

    // ------------------------------------------------------------ estado
    private static final class Drop { double x, y, z, vx, vy, vz; float size, age, life; float[] c; }
    private static final class Ring { double x, y, z; float age, life, radius, rise; float[] c; }
    private static final class Flash { double x, y, z; float age, life, size; float[] c; }
    /** Poça de gelatina no chão (oval, com brilho). lit = iluminação do local no momento em que caiu. */
    private static final class Splat { double x, y, z; float age, life, rx, rz, rot, lit = 1f; float[] c; }
    /** Pedaço de gelatina: voa, balança, quica, gruda na parede e escorre, ou vira poça. */
    private static final class Chunk {
        double x, y, z, vx, vy, vz;
        float size, age, jigAmp, jigT;
        int bounces;
        boolean stuck, dead;
        float[] c;
    }
    /** Fio de gelatina esticado entre o ponto onde o bloco estava e um pedaço que se afasta. */
    private static final class Strand { double ox, oy, oz; Chunk ch; float age; float[] c; }
    private static final class Txt { double x, y, z; String s; float age, life; int rgb; }

    private record Sample(double x, double y, double z, double ang, long ns) {}
    private record Pt(double x, double y, double z, double ang, float age) {}

    private static final List<Drop> drops = new ArrayList<>();
    private static final List<Ring> rings = new ArrayList<>();
    private static final List<Flash> flashes = new ArrayList<>();
    private static final List<Splat> splats = new ArrayList<>();
    private static final List<Chunk> chunks = new ArrayList<>();
    private static final List<Strand> strands = new ArrayList<>();
    private static final List<Txt> texts = new ArrayList<>();
    private static final Map<String, ArrayDeque<Sample>> HIST = new HashMap<>();
    private static final Random RNG = new Random();
    private static long lastNs = 0;

    private Fx() {}

    public static void clear() {
        drops.clear(); rings.clear(); flashes.clear(); splats.clear(); chunks.clear(); strands.clear(); texts.clear(); HIST.clear();
    }

    // ------------------------------------------------------------ eventos
    /**
     * kind 0 = gelatina devorada (splash grande), 1 = inimigo acertado, 2 = mordida (splash pequeno).
     * tier = tipo da gelatina (define a cor dos respingos).
     */
    public static void onCut(double x, double y, double z, UUID owner, int amount, int kind, int tier) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        boolean mine = mc.player != null && mc.player.getUuid().equals(owner);
        float[] goo = kind == 3 ? Palette.train(tier) : Palette.goo(tier);

        if (kind == 0) {
            // a gelatina se parte em pedaços macios + spray fino de gotas
            spawnChunks(mc.world, x, y, z, goo, 11 + RNG.nextInt(6), 1f, true);
            splash(mc.world, x, y, z, goo, 14 + RNG.nextInt(8), 1f);
            addRing(x, y - 0.35, z, 1.9f, 0.45f, 0f, goo);
            addFlash(x, y, z, 1.6f, 0.35f, mix(goo, WHITE, 0.55f));
            for (int i = 0; i < 6; i++) { // brilhinhos
                addFlash(x + (RNG.nextDouble() - 0.5) * 0.9, y + (RNG.nextDouble() - 0.5) * 0.9, z + (RNG.nextDouble() - 0.5) * 0.9,
                        0.22f + RNG.nextFloat() * 0.25f, 0.2f + RNG.nextFloat() * 0.25f, mix(goo, WHITE, 0.7f));
            }
        } else if (kind == 3) { // treino: respingos pequenos + brilho, a gelatina não quebra
            spawnChunks(mc.world, x, y + 0.2, z, goo, 1 + RNG.nextInt(2), 0.5f, false);
            splash(mc.world, x, y + 0.2, z, goo, 2 + RNG.nextInt(3), 0.5f);
            addFlash(x + (RNG.nextDouble() - 0.5) * 0.8, y + 0.2 + RNG.nextDouble() * 0.5, z + (RNG.nextDouble() - 0.5) * 0.8,
                    0.3f, 0.3f, mix(goo, WHITE, 0.7f));
        } else if (kind == 2) {
            spawnChunks(mc.world, x, y, z, goo, 2 + RNG.nextInt(3), 0.55f, false);
            splash(mc.world, x, y, z, goo, 2 + RNG.nextInt(3), 0.55f);
        } else {
            addRing(x, y - 0.2, z, 1.3f, 0.35f, 0f, new float[]{0.6f, 0.92f, 1f});
            addFlash(x, y, z, 1.3f, 0.3f, new float[]{0.75f, 0.95f, 1f});
        }

        if (mine && amount > 0) {
            Txt t = new Txt();
            t.x = x + (RNG.nextDouble() - 0.5) * 0.5;
            t.y = y + 0.6;
            t.z = z + (RNG.nextDouble() - 0.5) * 0.5;
            t.s = "+" + amount + (kind == 3 ? " XP" : "");
            t.life = 1.0f;
            t.rgb = kind == 3 ? 0x55FFFF : 0xFFD43B;
            texts.add(t);
        }
    }

    /**
     * Gotas de gelatina saindo do bloco. As gotas saem pelos lados ABERTOS (sem bloco), senão ficariam
     * presas dentro da parede de gelatina. Importante: elas atravessam blocos de gelatina, porque quando
     * este pacote chega o cliente ainda pode ter o bloco antigo no lugar (a atualização do bloco chega
     * alguns ms depois) - era isso que apagava todos os respingos no mesmo instante.
     */
    private static void splash(ClientWorld w, double x, double y, double z, float[] goo, int n, float power) {
        List<Direction> open = openDirs(w, x, y, z);
        for (int i = 0; i < n; i++) {
            Direction dir = open.isEmpty() ? Direction.UP : open.get(RNG.nextInt(open.size()));
            double sp = (1.4 + RNG.nextDouble() * 3.6) * power;
            double ang = RNG.nextDouble() * TAU;
            double spread = (0.6 + RNG.nextDouble() * 1.8) * power;
            Drop d = new Drop();
            d.x = x + dir.getOffsetX() * 0.42 + (RNG.nextDouble() - 0.5) * 0.6;
            d.y = y + dir.getOffsetY() * 0.42 + (RNG.nextDouble() - 0.5) * 0.6;
            d.z = z + dir.getOffsetZ() * 0.42 + (RNG.nextDouble() - 0.5) * 0.6;
            d.vx = dir.getOffsetX() * sp + Math.cos(ang) * spread;
            d.vz = dir.getOffsetZ() * sp + Math.sin(ang) * spread;
            d.vy = dir.getOffsetY() * sp * 0.8 + (dir == Direction.DOWN ? 0.5 : 1.8 + RNG.nextDouble() * 4.2) * power;
            d.size = (0.07f + RNG.nextFloat() * 0.12f) * (0.7f + 0.3f * power);
            d.life = 1.8f;
            float k = 0.85f + RNG.nextFloat() * 0.15f;
            d.c = new float[]{Math.min(1f, goo[0] * k), Math.min(1f, goo[1] * k + RNG.nextFloat() * 0.1f), Math.min(1f, goo[2] * k)};
            drops.add(d);
        }
    }

    /** Lados do bloco sem nada sólido encostado (por onde os pedaços podem sair). */
    private static List<Direction> openDirs(ClientWorld w, double x, double y, double z) {
        BlockPos bp = BlockPos.ofFloored(x, y, z);
        List<Direction> open = new ArrayList<>(6);
        for (Direction dir : Direction.values()) {
            BlockPos np = bp.offset(dir);
            if (w.getBlockState(np).getCollisionShape(w, np).isEmpty()) open.add(dir);
        }
        return open;
    }

    /** Pedaços de gelatina de tamanhos variados saindo do bloco; os maiores soltam fios esticados. */
    private static void spawnChunks(ClientWorld w, double x, double y, double z, float[] goo, int n, float power, boolean withStrands) {
        List<Direction> open = openDirs(w, x, y, z);
        int strandsLeft = 5;
        for (int i = 0; i < n && chunks.size() < 140; i++) {
            Direction dir = open.isEmpty() ? Direction.UP : open.get(RNG.nextInt(open.size()));
            double sp = (1.8 + RNG.nextDouble() * 3.4) * power;
            double ang = RNG.nextDouble() * TAU;
            double spread = (0.5 + RNG.nextDouble() * 1.6) * power;
            Chunk c = new Chunk();
            c.x = x + dir.getOffsetX() * 0.40 + (RNG.nextDouble() - 0.5) * 0.5;
            c.y = y + dir.getOffsetY() * 0.40 + (RNG.nextDouble() - 0.5) * 0.5;
            c.z = z + dir.getOffsetZ() * 0.40 + (RNG.nextDouble() - 0.5) * 0.5;
            c.vx = dir.getOffsetX() * sp + Math.cos(ang) * spread;
            c.vz = dir.getOffsetZ() * sp + Math.sin(ang) * spread;
            c.vy = dir.getOffsetY() * sp * 0.7 + (dir == Direction.DOWN ? 0.4 : 1.5 + RNG.nextDouble() * 3.5) * power;
            boolean big = RNG.nextFloat() < 0.28f;
            c.size = (big ? 0.16f + RNG.nextFloat() * 0.11f : 0.07f + RNG.nextFloat() * 0.08f) * (0.6f + 0.4f * power);
            c.jigAmp = 0.30f;
            float k = 0.88f + RNG.nextFloat() * 0.12f;
            c.c = new float[]{Math.min(1f, goo[0] * k), Math.min(1f, goo[1] * k), Math.min(1f, goo[2] * k)};
            chunks.add(c);
            if (withStrands && strandsLeft > 0 && c.size > 0.14f) {
                strandsLeft--;
                Strand st = new Strand();
                st.ox = x + dir.getOffsetX() * 0.35; st.oy = y + dir.getOffsetY() * 0.35; st.oz = z + dir.getOffsetZ() * 0.35;
                st.ch = c;
                st.c = c.c;
                strands.add(st);
            }
        }
    }

    /** Poça que se espalha no chão. */
    private static void addPuddle(ClientWorld w, double x, double gy, double z, float size, float[] c) {
        Splat s = new Splat();
        s.x = x; s.y = gy + 0.02 + RNG.nextFloat() * 0.01; s.z = z;
        float asp = 0.78f + RNG.nextFloat() * 0.5f;
        s.rx = size * asp;
        s.rz = size / asp;
        s.rot = (RNG.nextFloat() - 0.5f) * 0.7f; // pouco giro: o brilho da textura vem sempre de cima-esquerda
        s.life = 3.6f + RNG.nextFloat();
        s.c = c;
        s.lit = lightAt(w, x, gy + 0.5, z);
        splats.add(s);
    }

    /** Gotinhas que pulam de volta quando um pedaço bate no chão. */
    private static void miniSplash(double x, double y, double z, float[] c, int n) {
        for (int i = 0; i < n && drops.size() < 700; i++) {
            Drop d = new Drop();
            d.x = x; d.y = y + 0.05; d.z = z;
            double a = RNG.nextDouble() * TAU, sp = 0.6 + RNG.nextDouble() * 1.6;
            d.vx = Math.cos(a) * sp; d.vz = Math.sin(a) * sp; d.vy = 1.6 + RNG.nextDouble() * 2.2;
            d.size = 0.04f + RNG.nextFloat() * 0.04f;
            d.life = 1.2f;
            d.c = c;
            drops.add(d);
        }
    }

    /** Brilho do local (luz de bloco / céu), para as poças e pedaços não brilharem no escuro. */
    private static float lightAt(ClientWorld w, double x, double y, double z) {
        BlockPos bp = BlockPos.ofFloored(x, y, z);
        int b = w.getLightLevel(LightType.BLOCK, bp);
        int sky = Math.max(0, w.getLightLevel(LightType.SKY, bp) - w.getAmbientDarkness());
        return 0.4f + 0.6f * Math.max(b, sky) / 15f;
    }

    private static float[] tint(float[] c, float k) {
        return new float[]{c[0] * k, c[1] * k, c[2] * k};
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    public static void onLevelUp() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Vec3d p = mc.player.getPos();
        for (int i = 0; i < 3; i++) {
            Ring r = new Ring();
            r.x = p.x; r.y = p.y + 0.05; r.z = p.z;
            r.age = -0.12f * i; r.life = 0.9f; r.radius = 3.2f; r.rise = 2.2f;
            r.c = new float[]{0.4f, 0.85f, 1f};
            rings.add(r);
        }
        addFlash(p.x, p.y + 1.0, p.z, 3.0f, 0.6f, new float[]{0.7f, 0.95f, 1f});
    }

    private static void addRing(double x, double y, double z, float radius, float life, float rise, float[] c) {
        Ring r = new Ring();
        r.x = x; r.y = y; r.z = z; r.radius = radius; r.life = life; r.rise = rise; r.c = c;
        rings.add(r);
    }

    private static void addFlash(double x, double y, double z, float size, float life, float[] c) {
        Flash f = new Flash();
        f.x = x; f.y = y; f.z = z; f.size = size; f.life = life; f.c = c;
        flashes.add(f);
    }

    // ------------------------------------------------------------ simulação
    private static void simulate(ClientWorld w, float dt) {
        for (Iterator<Drop> it = drops.iterator(); it.hasNext(); ) {
            Drop d = it.next();
            d.age += dt;
            d.vy -= 14.0 * dt;
            d.x += d.vx * dt; d.y += d.vy * dt; d.z += d.vz * dt;
            if (d.age > d.life) { it.remove(); continue; }

            BlockPos bp = BlockPos.ofFloored(d.x, d.y, d.z);
            BlockState bs = w.getBlockState(bp);
            boolean inJelly = bs.getBlock() instanceof JellyBlock;
            if (!inJelly && !bs.getCollisionShape(w, bp).isEmpty()) { it.remove(); continue; }
            if (d.vy < 0 && !inJelly) {
                double gy = groundY(w, d.x, d.y + 0.05, d.z, 1);
                if (!Double.isNaN(gy) && d.y <= gy) {
                    addPuddle(w, d.x, gy, d.z, d.size * 2.6f + 0.07f, d.c);
                    it.remove();
                }
            }
        }
        simulateChunks(w, dt);
        strands.removeIf(st -> {
            st.age += dt;
            double dx = st.ch.x - st.ox, dy = st.ch.y - st.oy, dz = st.ch.z - st.oz;
            return st.age > 0.6f || st.ch.dead || dx * dx + dy * dy + dz * dz > 1.8 * 1.8;
        });
        splats.removeIf(s -> (s.age += dt) > s.life);
        rings.removeIf(r -> (r.age += dt) > r.life);
        flashes.removeIf(f -> (f.age += dt) > f.life);
        texts.removeIf(t -> (t.age += dt) > t.life);
        while (splats.size() > 300) splats.remove(0);
    }

    private static boolean solidAt(ClientWorld w, double x, double y, double z) {
        BlockPos bp = BlockPos.ofFloored(x, y, z);
        BlockState bs = w.getBlockState(bp);
        return !(bs.getBlock() instanceof JellyBlock) && !bs.getCollisionShape(w, bp).isEmpty();
    }

    private static void simulateChunks(ClientWorld w, float dt) {
        for (Iterator<Chunk> it = chunks.iterator(); it.hasNext(); ) {
            Chunk c = it.next();
            c.age += dt;
            c.jigT += dt;
            if (c.age > 5f || c.size < 0.03f) { c.dead = true; it.remove(); continue; }

            boolean inJelly = w.getBlockState(BlockPos.ofFloored(c.x, c.y, c.z)).getBlock() instanceof JellyBlock;
            if (c.stuck) { // grudado na parede: escorre devagar e vai derretendo
                c.vx = 0; c.vz = 0;
                c.vy = Math.max(c.vy - 5.0 * dt, -0.9);
                c.size -= 0.045f * dt;
            } else {
                c.vy -= 15.0 * dt;
                double drag = 1.0 - 0.5 * dt;
                c.vx *= drag; c.vz *= drag;
            }

            // eixos separados: bater de lado = gruda na parede
            double nx = c.x + c.vx * dt, nz = c.z + c.vz * dt;
            if (!inJelly && (solidAt(w, nx, c.y, c.z) || solidAt(w, c.x, c.y, nz))) {
                if (!c.stuck) {
                    c.stuck = true;
                    c.jigAmp = 0.45f; c.jigT = 0f;
                    c.vy = Math.max(c.vy * 0.2, -0.3);
                }
                c.vx = 0; c.vz = 0;
            } else {
                c.x = nx; c.z = nz;
            }

            double ny = c.y + c.vy * dt;
            if (c.vy < 0 && !inJelly) {
                double gy = groundY(w, c.x, ny + 0.05, c.z, 1);
                if (!Double.isNaN(gy) && ny - c.size * 0.55 <= gy) {
                    float impact = (float) -c.vy;
                    addPuddle(w, c.x, gy, c.z, c.size * (1.5f + RNG.nextFloat() * 0.6f), c.c);
                    if (c.bounces == 0 && !c.stuck && impact > 3.0f && c.size > 0.1f) {
                        // primeiro impacto: espirra, quica e balança
                        c.y = gy + c.size * 0.55;
                        c.vy = impact * 0.33; c.vx *= 0.55; c.vz *= 0.55;
                        c.bounces++;
                        c.jigAmp = 0.5f; c.jigT = 0f;
                        c.size *= 0.82f;
                        miniSplash(c.x, gy, c.z, c.c, 2 + RNG.nextInt(3));
                    } else {
                        // pousou: espalha numa poça maior
                        addPuddle(w, c.x, gy, c.z, c.size * 2.1f, c.c);
                        miniSplash(c.x, gy, c.z, c.c, 1 + RNG.nextInt(2));
                        c.dead = true;
                        it.remove();
                    }
                    continue;
                }
            }
            c.y = ny;
        }
        while (chunks.size() > 140) chunks.remove(0);
    }

    /** Y do topo do bloco sólido abaixo, ou NaN se não houver em maxDown blocos. */
    private static double groundY(ClientWorld w, double x, double y, double z, int maxDown) {
        BlockPos.Mutable bp = new BlockPos.Mutable();
        for (int dy = 0; dy <= maxDown; dy++) {
            bp.set(x, y - dy, z);
            VoxelShape sh = w.getBlockState(bp).getCollisionShape(w, bp);
            if (!sh.isEmpty()) return bp.getY() + sh.getMax(Direction.Axis.Y);
        }
        return Double.NaN;
    }

    // ------------------------------------------------------------ render
    private record Active(PlayerEntity p, ReapData d, int n, Vec3d c, float[] trail, double ang0) {}

    public static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null) return;

        long nowNs = System.nanoTime();
        float dt = lastNs == 0 ? 0f : Math.min(0.05f, (nowNs - lastNs) / 1e9f);
        lastNs = nowNs;
        if (mc.isPaused()) dt = 0f;

        Camera cam = ctx.camera();
        Vec3d cp = cam.getPos();
        float td = ctx.tickDelta();
        MatrixStack ms = ctx.matrixStack();
        Matrix4f m = ms.peek().getPositionMatrix();
        Quaternionf rot = cam.getRotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(rot);
        Vector3f up = new Vector3f(0, 1, 0).rotate(rot);
        long time = world.getTime();

        simulate(world, dt);

        // jogadores com colheres + histórico dos rastros
        List<Active> active = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PlayerEntity p : world.getPlayers()) {
            ReapData d = ClientData.MAP.get(p.getUuid());
            if (d == null || !d.enabled || p.isSpectator()) continue;
            int n = d.spoons();
            Vec3d c = p.getLerpedPos(td).add(0, SpoonLogic.CENTER_Y, 0);
            active.add(new Active(p, d, n, c, Palette.trail(d.rebirths), (time + td) * SpoonLogic.ORBIT_SPEED));
            for (int i = 0; i < n; i++) {
                String key = p.getUuid() + "#" + i;
                seen.add(key);
                ArrayDeque<Sample> dq = HIST.computeIfAbsent(key, k -> new ArrayDeque<>());
                double a = SpoonLogic.angle(time, td, i, n);
                double y = c.y + SpoonLogic.bob(time, td, i);
                Sample last = dq.peekLast();
                if (last != null) {
                    if (a < last.ang() - 1.0 || Math.abs(last.x() - c.x) + Math.abs(last.z() - c.z) > 8) dq.clear();
                    else if (a <= last.ang()) continue;
                }
                dq.addLast(new Sample(c.x, y, c.z, a, nowNs));
                while (dq.size() > 1 && nowNs - dq.peekFirst().ns() > TRAIL_NS) dq.removeFirst();
            }
        }
        HIST.keySet().removeIf(k -> !seen.contains(k));

        // ---------- estado GL
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // ---------- 1) normal: poças e gotas
        RenderSystem.defaultBlendFunc();
        if (!splats.isEmpty()) {
            QB q = new QB(T_PUDDLE, m);
            for (Splat sp : splats) {
                float[] an = splatAnim(sp);
                flatOval(q, sp.x - cp.x, sp.y - cp.y, sp.z - cp.z, sp.rx * an[0], sp.rz * an[0], sp.rot,
                        tint(sp.c, sp.lit), 0.95f * an[1]);
            }
            q.flush();
        }
        if (!strands.isEmpty()) {
            QB q = new QB(T_GLOW, m);
            for (Strand st : strands) drawStrand(q, st, cp, world);
            q.flush();
        }
        if (!chunks.isEmpty()) {
            QB q = new QB(T_BLOB, m);
            for (Chunk c : chunks) drawChunk(q, c, cp, right, tint(c.c, lightAt(world, c.x, c.y, c.z)), 0.96f);
            q.flush();
        }
        if (!drops.isEmpty()) {
            QB q = new QB(T_DROP, m);
            for (Drop d : drops) {
                double px = d.x - cp.x, py = d.y - cp.y, pz = d.z - cp.z;
                double sp = Math.sqrt(d.vx * d.vx + d.vy * d.vy + d.vz * d.vz);
                Vector3f vd = sp > 0.01 ? new Vector3f((float) (d.vx / sp), (float) (d.vy / sp), (float) (d.vz / sp))
                        : new Vector3f(0, 1, 0);
                Vector3f view = new Vector3f((float) px, (float) py, (float) pz).normalize();
                Vector3f side = vd.cross(view, new Vector3f());
                if (side.lengthSquared() < 1e-6f) side.set(right);
                side.normalize();
                float len = d.size * (1f + Math.min((float) sp * 0.14f, 2.2f));
                float wid = d.size * 0.85f;
                float a = Math.min(1f, (d.life - d.age) / 0.3f);
                quadAxes(q, px, py, pz, vd, len, side, wid, d.c, a);
            }
            q.flush();
        }

        // ---------- 2) aditivo: brilho
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);

        // brilho de gelatina molhada nas poças e nos pedaços
        if (!splats.isEmpty()) {
            QB q = new QB(T_PUDDLE_SPEC, m);
            for (Splat sp : splats) {
                float[] an = splatAnim(sp);
                flatOval(q, sp.x - cp.x, sp.y + 0.002 - cp.y, sp.z - cp.z, sp.rx * an[0], sp.rz * an[0], sp.rot,
                        WHITE, 0.75f * an[1] * sp.lit);
            }
            q.flush();
        }
        if (!chunks.isEmpty()) {
            QB q = new QB(T_BLOB_SPEC, m);
            for (Chunk c : chunks) drawChunk(q, c, cp, right, WHITE, 0.85f * lightAt(world, c.x, c.y, c.z));
            q.flush();
        }

        if (!active.isEmpty()) {
            // disco de movimento girando (constante)
            QB q = new QB(T_SWIRL, m);
            for (Active a : active) {
                double R = SpoonLogic.HANDLE_R + SpoonLogic.LENGTH + 0.2;
                flat(q, a.c().x - cp.x, a.c().y - cp.y, a.c().z - cp.z, (float) R, (float) (a.ang0() * 0.55), a.trail(), 0.34f);
                flat(q, a.c().x - cp.x, a.c().y - cp.y + 0.03, a.c().z - cp.z, (float) (R * 0.62), (float) (-a.ang0() * 0.9), WHITE, 0.14f);
            }
            q.flush();

            // anel no chão pulsando
            q = new QB(T_RING, m);
            for (Active a : active) {
                double gy = groundY(world, a.c().x, a.c().y, a.c().z, 6);
                if (Double.isNaN(gy)) continue;
                double sc = 1.0 + 0.07 * Math.sin((time + td) * 0.2);
                flat(q, a.c().x - cp.x, gy + 0.03 - cp.y, a.c().z - cp.z, (float) (SpoonLogic.HANDLE_R + SpoonLogic.LENGTH * 0.92 * sc),
                        (float) (a.ang0() * 0.3), a.trail(), 0.55f);
            }
            for (Ring r : rings) {
                if (r.age < 0) continue;
                float t = r.age / r.life;
                float e = 1f - (1f - t) * (1f - t) * (1f - t);
                flat(q, r.x - cp.x, r.y + r.rise * t - cp.y, r.z - cp.z, r.radius * e, 0f, r.c, (float) Math.pow(1 - t, 1.3) * 0.95f);
            }
            q.flush();

            // rastro em fita
            q = new QB(T_TRAIL, m);
            for (Active a : active) {
                for (int i = 0; i < a.n(); i++) {
                    ArrayDeque<Sample> dq = HIST.get(a.p().getUuid() + "#" + i);
                    if (dq == null || dq.size() < 2) continue;
                    List<Pt> pts = smooth(dq, nowNs);
                    strip(q, cp, pts, R_OUT, 0, R_OUT - 0.95, 0, a.trail(), 0.85f, 0f, 1f);       // faixa horizontal
                    strip(q, cp, pts, R_OUT - 0.1, 0, R_OUT - 0.1, 0.40, a.trail(), 0.75f, 0f, 1f); // banda superior
                    strip(q, cp, pts, R_OUT - 0.1, 0, R_OUT - 0.1, -0.28, a.trail(), 0.65f, 0f, 1f); // banda inferior
                    strip(q, cp, pts, R_OUT, 0, R_OUT - 0.28, 0, WHITE, 1f, 0.1f, 1.8f);          // núcleo quente
                }
            }
            q.flush();
        } else if (!rings.isEmpty()) {
            QB q = new QB(T_RING, m);
            for (Ring r : rings) {
                if (r.age < 0) continue;
                float t = r.age / r.life;
                float e = 1f - (1f - t) * (1f - t) * (1f - t);
                flat(q, r.x - cp.x, r.y + r.rise * t - cp.y, r.z - cp.z, r.radius * e, 0f, r.c, (float) Math.pow(1 - t, 1.3) * 0.95f);
            }
            q.flush();
        }

        // brilhos: pontas das colheres + flashes
        QB g = new QB(T_GLOW, m);
        for (Active a : active) {
            for (int i = 0; i < a.n(); i++) {
                double ang = SpoonLogic.angle(time, td, i, a.n());
                double y = a.c().y + SpoonLogic.bob(time, td, i);
                double tx = a.c().x + Math.cos(ang) * R_OUT - cp.x;
                double ty = y - cp.y;
                double tz = a.c().z + Math.sin(ang) * R_OUT - cp.z;
                billboard(g, right, up, tx, ty, tz, 0.55f, a.trail(), 0.55f);
                billboard(g, right, up, tx, ty, tz, 0.2f, WHITE, 0.9f);
            }
        }
        for (Flash f : flashes) {
            float t = f.age / f.life;
            billboard(g, right, up, f.x - cp.x, f.y - cp.y, f.z - cp.z, f.size * (0.35f + t), f.c, (1 - t) * (1 - t));
        }
        g.flush();

        // ---------- restaura GL
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();

        // ---------- 3) textos flutuantes "+N"
        if (!texts.isEmpty()) {
            TextRenderer tr = mc.textRenderer;
            VertexConsumerProvider.Immediate imm = mc.getBufferBuilders().getEffectVertexConsumers();
            for (Txt t : texts) {
                float k = t.age / t.life;
                float pop = 1f + 0.5f * Math.max(0f, 1f - k * 6f);
                double yy = t.y + k * 1.1;
                int alpha = Math.max(5, (int) (255 * Math.min(1f, (1f - k) * 2.2f)));
                ms.push();
                ms.translate(t.x - cp.x, yy - cp.y, t.z - cp.z);
                ms.multiply(rot);
                float s = 0.035f * pop;
                ms.scale(-s, -s, s);
                float w = tr.getWidth(t.s);
                tr.draw(t.s, -w / 2f, 0, (alpha << 24) | t.rgb, true, ms.peek().getPositionMatrix(), imm,
                        TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
                ms.pop();
            }
            imm.draw();
        }
    }

    // ------------------------------------------------------------ helpers de geometria
    /** Escala e opacidade da poça: espalha com um "estalo" ao cair, cresce devagar e some encolhendo. */
    private static float[] splatAnim(Splat s) {
        float pop = Math.min(1f, s.age / 0.14f);
        float over = 1f + 0.12f * (float) Math.sin(pop * Math.PI);
        float grow = 1f + 0.08f * Math.min(1f, s.age / (s.life * 0.7f));
        float fade = Math.max(0f, Math.min(1f, (s.life - s.age) / 1.2f));
        float scale = (0.35f + 0.65f * pop) * over * grow * (0.82f + 0.18f * fade);
        return new float[]{scale, fade};
    }

    /** Quad horizontal oval (raios rx/rz), girado em torno do centro. */
    private static void flatOval(QB q, double cx, double cy, double cz, float rx, float rz, float rot, float[] c, float a) {
        double[] sx = {-1, 1, 1, -1}, sz = {-1, -1, 1, 1};
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        double cr = Math.cos(rot), sr = Math.sin(rot);
        for (int k = 0; k < 4; k++) {
            double px = sx[k] * rx, pz = sz[k] * rz;
            q.v(cx + px * cr - pz * sr, cy, cz + px * sr + pz * cr, uv[k][0], uv[k][1], c, a);
        }
    }

    /**
     * Pedaço de gelatina: estica na direção do movimento (volume constante), balança depois de um impacto
     * e fica alongado na vertical quando está escorrendo na parede.
     */
    private static void drawChunk(QB q, Chunk c, Vec3d cp, Vector3f right, float[] col, float a) {
        double px = c.x - cp.x, py = c.y - cp.y, pz = c.z - cp.z;
        double sp = Math.sqrt(c.vx * c.vx + c.vy * c.vy + c.vz * c.vz);
        Vector3f vd = (!c.stuck && sp > 0.6)
                ? new Vector3f((float) (c.vx / sp), (float) (c.vy / sp), (float) (c.vz / sp))
                : new Vector3f(0, 1, 0);
        Vector3f view = new Vector3f((float) px, (float) py, (float) pz).normalize();
        Vector3f side = vd.cross(view, new Vector3f());
        if (side.lengthSquared() < 1e-6f) side.set(right);
        side.normalize();
        float stretch = c.stuck ? 0.45f : Math.min((float) sp * 0.055f, 0.9f);
        float jig = c.jigAmp * (float) (Math.sin(30.0 * c.jigT) * Math.exp(-6.0 * c.jigT));
        float k = Math.max(0.35f, 1f + stretch + jig);
        float len = c.size * k;
        float wid = c.size / (float) Math.sqrt(k);
        float fade = Math.min(1f, (5f - c.age) / 0.5f);
        quadAxes(q, px, py, pz, vd, len, side, wid, col, a * fade);
    }

    /** Fio de gelatina entre o ponto de origem e o pedaço: afina com a distância e arrebenta. */
    private static void drawStrand(QB q, Strand st, Vec3d cp, ClientWorld w) {
        Chunk ch = st.ch;
        double dx = ch.x - st.ox, dy = ch.y - st.oy, dz = ch.z - st.oz;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float t = (float) (dist / 1.8);
        float w0 = 0.055f * (float) Math.pow(Math.max(0f, 1f - t), 1.2) * Math.max(0f, 1f - st.age / 0.6f);
        if (w0 < 0.004f) return;
        float[] col = tint(st.c, lightAt(w, st.ox, st.oy, st.oz));
        float sag = 0.12f * (1f - t);
        int segs = 7;
        double[][] pts = new double[segs + 1][3];
        for (int i = 0; i <= segs; i++) {
            double u = i / (double) segs;
            pts[i][0] = st.ox + dx * u - cp.x;
            pts[i][1] = st.oy + dy * u - sag * Math.sin(Math.PI * u) - cp.y;
            pts[i][2] = st.oz + dz * u - cp.z;
        }
        for (int i = 0; i < segs; i++) {
            double[] p0 = pts[i], p1 = pts[i + 1];
            Vector3f dir = new Vector3f((float) (p1[0] - p0[0]), (float) (p1[1] - p0[1]), (float) (p1[2] - p0[2]));
            Vector3f view = new Vector3f((float) p0[0], (float) p0[1], (float) p0[2]).normalize();
            Vector3f sd = dir.cross(view, new Vector3f());
            if (sd.lengthSquared() < 1e-9f) continue;
            sd.normalize();
            float wa = w0 * (1f - 0.45f * i / segs), wb = w0 * (1f - 0.45f * (i + 1) / segs);
            q.v(p0[0] - sd.x * wa, p0[1] - sd.y * wa, p0[2] - sd.z * wa, 0f, 0.5f, col, 0.9f);
            q.v(p0[0] + sd.x * wa, p0[1] + sd.y * wa, p0[2] + sd.z * wa, 1f, 0.5f, col, 0.9f);
            q.v(p1[0] + sd.x * wb, p1[1] + sd.y * wb, p1[2] + sd.z * wb, 1f, 0.5f, col, 0.9f);
            q.v(p1[0] - sd.x * wb, p1[1] - sd.y * wb, p1[2] - sd.z * wb, 0f, 0.5f, col, 0.9f);
        }
    }

    /** Quad horizontal girado em torno do centro. */
    private static void flat(QB q, double cx, double cy, double cz, float half, float rot, float[] c, float a) {
        float r = half * 1.41421356f;
        double[] ux = {0, 1, 1, 0}, vy = {0, 0, 1, 1};
        for (int k = 0; k < 4; k++) {
            double ang = rot + Math.PI / 4 + k * Math.PI / 2;
            q.v(cx + Math.cos(ang) * r, cy, cz + Math.sin(ang) * r, (float) ux[k], (float) vy[k], c, a);
        }
    }

    /** Quad voltado para a câmera. */
    private static void billboard(QB q, Vector3f right, Vector3f up, double cx, double cy, double cz,
                                  float size, float[] c, float a) {
        float[][] cr = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
        float[][] uv = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};
        for (int k = 0; k < 4; k++) {
            q.v(cx + (right.x * cr[k][0] + up.x * cr[k][1]) * size,
                cy + (right.y * cr[k][0] + up.y * cr[k][1]) * size,
                cz + (right.z * cr[k][0] + up.z * cr[k][1]) * size, uv[k][0], uv[k][1], c, a);
        }
    }

    /** Quad com eixos arbitrários (usado nas gotas esticadas pela velocidade). */
    private static void quadAxes(QB q, double cx, double cy, double cz, Vector3f ax, float lx,
                                 Vector3f ay, float ly, float[] c, float a) {
        float[][] cr = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int k = 0; k < 4; k++) {
            q.v(cx + ax.x * lx * cr[k][0] + ay.x * ly * cr[k][1],
                cy + ax.y * lx * cr[k][0] + ay.y * ly * cr[k][1],
                cz + ax.z * lx * cr[k][0] + ay.z * ly * cr[k][1], uv[k][0], uv[k][1], c, a);
        }
    }

    /** Interpola o histórico (centro linear, ângulo em passos finos) para a fita ficar curva e lisa. */
    private static List<Pt> smooth(ArrayDeque<Sample> dq, long nowNs) {
        Sample[] s = dq.toArray(new Sample[0]);
        List<Pt> out = new ArrayList<>();
        for (int j = 0; j < s.length - 1; j++) {
            Sample a = s[j], b = s[j + 1];
            int steps = Math.max(1, (int) Math.ceil(Math.abs(b.ang() - a.ang()) / 0.05));
            for (int k = 0; k < steps; k++) {
                double t = k / (double) steps;
                double ns = a.ns() + (b.ns() - a.ns()) * t;
                out.add(new Pt(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t,
                        a.ang() + (b.ang() - a.ang()) * t, (float) ((nowNs - ns) / (double) TRAIL_NS)));
            }
        }
        Sample l = s[s.length - 1];
        out.add(new Pt(l.x(), l.y(), l.z(), l.ang(), (float) ((nowNs - l.ns()) / (double) TRAIL_NS)));
        return out;
    }

    /**
     * Faixa entre uma "borda" (raio rA, deslocamento vertical yA) e uma "ponta" (rB, yB).
     * A opacidade some com a idade; u = idade, v = 0 na borda e 1 na ponta.
     */
    private static void strip(QB q, Vec3d cp, List<Pt> pts, double rA, double yA, double rB, double yB,
                              float[] col, float aEdge, float aFar, float ageMul) {
        for (int i = 0; i < pts.size() - 1; i++) {
            Pt p0 = pts.get(i), p1 = pts.get(i + 1);
            float f0 = fade(p0.age() * ageMul), f1 = fade(p1.age() * ageMul);
            if (f0 <= 0.002f && f1 <= 0.002f) continue;
            float u0 = Math.min(1f, p0.age()), u1 = Math.min(1f, p1.age());
            put(q, cp, p0, rA, yA, u0, 0f, col, aEdge * f0);
            put(q, cp, p0, rB, yB, u0, 1f, col, aFar * f0);
            put(q, cp, p1, rB, yB, u1, 1f, col, aFar * f1);
            put(q, cp, p1, rA, yA, u1, 0f, col, aEdge * f1);
        }
    }

    private static void put(QB q, Vec3d cp, Pt p, double r, double dy, float u, float v, float[] col, float a) {
        q.v(p.x() + Math.cos(p.ang()) * r - cp.x, p.y() + dy - cp.y, p.z() + Math.sin(p.ang()) * r - cp.z, u, v, col, a);
    }

    private static float fade(float x) {
        if (x >= 1f) return 0f;
        if (x < 0f) x = 0f;
        float k = 1f - x;
        return k * k * (0.4f + 0.6f * k);
    }
}
