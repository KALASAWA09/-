package com.kalasawa.sacredsouls.spirit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
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
import com.kalasawa.sacredsouls.title.SoulTitle;

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
	private static final Identifier WATER_SWIM = SacredSouls.id("spirit_water_swim");
	private static final Map<UUID, Boolean> WAS_IN_WATER = new HashMap<>();

	private static final Map<UUID, Long> DEATH_READY = new HashMap<>();
	private static final Map<UUID, Long> WATER_READY = new HashMap<>();
	private static final Map<UUID, Long> FIRE_HIT_READY = new HashMap<>();
	private static final Map<UUID, Long> FIRE_RING_READY = new HashMap<>();
	private static final Map<UUID, Long> LIGHT_FLASH_READY = new HashMap<>();
	/** Health and food from the last second, to notice healing and eating. */
	private static final Map<UUID, float[]> LAST_VITALS = new HashMap<>();
	private static final ParticleOptions GOLD_DUST = new DustParticleOptions(0xFFD86B, 0.8f);

	private SpiritHandler() {
	}

	private static float pick(int level, float a, float b, float c) {
		return level >= 3 ? c : level == 2 ? b : a;
	}

	/** Spirit King holds every spirit at this level. */
	private static final int KING_LEVEL = 2;

	private static boolean isKing(SoulData data) {
		return !data.isSoulless() && data.soulTitle() == SoulTitle.SPIRIT_KING;
	}

	/** Level of the spirit if the player can use it: their bound spirit, or every spirit for the Spirit King. */
	private static int level(ServerPlayer player, Spirit spirit) {
		SoulData data = SoulManager.get(player);
		if (data.isSoulless()) {
			return 0;
		}
		int own = data.spirit() == spirit ? data.spiritLevel() : 0;
		return isKing(data) ? Math.max(own, KING_LEVEL) : own;
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
			// Sparks and embers.
			case FIRE -> mote % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME;
			// Soft golden light.
			case LIGHT -> GOLD_DUST;
			// Bubbles only live in water; in air they show as popping bubbles.
			case WATER -> mote % 2 == 1 ? ParticleTypes.SPLASH : player.isInWater() ? ParticleTypes.BUBBLE : ParticleTypes.BUBBLE_POP;
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
		if (isKing(data)) {
			// Spirit King: three motes that cycle through all eight elements.
			Spirit[] all = Spirit.values();
			for (int i = 0; i < 3; i++) {
				Spirit element = all[(int) ((time / 6 + i * 3) % all.length)];
				double angle = time * 0.18 + i * (Math.PI * 2 / 3);
				double height = 0.3 + 0.7 * (0.5 + 0.5 * Math.sin(time * 0.07 + i * 2.1));
				level.sendParticles(particle(element, player, i), player.getX() + Math.cos(angle) * 0.9, player.getY() + height,
						player.getZ() + Math.sin(angle) * 0.9, 1, 0.0, 0.0, 0.0, 0.0);
			}
			return;
		}
		Spirit spirit = data.spirit();
		if (spirit == null || data.isSoulless()) {
			return;
		}
		// Fire: a little smoke trails behind while walking.
		if (spirit == Spirit.FIRE && player.getDeltaMovement().horizontalDistanceSqr() > 0.001) {
			level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.2, 0.0, 0.2, 0.0);
		}
		// Water: a spray of droplets on jumping or diving in.
		if (spirit == Spirit.WATER) {
			boolean inWater = player.isInWater();
			boolean dived = inWater && !WAS_IN_WATER.getOrDefault(player.getUUID(), false);
			WAS_IN_WATER.put(player.getUUID(), inWater);
			if (dived || (!player.onGround() && !inWater && player.getDeltaMovement().y > 0.25)) {
				level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.2, player.getZ(), 12, 0.4, 0.1, 0.4, 0.1);
			}
		}
		// Light: now and then a glowing End Rod speck drifts off.
		if (spirit == Spirit.LIGHT && time % 15 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.4, 0.5, 0.4, 0.005);
		}
		int motes = data.spiritLevel() >= 3 ? 3 : 2;
		for (int i = 0; i < motes; i++) {
			double angle = time * 0.18 + i * (Math.PI * 2 / motes);
			double height = 0.3 + 0.7 * (0.5 + 0.5 * Math.sin(time * 0.07 + i * 2.1));
			double x = player.getX() + Math.cos(angle) * 0.8;
			double z = player.getZ() + Math.sin(angle) * 0.8;
			level.sendParticles(particle(spirit, player, i), x, player.getY() + height, z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** Every second: effects and attributes. */
	public static void second(ServerPlayer player, ServerLevel level) {
		// Old modifiers from earlier spirit versions.
		setModifier(player, Attributes.MOVEMENT_SPEED, WIND_SPEED, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
		setModifier(player, Attributes.ARMOR, STONE_ARMOR, 0, AttributeModifier.Operation.ADD_VALUE, false);
		setModifier(player, Attributes.ARMOR_TOUGHNESS, STONE_TOUGHNESS, 0, AttributeModifier.Operation.ADD_VALUE, false);

		// Fire: immune to fire and lava.
		if (level(player, Spirit.FIRE) > 0) {
			Effects.keep(player, "fire_resistance", 0, 40);
		}

		// Light: night vision; regeneration in bright light, where bad effects also wear off faster.
		int light = level(player, Spirit.LIGHT);
		if (light > 0) {
			Effects.keep(player, "night_vision", 0, 300);
			if (level.getMaxLocalRawBrightness(player.blockPosition()) >= 12) {
				Effects.keep(player, "regeneration", light >= 3 ? 1 : 0, 40);
				if (light >= 2) {
					shortenHarmful(player, 4);
				}
			}
		}
		lightFlash(player, level, light);

		// Water: breathe and see underwater, swim fast; strong in water or rain (level 2).
		int water = level(player, Spirit.WATER);
		if (water > 0) {
			Effects.keep(player, "water_breathing", 0, 40);
			Effects.keep(player, "conduit_power", 0, 40);
		}
		setModifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY, WATER_SWIM, water >= 2 ? 0.66 : 0.33, AttributeModifier.Operation.ADD_VALUE, water > 0);
		if (water >= 2 && player.isInWaterOrRain()) {
			Effects.keep(player, "strength", 0, 40);
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

	/** Takes extra ticks off every harmful effect (20 ticks a second, so 4 is 20% faster). */
	private static void shortenHarmful(ServerPlayer player, int ticks) {
		for (MobEffectInstance effect : java.util.List.copyOf(player.getActiveEffects())) {
			if (effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL || effect.isInfiniteDuration()) {
				continue;
			}
			int left = effect.getDuration() - ticks;
			player.removeEffect(effect.getEffect());
			if (left > 0) {
				player.addEffect(new MobEffectInstance(effect.getEffect(), left, effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon()));
			}
		}
	}

	/** Light level 3: healing or eating may let out a flash that dazes enemies within 3 blocks. */
	private static void lightFlash(ServerPlayer player, ServerLevel level, int light) {
		float[] last = LAST_VITALS.get(player.getUUID());
		float health = player.getHealth();
		float food = player.getFoodData().getFoodLevel();
		LAST_VITALS.put(player.getUUID(), new float[] {health, food});
		if (light < 3 || last == null || (health <= last[0] && food <= last[1])) {
			return;
		}
		long now = level.getGameTime();
		if (LIGHT_FLASH_READY.getOrDefault(player.getUUID(), 0L) > now || player.getRandom().nextFloat() >= 0.25f) {
			return;
		}
		LIGHT_FLASH_READY.put(player.getUUID(), now + 400);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3), e -> enemy(player, e))) {
			Effects.give(target, "blindness", 0, 20);
			Effects.give(target, "slowness", 2, 20);
		}
		level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.5, 0.6, 0.5, 0.1);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.5f);
	}

	/** Melee damage the player deals. */
	public static float modifyOutgoingDamage(ServerPlayer attacker, LivingEntity victim, float amount) {
		int light = level(attacker, Spirit.LIGHT);
		if (light >= 2 && victim.getType().is(EntityTypeTags.UNDEAD)) {
			amount *= 1.20f;
		}
		// Fire: hits on a burning enemy land harder (level 2); a hit sets the enemy alight, once every 2.5s.
		int fire = level(attacker, Spirit.FIRE);
		if (fire >= 2 && victim.isOnFire()) {
			amount *= 1.10f;
		}
		long now = attacker.level().getGameTime();
		if (fire > 0 && FIRE_HIT_READY.getOrDefault(attacker.getUUID(), 0L) <= now) {
			FIRE_HIT_READY.put(attacker.getUUID(), now + 50);
			victim.igniteForTicks(Math.max(victim.getRemainingFireTicks(), 30));
			amount += 1.0f;
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
			attacker.hurtServer(level, level.damageSources().lightningBolt(), pick(lightning, 1.0f, 2.0f, 3.0f));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, attacker.getX(), attacker.getY() + 1.0, attacker.getZ(), 20, 0.3, 0.6, 0.3, 0.2);
			level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.4f, 1.6f);
		}
		// Fire level 3: a ring of flame bursts out at low health, pushing enemies back and setting them alight.
		int fire = level(player, Spirit.FIRE);
		long time = level.getGameTime();
		if (fire >= 3 && player.getHealth() - amount < player.getMaxHealth() * 0.3f
				&& FIRE_RING_READY.getOrDefault(player.getUUID(), 0L) <= time) {
			FIRE_RING_READY.put(player.getUUID(), time + 900);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(4), e -> enemy(player, e))) {
				target.knockback(1.2, player.getX() - target.getX(), player.getZ() - target.getZ());
				target.hurtMarked = true;
				target.igniteForSeconds(3.0f);
			}
			for (int i = 0; i < 32; i++) {
				double angle = i * Math.PI * 2 / 32;
				level.sendParticles(ParticleTypes.FLAME, player.getX() + Math.cos(angle) * 2.5, player.getY() + 0.3, player.getZ() + Math.sin(angle) * 2.5,
						2, 0.1, 0.1, 0.1, 0.02);
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 0.7f);
		}
		// Water level 3: a shield of water at low health, 4 hearts or 6 hearts in water.
		int water = level(player, Spirit.WATER);
		long now = level.getGameTime();
		if (water >= 3 && player.getHealth() - amount < player.getMaxHealth() * 0.4f
				&& WATER_READY.getOrDefault(player.getUUID(), 0L) <= now) {
			WATER_READY.put(player.getUUID(), now + 900);
			Effects.give(player, "absorption", player.isInWater() ? 2 : 1, 200);
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
