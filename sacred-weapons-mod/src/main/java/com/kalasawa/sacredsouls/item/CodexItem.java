package com.kalasawa.sacredsouls.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import com.kalasawa.sacredsouls.soul.SoulManager;

/** คัมภีร์วิญญาณ: lists who devoured what, who holds which title, and where soul items come from. */
public class CodexItem extends Item {
	public CodexItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			SoulManager.showCodex(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}
}
