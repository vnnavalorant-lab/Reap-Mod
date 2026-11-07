package com.reap;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Gelatina de treino GRANDE (como no jogo original): INQUEBRÁVEL, fica parada no lobby e nunca é devorada.
 * Este bloco é o "controlador" (centro da base). Ao colocar, ele preenche 3x3x3 com {@link TrainJellyPartBlock}
 * (colisão) e o cliente desenha a placa 5x5 + a gelatina grande (ver client/TrainJellyRenderer).
 * Enquanto as colheres encostam, o jogador ganha XP multiplicado (se tiver os rebirths exigidos).
 */
public class TrainJellyBlock extends Block implements BlockEntityProvider {
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 16, 16);

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

    /** Só dá para colocar se houver espaço livre para a estrutura inteira (3x3x3). */
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        World w = ctx.getWorld();
        BlockPos o = ctx.getBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos p = o.add(dx, dy, dz);
                    if (w.isOutOfHeightLimit(p) || !w.getBlockState(p).isReplaceable()) return null;
                }
            }
        }
        return getDefaultState();
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (world.isClient) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    world.setBlockState(pos.add(dx, dy, dz),
                            ReapMod.TRAIN_PART.getDefaultState().with(TrainJellyPartBlock.LAYER, dy), Block.NOTIFY_ALL);
                }
            }
        }
    }

    /** Se o controlador some, as partes somem junto. */
    @Override
    @Deprecated
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClient && !state.isOf(newState.getBlock())) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos p = pos.add(dx, dy, dz);
                        if (world.getBlockState(p).getBlock() instanceof TrainJellyPartBlock) {
                            world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                        }
                    }
                }
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TrainJellyBlockEntity(pos, state);
    }
}
