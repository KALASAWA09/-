package com.kalasawa.sacredsouls.spirit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import com.kalasawa.sacredsouls.SacredSouls;
import com.kalasawa.sacredsouls.soul.Effects;
import com.kalasawa.sacredsouls.soul.SoulData;
import com.kalasawa.sacredsouls.soul.SoulManager;

/**
 * Passive powers of the bound spirit and its aura of particles. Spirits are a tier above
 * soul items: each has a signature power that no soul item gives.
 */
public final class SpiritHandler {
	private static final Identifier WIND_SPEED = SacredSouls.id("spirit_wind_speed");
	private static final Identifier EARTH_KNOCKBACK = SacredSouls.id("spirit_earth_knockback");
	private static final Identifier LIGHTNING_ATTACK_SPEED = SacredSouls.id("spirit_lightning_attack_speed");
	private static final Identifier LIGHTNING_SPEED = SacredSouls.id("spirit_lightning_speed");
	private static final Identifier STONE_ARMOR = SacredSouls.id("spirit_stone_armor");
	private static final Identifier STONE_TOUGHNESS = SacredSouls.id("spirit_stone_toughness");

	private static final Map<UUID, Long> DEATH_READY = new HashMap<>();
	private static final Map<UUID, Long> WATER_READY = new HashMap<>();

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

	private static boolean enemy(ServerPlayer self, Entity other) {
		if (other == self || !(other instanceof LivingEntity living) || !living.isAlive() || self.isAlliedTo(other)) {
			return false;
		}
		if (other instanceof Player player) {
			return !player.isSpectator() && !player.isCreative();
		}
		return other instanceof Enemy;
	}

	private static boolean inDarkness(ServerPlayer player) {
		return player.level().getMaxLocalRawBrightness(player.blockPosition()) <= 7;
	}

	private static ParticleOptions particle(Spirit spirit, ServerPlayer player, int mote) {
		return switch (spirit) {
			case FIRE -> ParticleTypes.FLAME;
			case LIGHT -> ParticleTypes.END_ROD;
			// Bubbles only live in water; in air they show as popping bubbles.
			case WATER -> player.isInWater() ? ParticleTypes.BUBBLE : ParticleTypes.BUBBLE_POP;
			// Black smoke mixed with blue soul fire.
			case DEATH -> mote % 2 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMOKE;
			case EARTH -> ParticleTypes.COMPOSTER;
			// The black smoke a Wither Rose gives off.
			case DARKNESS -> ParticleTypes.SMOKE;
			case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
			case STONE -> ParticleTypes.WHITE_ASH;
		};
	}

	/** Every tick: the aura, motes circling the body. Level 3 adds a third mote. */
	public static void tick(ServerPlayer player, ServerLevel level, long time) {
		if (time % 3 != 0 || player.isInvisible() || player.isSpectator()) {
			return;
		}
		SoulData data = SoulManager.get(player);
		Spirit spirit = data.spirit();
		if (spirit == null || data.isSoulless()) {
			return;
		}
		// Light motes linger and glow, so the light aura is kept thin.
		if (spirit == Spirit.LIGHT && time % 9 != 0) {
			return;
		}
		int motes = spirit == Spirit.LIGHT ? 1 : data.spiritLevel() >= 3 ? 3 : 2;
		for (int i = 0; i < motes; i++) {
			double angle = time * 0.18 + i * (Math.PI * 2 / motes);
			double height = 0.3 + 0.7 * (0.5 + 0.5 * Math.sin(time * 0.07 + i * 2.1));
			double x = player.getX() + Math.cos(angle) * 0.8;
			double z = player.getZ() + Math.sin(angle) * 0.8;
			level.sendParticles(particle(spirit, player, i), x, player.getY() + height, z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** Every second: effects, attributes and the fire spirit's burning aura. */
	public static void second(ServerPlayer player, ServerLevel level) {
		// Old modifiers from earlier spirit versions.
		setModifier(player, Attributes.MOVEMENT_SPEED, WIND_SPEED, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
		setModifier(player, Attributes.ARMOR, STONE_ARMOR, 0, AttributeModifier.Operation.ADD_VALUE, false);
		setModifier(player, Attributes.ARMOR_TOUGHNESS, STONE_TOUGHNESS, 0, AttributeModifier.Operation.ADD_VALUE, false);

		// Fire: immune to fire and lava; every 2 seconds burns enemies nearby.
		int fire = level(player, Spirit.FIRE);
		if (fire > 0) {
			Effects.keep(player, "fire_resistance", 0, 40);
			if (player.tickCount % 40 < 20) {
				double radius = pick(fire, 3, 4, 5);
				for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius), e -> enemy(player, e))) {
					target.igniteForSeconds(3.0f);
					target.hurtServer(level, level.damageSources().inFire(), pick(fire, 2.0f, 3.0f, 4.0f));
				}
				level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 16, radius / 2, 0.1, radius / 2, 0.01);
			}
		}

		// Light: night vision; regeneration while standing in light.
		int light = level(player, Spirit.LIGHT);
		if (light > 0) {
			Effects.keep(player, "night_vision", 0, 300);
			if (level.getMaxLocalRawBrightness(player.blockPosition()) >= 10) {
				Effects.keep(player, "regeneration", light >= 3 ? 1 : 0, 40);
			}
		}

		// Water: breathe and see underwater anywhere.
		int water = level(player, Spirit.WATER);
		if (water > 0) {
			Effects.keep(player, "water_breathing", 0, 40);
			Effects.keep(player, "conduit_power", 0, 40);
		}

		// Death: Wither cannot touch you.
		if (level(player, Spirit.DEATH) > 0 && player.hasEffect(Effects.get("wither"))) {
			player.removeEffect(Effects.get("wither"));
		}

		// Earth: cannot be knocked back, digs fast, strong while standing on the ground.
		int earth = level(player, Spirit.EARTH);
		setModifier(player, Attributes.KNOCKBACK_RESISTANCE, EARTH_KNOCKBACK, 1.0, AttributeModifier.Operation.ADD_VALUE, earth > 0);
		if (earth > 0) {
			Effects.keep(player, "haste", earth >= 2 ? 1 : 0, 40);
		}
		if (earth >= 3 && player.onGround()) {
			Effects.keep(player, "strength", 0, 40);
		}

		// Lightning: fast feet and faster swings.
		int lightning = level(player, Spirit.LIGHTNING);
		setModifier(player, Attributes.MOVEMENT_SPEED, LIGHTNING_SPEED, pick(lightning, 0.10f, 0.15f, 0.20f), AttributeModifier.Operation.ADD_MULTIPLIED_BASE, lightning > 0);
		setModifier(player, Attributes.ATTACK_SPEED, LIGHTNING_ATTACK_SPEED, pick(lightning, 0.20f, 0.30f, 0.40f), AttributeModifier.Operation.ADD_MULTIPLIED_BASE, lightning > 0);
	}

