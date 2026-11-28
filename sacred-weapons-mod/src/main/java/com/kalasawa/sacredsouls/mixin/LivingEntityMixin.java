package com.kalasawa.sacredsouls.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import com.kalasawa.sacredsouls.SacredSouls;
import com.kalasawa.sacredsouls.power.SoulPower;
import com.kalasawa.sacredsouls.soul.ModAttachments;
import com.kalasawa.sacredsouls.soul.SoulData;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/** หัวใจศิลา and ถ่านอัคคี change the damage amount before it is applied. */
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float sacredsouls$modifyDamage(float amount, ServerLevel level, DamageSource source) {
		return SacredSouls.POWERS.modifyIncomingDamage((LivingEntity) (Object) this, source, amount);
	}

	/** เขี้ยวแมงมุม: climb walls. Runs on both sides because player movement is client-side. */
	@Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
	private void sacredsouls$climbWalls(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			return;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof Player player && player.horizontalCollision && !player.getAbilities().flying) {
			SoulData data = player.getAttached(ModAttachments.SOUL);
			if (data != null && !data.isSoulless() && data.level(SoulPower.SPIDER_FANG) > 0) {
				cir.setReturnValue(true);
			}
		}
	}
}
