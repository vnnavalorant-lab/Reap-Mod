package com.reap;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** Não guarda dados: existe só para o cliente desenhar o letreiro "Return +N WINS" sobre a plataforma. */
public class ReturnPadBlockEntity extends BlockEntity {
    // cache do cliente (recalculado a cada 1 s)
    public long cachedAt = Long.MIN_VALUE;
    public ReturnPads.Group group;

    public ReturnPadBlockEntity(BlockPos pos, BlockState state) {
        super(ReapMod.PAD_BE, pos, state);
    }
}
