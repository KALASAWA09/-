package com.kalasawa.sacredsouls.spirit;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;

import com.kalasawa.sacredsouls.SacredSouls;
import com.kalasawa.sacredsouls.soul.Effects;
import com.kalasawa.sacredsouls.soul.SoulData;
import com.kalasawa.sacredsouls.soul.SoulManager;

/** Passive buffs of the bound spirit and its aura of particles. */
public final class SpiritHandler {
	private static final Identifier WIND_SPEED = SacredSouls.id("spirit_wind_speed");
	private static final Identifier EARTH_KNOCKBACK = SacredSouls.id("spirit_earth_knockback");
	private static final Identifier LIGHTNING_ATTACK_SPEED = SacredSouls.id("spirit_lightning_attack_speed");
	private static final Identifier STONE_ARMOR = SacredSouls.id("spirit_stone_armor");
	private static final Identifier STONE_TOUGHNESS = SacredSouls.id("spirit_stone_toughness");

	private SpiritHandler() {
	}

	private static float pick(int level, float a, float b, float c) {
		return level >= 3 ? c : level == 2 ? b : a;
	}

	/** Level of the spirit if it is the one the player has bound and can use, else 0. */
	private static int level(ServerPlayer player, Spirit spirit) {
		SoulData data = SoulManager.get(player);
		return !data.isSoulless() && data.spirit() == spirit ? data.spiritLevel() : 0;
	}

	private static ParticleOptions particle(Spirit spirit) {
		return switch (spirit) {
			case FIRE -> ParticleTypes.FLAME;
			case LIGHT -> ParticleTypes.END_ROD;
			case WATER -> ParticleTypes.FALLING_WATER;
			case WIND -> ParticleTypes.CLOUD;
			case EARTH -> ParticleTypes.COMPOSTER;
			case DARKNESS -> ParticleTypes.REVERSE_PORTAL;
			case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
			case STONE -> ParticleTypes.WHITE_ASH;
		};
	}

	/** Every tick: the aura, two motes circling the body. Higher levels add a third mote. */
	public static void tick(ServerPlayer player, ServerLevel level, long time) {
		if (time % 3 != 0 || player.isInvisible() || player.isSpectator()) {
			return;
		}
		SoulData data = SoulManager.get(player);
		Spirit spirit = data.spirit();
		if (spirit == null || data.isSoulless()) {
			return;
		}
		int motes = data.spiritLevel() >= 3 ? 3 : 2;
		ParticleOptions particle = particle(spirit);
		for (int i = 0; i < motes; i++) {
			double angle = time * 0.18 + i * (Math.PI * 2 / motes);
			double height = 0.3 + 0.7 * (0.5 + 0.5 * Math.sin(time * 0.07 + i * 2.1));
			double x = player.getX() + Math.cos(angle) * 0.8;
			double z = player.getZ() + Math.sin(angle) * 0.8;
			level.sendParticles(particle, x, player.getY() + height, z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** Every second: effects and attribute buffs. */
	public static void second(ServerPlayer player, ServerLevel level) {
		int fire = level(player, Spirit.FIRE);
		if (fire > 0) {
			Effects.keep(player, "fire_resistance", 0, 40);
		}

		int light = level(player, Spirit.LIGHT);
		if (light > 0) {
			Effects.keep(player, "night_vision", 0, 300);
		}
		if (light >= 2) {
			double radius = light >= 3 ? 16 : 10;
			for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius), m -> m instanceof Enemy && m.isAlive())) {
				Effects.keep(mob, "glowing", 0, 30);
			}
		}

		int water = level(player, Spirit.WATER);
		if (water > 0) {
			Effects.keep(player, "water_breathing", 0, 40);
		}
		if (water >= 2 && player.isInWaterOrRain()) {
			Effects.keep(player, "regeneration", 0, 40);
		}
		if (water >= 3 && player.isInWater()) {
			Effects.keep(player, "dolphins_grace", 0, 40);
		}

		int wind = level(player, Spirit.WIND);
		setModifier(player, Attributes.MOVEMENT_SPEED, WIND_SPEED, pick(wind, 0.05f, 0.10f, 0.15f), AttributeModifier.Operation.ADD_MULTIPLIED_BASE, wind > 0);

		int earth = level(player, Spirit.EARTH);
		setModifier(player, Attributes.KNOCKBACK_RESISTANCE, EARTH_KNOCKBACK, pick(earth, 0.2f, 0.35f, 0.5f), AttributeModifier.Operation.ADD_VALUE, earth > 0);
		if (earth > 0) {
			Effects.keep(player, "haste", earth >= 3 ? 1 : 0, 40);
		}

		int lightning = level(player, Spirit.LIGHTNING);
		setModifier(player, Attributes.ATTACK_SPEED, LIGHTNING_ATTACK_SPEED, pick(lightning, 0.10f, 0.15f, 0.20f), AttributeModifier.Operation.ADD_MULTIPLIED_BASE, lightning > 0);

		int stone = level(player, Spirit.STONE);
		setModifier(player, Attributes.ARMOR, STONE_ARMOR, pick(stone, 2, 3, 4), AttributeModifier.Operation.ADD_VALUE, stone > 0);
		setModifier(player, Attributes.ARMOR_TOUGHNESS, STONE_TOUGHNESS, 2, AttributeModifier.Operation.ADD_VALUE, stone >= 3);
	}

	/** Damage the player takes: wind softens falls, lightning cannot hurt a lightning spirit. */
	public static float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		int wind = level(player, Spirit.WIND);
		if (wind > 0 && source.is(DamageTypeTags.IS_FALL)) {
			amount *= 1.0f - pick(wind, 0.3f, 0.5f, 0.7f);
		}
		if (level(player, Spirit.LIGHTNING) > 0 && source.is(DamageTypes.LIGHTNING_BOLT)) {
			amount = 0.0f;
		}
		return amount;
	}

	/** After the player hits something in melee. */
	public static void onHit(ServerPlayer attacker, LivingEntity victim) {
		int fire = level(attacker, Spirit.FIRE);
		if (fire > 0 && attacker.getRandom().nextFloat() < pick(fire, 0.20f, 0.30f, 0.40f)) {
			victim.igniteForSeconds(3.0f);
		}
		int darkness = level(attacker, Spirit.DARKNESS);
		if (darkness > 0 && attacker.getRandom().nextFloat() < pick(darkness, 0.15f, 0.25f, 0.35f)) {
			Effects.give(victim, "blindness", 0, 40);
		}
	}

	private static void setModifier(ServerPlayer player, Holder<Attribute> type, Identifier id, double amount, AttributeModifier.Operation operation, boolean on) {
		AttributeInstance attribute = player.getAttribute(type);
		if (attribute == null) {
			return;
		}
		if (on) {
			attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		} else {
			attribute.removeModifier(id);
		}
	}
}
