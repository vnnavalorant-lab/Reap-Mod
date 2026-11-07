package com.reap;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** Não guarda dados: existe só para o cliente desenhar o letreiro ("Rebirth 3 / x2") sobre a gelatina. */
public class TrainJellyBlockEntity extends BlockEntity {
    public TrainJellyBlockEntity(BlockPos pos, BlockState state) {
        super(ReapMod.TRAIN_BE, pos, state);
    }
}
