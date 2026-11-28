package com.kalasawa.sacredsouls.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

import com.kalasawa.sacredsouls.soul.SoulManager;
import com.kalasawa.sacredsouls.spirit.Spirit;

/** An elemental spirit. Hold right-click to bind it to the spirit slot. */
public class SpiritItem extends Item {
	public final Spirit spirit;

	public SpiritItem(Spirit spirit, Properties properties) {
		super(properties);
		this.spirit = spirit;
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
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
		if (!level.isClientSide() && remaining % 4 == 0) {
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS,
					0.5f + 0.5f * level.getRandom().nextFloat(), 0.8f + 0.4f * level.getRandom().nextFloat());
		}
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer player && SoulManager.bindSpirit(player, spirit)) {
			stack.shrink(1);
		}
		return stack;
	}
}
