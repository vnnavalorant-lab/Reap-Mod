package com.reap;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Parte invisível de uma gelatina de treino grande (só dá a colisão 3x3x3). layer = altura (0..2) dentro da estrutura.
 * Tirar uma parte (no criativo) remove a estrutura inteira.
 */
public class TrainJellyPartBlock extends Block {
    public static final IntProperty LAYER = IntProperty.of("layer", 0, 2);
    private static final VoxelShape FULL = Block.createCuboidShape(0, 0, 0, 16, 16, 16);
    /** A gelatina tem ~2,7 blocos de altura: a camada de cima é mais baixa. */
    private static final VoxelShape TOP = Block.createCuboidShape(0, 0, 0, 16, 11, 16);

    public TrainJellyPartBlock(AbstractBlock.Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(LAYER, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(LAYER);
    }

    @Override
    @Deprecated
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return state.get(LAYER) == 2 ? TOP : FULL;
    }

    /** Posição do bloco controlador (centro da base) desta parte, ou null. */
    public static BlockPos findController(BlockView w, BlockPos p, int layer) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos c = p.add(dx, -layer, dz);
                if (w.getBlockState(c).getBlock() instanceof TrainJellyBlock) return c;
            }
        }
        return null;
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            BlockPos c = findController(world, pos, state.get(LAYER));
            if (c != null) world.setBlockState(c, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
        return super.onBreak(world, pos, state, player);
    }
}
