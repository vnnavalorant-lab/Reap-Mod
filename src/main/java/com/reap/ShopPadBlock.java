package com.reap;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Bloco de loja de Reap: ao pisar, gasta os wins da Reap {@link #tier} e equipa essa Reap
 * (ver {@link ShopPads}). O letreiro e a colher giratória são desenhados no cliente.
 */
public class ShopPadBlock extends Block implements BlockEntityProvider {
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 3, 16);

    /** Índice em {@link Reaps#TIERS} (1..MAX). */
    public final int tier;

    public ShopPadBlock(AbstractBlock.Settings settings, int tier) {
        super(settings);
        this.tier = tier;
    }

    @Override
    @Deprecated
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ShopPadBlockEntity(pos, state);
    }
}