	/** Melee damage the player deals. */
	public static float modifyOutgoingDamage(ServerPlayer attacker, LivingEntity victim, float amount) {
		int light = level(attacker, Spirit.LIGHT);
		if (light > 0 && victim.getType().is(EntityTypeTags.UNDEAD)) {
			amount *= 1.0f + pick(light, 0.25f, 0.35f, 0.50f);
		}
		int darkness = level(attacker, Spirit.DARKNESS);
		if (darkness > 0 && inDarkness(attacker)) {
			amount *= 1.0f + pick(darkness, 0.20f, 0.30f, 0.40f);
		}
		return amount;
	}

	/** Damage the player takes. */
	public static float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		ServerLevel level = (ServerLevel) player.level();
		if (level(player, Spirit.LIGHTNING) > 0 && source.is(DamageTypes.LIGHTNING_BOLT)) {
			return 0.0f;
		}
		int earth = level(player, Spirit.EARTH);
		if (earth > 0 && (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL))) {
			return 0.0f;
		}
		int stone = level(player, Spirit.STONE);
		if (stone > 0) {
			float cut = source.is(DamageTypeTags.IS_PROJECTILE) ? pick(stone, 0.40f, 0.50f, 0.60f) : pick(stone, 0.15f, 0.20f, 0.25f);
			amount *= 1.0f - cut;
		}
		// Lightning: shock whoever hits you in melee.
		int lightning = level(player, Spirit.LIGHTNING);
		if (lightning > 0 && source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
				&& attacker != player && player.getRandom().nextFloat() < pick(lightning, 0.25f, 0.35f, 0.45f)) {
			attacker.hurtServer(level, level.damageSources().lightningBolt(), pick(lightning, 4.0f, 6.0f, 8.0f));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, attacker.getX(), attacker.getY() + 1.0, attacker.getZ(), 20, 0.3, 0.6, 0.3, 0.2);
			level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.4f, 1.6f);
		}
		// Water: a shield of water when health runs low.
		int water = level(player, Spirit.WATER);
		long now = level.getGameTime();
		if (water > 0 && player.getHealth() - amount < player.getMaxHealth() * 0.4f
				&& WATER_READY.getOrDefault(player.getUUID(), 0L) <= now) {
			WATER_READY.put(player.getUUID(), now + 900);
			Effects.give(player, "absorption", water, 200);
			level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.8, 0.5, 0.1);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 0.8f, 1.2f);
		}
		return amount;
	}

	/** After the player hits something in melee. */
	public static void onHit(ServerPlayer attacker, LivingEntity victim) {
		int darkness = level(attacker, Spirit.DARKNESS);
		if (darkness > 0 && attacker.getRandom().nextFloat() < pick(darkness, 0.25f, 0.35f, 0.50f)) {
			Effects.give(victim, "blindness", 0, 40);
		}
		int death = level(attacker, Spirit.DEATH);
		if (death > 0 && attacker.getRandom().nextFloat() < pick(death, 0.15f, 0.25f, 0.35f)) {
			Effects.give(victim, "wither", 1, 60);
		}
	}

	/** The death spirit drinks life from every kill. */
	public static void onKill(ServerPlayer killer, LivingEntity victim) {
		int death = level(killer, Spirit.DEATH);
		if (death > 0) {
			killer.heal(pick(death, 4.0f, 6.0f, 8.0f));
			((ServerLevel) killer.level()).sendParticles(ParticleTypes.SOUL, victim.getX(), victim.getY() + 1.0, victim.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
		}
	}

	/** Death spirit: refuse a fatal blow once in a while. Returns false to cancel the death. */
	public static boolean allowDeath(ServerPlayer player) {
		int death = level(player, Spirit.DEATH);
		if (death <= 0) {
			return true;
		}
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		if (DEATH_READY.getOrDefault(player.getUUID(), 0L) > now) {
			return true;
		}
		DEATH_READY.put(player.getUUID(), now + (long) pick(death, 6000, 4800, 3600));
		player.setHealth(4.0f);
		Effects.give(player, "resistance", 2, 60);
		level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.8, 0.5, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.6f, 1.6f);
		player.displayClientMessage(SoulManager.msg("death_spirit_saved").withStyle(ChatFormatting.DARK_AQUA), false);
		return false;
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
