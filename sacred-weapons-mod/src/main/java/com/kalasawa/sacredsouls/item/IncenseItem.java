package com.kalasawa.sacredsouls.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import com.kalasawa.sacredsouls.soul.SoulManager;

/** ธูปเรียกวิญญาณ: calls the soul back to a soulless body (energy 3). Phase 2 moves this to the shrine. */
public class IncenseItem extends Item {
	public IncenseItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer && SoulManager.useIncense(serverPlayer)) {
			player.getItemInHand(hand).shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
