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
 * Gelatina de treino: fica parada no lobby, é INQUEBRÁVEL e nunca é devorada. Enquanto as colheres encostam
 * nela o jogador ganha XP (multiplicado pelo tipo), desde que tenha os rebirths exigidos.
 */
public class TrainJellyBlock extends Block implements BlockEntityProvider {
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 13, 16);

    /** Índice em {@link ReapMod#TRAIN}. */
    public final int index;

    public TrainJellyBlock(AbstractBlock.Settings settings, int index) {
        super(settings);
        this.index = index;
    }

    @Override
    @Deprecated
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TrainJellyBlockEntity(pos, state);
    }
}
