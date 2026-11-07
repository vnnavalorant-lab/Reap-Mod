package com.reap;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.GlassBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Gelatina. A propriedade "bites" (0..7) diz o quanto já foi devorada:
 * 0 = bloco inteiro, 7 = só um restinho no meio. Cada valor tem um modelo diferente (ver tools/gen_assets.py).
 */
public class JellyBlock extends GlassBlock {
    public static final IntProperty BITES = IntProperty.of("bites", 0, 7);

    /** Índice em {@link ReapMod#TYPES}. */
    public final int tier;

    public JellyBlock(AbstractBlock.Settings settings, int tier) {
        super(settings);
        this.tier = tier;
        setDefaultState(getStateManager().getDefaultState().with(BITES, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(BITES);
    }

    /** Duas gelatinas inteiras iguais encostadas não desenham a face de contato; mordida sempre aparece. */
    @Override
    @Deprecated
    public boolean isSideInvisible(BlockState state, BlockState stateFrom, Direction direction) {
        return stateFrom.isOf(this) && stateFrom.get(BITES) == 0;
    }

    /** Gelatina mordida que ninguém está comendo (ex.: servidor reiniciou) volta a ficar inteira. */
    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, net.minecraft.util.math.random.Random random) {
        if (state.get(BITES) > 0 && !ReapMod.isChewing(world, pos)) {
            world.setBlockState(pos, state.with(BITES, 0), Block.NOTIFY_ALL);
        }
    }
}
