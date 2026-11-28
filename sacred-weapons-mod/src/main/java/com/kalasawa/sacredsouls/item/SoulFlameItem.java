package com.kalasawa.sacredsouls.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import com.kalasawa.sacredsouls.soul.SoulManager;

/** ดวงไฟวิญญาณ: +1 soul energy (max 10). Tradeable. */
public class SoulFlameItem extends Item {
	public SoulFlameItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer && SoulManager.useSoulFlame(serverPlayer)) {
			player.getItemInHand(hand).shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
