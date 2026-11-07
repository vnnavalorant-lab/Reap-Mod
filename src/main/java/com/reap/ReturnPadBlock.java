package com.reap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/**
 * Plataforma de retorno: quem pisa ganha {@link #wins} wins e volta para o spawn.
 * Plataformas encostadas se conectam (sem borda entre elas) e funcionam como UMA só (ver {@link ReturnPads}).
 */
public class ReturnPadBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty NORTH = Properties.NORTH;
    public static final BooleanProperty EAST = Properties.EAST;
    public static final BooleanProperty SOUTH = Properties.SOUTH;
    public static final BooleanProperty WEST = Properties.WEST;

    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 3, 16);

    public final int wins;

    public ReturnPadBlock(net.minecraft.block.AbstractBlock.Settings settings, int wins) {
        super(settings);
        this.wins = wins;
        setDefaultState(getStateManager().getDefaultState()
                .with(NORTH, false).with(EAST, false).with(SOUTH, false).with(WEST, false));
    }

    private static BooleanProperty prop(Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> null;
        };
    }

    private static boolean connects(BlockState s) {
        return s.getBlock() instanceof ReturnPadBlock;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        WorldView w = ctx.getWorld();
        BlockPos p = ctx.getBlockPos();
        BlockState s = getDefaultState();
        for (Direction d : Direction.Type.HORIZONTAL) {
            s = s.with(prop(d), connects(w.getBlockState(p.offset(d))));
        }
        return s;
    }

    @Override
    @Deprecated
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        BooleanProperty pr = prop(direction);
        if (pr == null) return state;
        return state.with(pr, connects(neighborState));
    }

    @Override
    @Deprecated
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ReturnPadBlockEntity(pos, state);
    }
}
