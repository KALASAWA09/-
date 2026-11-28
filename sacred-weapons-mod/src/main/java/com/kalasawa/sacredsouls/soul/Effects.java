package com.kalasawa.sacredsouls.soul;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** Looks effects up by id so the code does not depend on MobEffects field names. */
public final class Effects {
	private Effects() {
	}

	public static Holder<MobEffect> get(String name) {
		return BuiltInRegistries.MOB_EFFECT.get(Identifier.withDefaultNamespace(name)).orElseThrow();
	}

	/** Gives a short, particle-free effect that is refreshed every second by the power tick. */
	public static void keep(LivingEntity entity, String name, int amplifier, int ticks) {
		entity.addEffect(new MobEffectInstance(get(name), ticks, amplifier, true, false, true));
	}

	public static void give(LivingEntity entity, String name, int amplifier, int ticks) {
		entity.addEffect(new MobEffectInstance(get(name), ticks, amplifier));
	}
}
