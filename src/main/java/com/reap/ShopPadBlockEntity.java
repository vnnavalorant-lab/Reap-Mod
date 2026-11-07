package com.reap;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** Não guarda dados: existe só para o cliente desenhar o letreiro e a colher giratória sobre a plataforma. */
public class ShopPadBlockEntity extends BlockEntity {
    public ShopPadBlockEntity(BlockPos pos, BlockState state) {
        super(ReapMod.SHOP_BE, pos, state);
    }
}
