package com.kalasawa.sacredsouls.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

import com.kalasawa.sacredsouls.power.SoulPower;
import com.kalasawa.sacredsouls.soul.SoulManager;

/** A soul item. Hold right-click to let the soul devour it. */
public class SoulItem extends Item {
	public final SoulPower power;

	public SoulItem(SoulPower power, Properties properties) {
		super(properties);
		this.power = power;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 32;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.EAT;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer player && SoulManager.devour(player, power)) {
			stack.shrink(1);
		}
		return stack;
	}
}
