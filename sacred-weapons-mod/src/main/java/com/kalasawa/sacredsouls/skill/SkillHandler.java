package com.kalasawa.sacredsouls.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.kalasawa.sacredsouls.network.SkillCooldownPayload;
import com.kalasawa.sacredsouls.soul.Effects;
import com.kalasawa.sacredsouls.soul.ModAttachments;
import com.kalasawa.sacredsouls.soul.SoulData;
import com.kalasawa.sacredsouls.soul.SoulManager;
import com.kalasawa.sacredsouls.spirit.Spirit;
import com.kalasawa.sacredsouls.title.SoulTitle;

/**
 * Active skills. Each cast warns first (a ring or a charge others can see), then lands.
 * Damage is explosion damage owned by the caster, so armor and Blast Protection count and kills are credited.
 */
public final class SkillHandler {
	private static final Map<UUID, Map<Skill, Long>> READY = new HashMap<>();
	private static final Map<UUID, Long> SHARED_READY = new HashMap<>();
	private static final List<Cast> CASTS = new ArrayList<>();
	private static final Map<UUID, BloodPact> BLOOD = new HashMap<>();

	private static final ParticleOptions GOLD = new DustParticleOptions(0xFFE08A, 1.2f);
	private static final ParticleOptions BLOOD_RED = new DustParticleOptions(0xA0121E, 1.3f);
	private static final ParticleOptions SHADOW = new DustParticleOptions(0x2A1440, 1.6f);
	private static final ParticleOptions KING_GOLD = new DustParticleOptions(0xFFE6A0, 3.0f);
	private static final ParticleOptions KING_SHADOW = new DustParticleOptions(0xC8B4FF, 2.0f);
	private static final ParticleOptions KING_CORE = new DustParticleOptions(0xFFFFFF, 4.0f);
	// Big dust for the spirit skills: fewer, larger particles read better from afar and cost less.
	private static final ParticleOptions FIRE_DUST = new DustParticleOptions(0xFF6A1A, 3.0f);
	private static final ParticleOptions FIRE_CORE = new DustParticleOptions(0xFFD24A, 4.0f);
	private static final ParticleOptions LIGHT_DUST = new DustParticleOptions(0xFFE08A, 2.5f);
	private static final ParticleOptions PILLAR_DUST = new DustParticleOptions(0xFFF6D0, 4.0f);
	private static final ParticleOptions WATER_DUST = new DustParticleOptions(0x2F7FE0, 3.5f);
	private static final ParticleOptions DEATH_DUST = new DustParticleOptions(0x5A2A8A, 3.0f);
	private static final ParticleOptions NIGHT_DUST = new DustParticleOptions(0x120A1E, 4.0f);
	private static final ParticleOptions SPARK_DUST = new DustParticleOptions(0x9FE8FF, 2.5f);
	private static final ParticleOptions ICE_DUST = new DustParticleOptions(0xBFEFFF, 3.0f);
	private static final ParticleOptions ASH_DUST = new DustParticleOptions(0x5A5450, 4.0f);
	private static final ParticleOptions BLOOD_BIG = new DustParticleOptions(0x8A0A14, 3.0f);

	private static final class BloodPact {
		float healed;
	}

	private SkillHandler() {
	}

	// ---------------------------------------------------------------- who can use what

	/** Skills this player may cast: their bound spirit's at level 3, or all of them for the Spirit King. */
	public static List<Skill> available(ServerPlayer player) {
		return available(SoulManager.get(player));
	}

	public static List<Skill> available(SoulData data) {
		List<Skill> skills = new ArrayList<>();
		if (data.isSoulless()) {
			return skills;
		}
		if (data.soulTitle() == SoulTitle.SPIRIT_KING) {
			for (Skill skill : Skill.values()) {
				skills.add(skill);
			}
			return skills;
		}
		Spirit spirit = data.spirit();
		if (spirit != null && data.spiritLevel() >= 3) {
			skills.add(Skill.of(spirit));
		}
		return skills;
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

	private static List<LivingEntity> enemiesNear(ServerPlayer player, Vec3 center, double radius) {
		return player.level().getEntitiesOfClass(LivingEntity.class, AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
				e -> enemy(player, e) && e.position().distanceTo(center) <= radius);
	}

	/** The enemy an aimed skill locks onto: a player before a mob, then the nearest. */
	private static LivingEntity autoTarget(ServerPlayer player, double range) {
		return enemiesNear(player, player.position(), range).stream()
				.min(Comparator.<LivingEntity>comparingInt(e -> e instanceof Player ? 0 : 1)
						.thenComparingDouble(e -> e.distanceTo(player)))
				.orElse(null);
	}

	private static void hit(ServerLevel level, ServerPlayer player, LivingEntity target, float amount) {
		target.invulnerableTime = 0;
		target.hurtServer(level, level.damageSources().explosion(null, player), amount);
	}

	// ---------------------------------------------------------------- cooldowns

	private static long readyAt(ServerPlayer player, Skill skill) {
		if (skill == Skill.KING) {
			Long ready = player.getAttached(ModAttachments.ULTIMATE_READY);
			return ready == null ? 0L : ready;
		}
		return READY.getOrDefault(player.getUUID(), Map.of()).getOrDefault(skill, 0L);
	}

	private static void setReady(ServerPlayer player, Skill skill, long at) {
		if (skill == Skill.KING) {
			player.setAttached(ModAttachments.ULTIMATE_READY, at);
		} else {
			READY.computeIfAbsent(player.getUUID(), u -> new EnumMap<>(Skill.class)).put(skill, at);
		}
	}

	/** Tells the client how long each skill still has to wait. */
	public static void sync(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, SkillCooldownPayload.TYPE)) {
			return;
		}
		long now = player.level().getGameTime();
		StringBuilder text = new StringBuilder();
		for (Skill skill : Skill.values()) {
			text.append(skill.id).append(':').append(Math.max(0L, readyAt(player, skill) - now)).append(';');
		}
		text.append("shared:").append(Math.max(0L, SHARED_READY.getOrDefault(player.getUUID(), 0L) - now));
		ServerPlayNetworking.send(player, new SkillCooldownPayload(text.toString()));
	}

	private static void say(ServerPlayer player, String key, Object... args) {
		player.displayClientMessage(SoulManager.msg(key, args).withStyle(ChatFormatting.RED), true);
	}

	// ---------------------------------------------------------------- casting

	public static void tryCast(ServerPlayer player, String id) {
		Skill skill = Skill.byId(id);
		if (skill == null || !player.isAlive() || player.isSpectator()) {
			return;
		}
		if (!available(player).contains(skill)) {
			say(player, "skill_locked");
			return;
		}
		for (Cast cast : CASTS) {
			if (cast.player == player && cast.age < cast.castTicks) {
				say(player, "skill_busy");
				return;
			}
		}
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		long shared = SHARED_READY.getOrDefault(player.getUUID(), 0L);
		long ready = readyAt(player, skill);
		if (ready > now || shared > now) {
			long wait = Math.max(ready, shared) - now;
			say(player, "skill_cooldown", skill.displayName(), formatTime(wait));
			sync(player);
			return;
		}
		if (skill == Skill.BLOOD && player.getHealth() <= 7.0f) {
			say(player, "skill_blood_low");
			return;
		}
		setReady(player, skill, now + skill.cooldownTicks);
		SHARED_READY.put(player.getUUID(), now + Skill.SHARED_COOLDOWN_TICKS);
		Cast cast = new Cast(player, level, skill);
		CASTS.add(cast);
		if (skill != Skill.KING && cast.castTicks > 0) {
			Effects.give(player, "slowness", 1, cast.castTicks);
		}
		sync(player);
	}

	private static String formatTime(long ticks) {
		long seconds = (ticks + 19) / 20;
		return seconds >= 60 ? (seconds / 60) + ":" + String.format("%02d", seconds % 60) : Long.toString(seconds);
	}

	public static void tick(MinecraftServer server) {
		for (Iterator<Cast> it = CASTS.iterator(); it.hasNext();) {
			Cast cast = it.next();
			if (cast.player.isRemoved() || !cast.player.isAlive() || cast.player.level() != cast.level) {
				cast.cancel();
				it.remove();
				continue;
			}
			cast.tick();
			cast.age++;
			if (cast.done) {
				it.remove();
			}
		}
		BlockChanges.tick(server);
	}

	public static void onLeave(ServerPlayer player) {
		CASTS.removeIf(cast -> {
			if (cast.player == player) {
				cast.cancel();
				return true;
			}
			return false;
		});
		BLOOD.remove(player.getUUID());
	}

	// ---------------------------------------------------------------- blood pact hooks

	/** Blood pact: +15% melee damage while it lasts. */
	public static float modifyOutgoing(ServerPlayer attacker, float amount) {
		return BLOOD.containsKey(attacker.getUUID()) ? amount * 1.15f : amount;
	}

	/** Blood pact: melee hits drink 20% back, at most 1 heart a hit. */
	public static void onHit(ServerPlayer attacker, LivingEntity victim, float damageTaken) {
		BloodPact pact = BLOOD.get(attacker.getUUID());
		if (pact == null) {
			return;
		}
		float heal = Math.min(damageTaken * 0.20f, 2.0f);
		attacker.heal(heal);
		pact.healed += heal;
		((ServerLevel) attacker.level()).sendParticles(BLOOD_RED, victim.getX(), victim.getY() + 1.0, victim.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
	}

	// ---------------------------------------------------------------- shapes and helpers

	private static Vec3 ground(ServerLevel level, Vec3 point) {
		BlockPos pos = BlockPos.containing(point);
		for (int i = 0; i < 16; i++) {
			BlockState below = level.getBlockState(pos.below());
			if (below.blocksMotion()) {
				return new Vec3(point.x, pos.getY(), point.z);
			}
			pos = pos.below();
		}
		return point;
	}

	private static Vec3 aim(ServerPlayer player, double range) {
		HitResult hit = player.pick(range, 1.0f, false);
		Vec3 point = hit.getType() == HitResult.Type.MISS
				? player.getEyePosition().add(player.getLookAngle().scale(range)) : hit.getLocation();
		return ground((ServerLevel) player.level(), point);
	}

	private static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points, double height, double phase) {
		for (int i = 0; i < points; i++) {
			double angle = phase + i * Math.PI * 2 / points;
			level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y + height, center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
		}
	}

	/** A ring seen from far away, for the ultimate. */
	private static void farRing(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points, double phase, double jitter) {
		for (int i = 0; i < points; i++) {
			double angle = phase + i * Math.PI * 2 / points;
			level.sendParticles(particle, true, true, center.x + Math.cos(angle) * radius, center.y, center.z + Math.sin(angle) * radius,
					1, jitter, 0, jitter, 0);
		}
	}

	private static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, double step) {
		Vec3 delta = to.subtract(from);
		int steps = Math.max(1, (int) (delta.length() / step));
		for (int i = 0; i <= steps; i++) {
			Vec3 p = from.add(delta.scale((double) i / steps));
			level.sendParticles(particle, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
	}

	private static void sound(ServerLevel level, Vec3 at, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	private static void sound(ServerLevel level, Vec3 at, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	private static int regrow() {
		return SkillConfig.regrowSeconds * 20;
	}

	/** A bowl of natural blocks under a point. */
	private static void crater(ServerLevel level, ServerPlayer owner, Vec3 center, double radius) {
		BlockPos base = BlockPos.containing(center);
		int r = (int) Math.ceil(radius);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dy = -r; dy <= 1; dy++) {
					double d = Math.sqrt(dx * dx + dz * dz + (dy < 0 ? dy * dy * 1.6 : 0));
					if (d <= radius) {
						BlockChanges.breakBlock(level, owner, base.offset(dx, dy, dz), true, regrow());
					}
				}
			}
		}
	}

	/** Calls the action for the top block of every column in a disc. */
	private static void surface(ServerLevel level, Vec3 center, double radius, java.util.function.BiConsumer<BlockPos, BlockState> action) {
		BlockPos base = BlockPos.containing(center);
		int r = (int) Math.ceil(radius);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > radius * radius) {
					continue;
				}
				for (int dy = 2; dy >= -3; dy--) {
					BlockPos pos = base.offset(dx, dy, dz);
					BlockState state = level.getBlockState(pos);
					if (!state.isAir()) {
						action.accept(pos, state);
						break;
					}
				}
			}
		}
	}

	private static ParticleOptions spiritParticle(Spirit spirit) {
		return switch (spirit) {
			case FIRE -> ParticleTypes.FLAME;
			case LIGHT -> ParticleTypes.END_ROD;
			case WATER -> ParticleTypes.SPLASH;
			case DEATH -> ParticleTypes.SOUL;
			case DARKNESS -> ParticleTypes.SQUID_INK;
			case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
			case ICE -> ParticleTypes.SNOWFLAKE;
			case BLOOD -> BLOOD_RED;
		};
	}

	// ---------------------------------------------------------------- one cast in flight

	private static final class Cast {
		final ServerPlayer player;
		final ServerLevel level;
		final Skill skill;
		final int castTicks;
		Vec3 target;
		/** The enemy this cast locked onto, if any: aimed skills follow it during the warning. */
		final LivingEntity locked;
		final Vec3 start;
		final Vec3 direction;
		final float startHealth;
		final List<LivingEntity> marked = new ArrayList<>();
		final List<LivingEntity> wallCheck = new ArrayList<>();
		final List<LivingEntity> bolts = new ArrayList<>();
		final List<Vec3> extra = new ArrayList<>();
		int age;
		boolean done;

		Cast(ServerPlayer player, ServerLevel level, Skill skill) {
			this.player = player;
			this.level = level;
			this.skill = skill;
			this.castTicks = skill == Skill.KING ? SkillConfig.ultimateCastSeconds * 20 : skill.castTicks;
			this.start = player.position();
			double lockRange = switch (skill) {
				case FIRE -> 32;
				case LIGHT -> 24;
				case WATER, LIGHTNING -> 16;
				default -> 0;
			};
			this.locked = lockRange > 0 ? autoTarget(player, lockRange) : null;
			Vec3 look = locked != null ? locked.position().subtract(start) : player.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			this.direction = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
			this.target = switch (skill) {
				case FIRE -> locked != null ? ground(level, locked.position().add(0, 0.5, 0)) : aim(player, 32);
				case LIGHT -> locked != null ? ground(level, locked.position().add(0, 0.5, 0)) : aim(player, 24);
				default -> start;
			};
			this.startHealth = player.getHealth();
		}

		void cancel() {
			if (skill == Skill.KING && age < castTicks) {
				setReady(player, Skill.KING, level.getGameTime() + Skill.KING.cooldownTicks / 2);
			}
			BLOOD.remove(player.getUUID());
		}

		void tick() {
			// Aimed skills keep following the locked enemy until just before they land.
			if ((skill == Skill.FIRE || skill == Skill.LIGHT) && locked != null && locked.isAlive() && age < castTicks - 6) {
				target = ground(level, locked.position().add(0, 0.5, 0));
			}
			switch (skill) {
				case FIRE -> fire();
				case LIGHT -> light();
				case WATER -> water();
				case DEATH -> death();
				case DARKNESS -> darkness();
				case LIGHTNING -> lightning();
				case ICE -> ice();
				case BLOOD -> blood();
				case KING -> king();
			}
		}

		// ---- Fire: อุกกาบาตเพลิง. A huge meteor falls on the marked spot, with smaller ones raining around it.
		void fire() {
			if (age == 0) {
				sound(level, start, SoundEvents.BLAZE_SHOOT, 2.0f, 0.5f);
				sound(level, target, SoundEvents.ENDER_DRAGON_GROWL, 1.5f, 1.6f);
				for (int i = 0; i < 4; i++) {
					double angle = i * Math.PI / 2 + level.random.nextDouble();
					double r = 4.0 + level.random.nextDouble() * 3.0;
					extra.add(ground(level, target.add(Math.cos(angle) * r, 2.0, Math.sin(angle) * r)));
				}
			}
			if (age < castTicks) {
				double t = (double) age / castTicks;
				// The marked ground glows: a big ring of fire and magma dust.
				if (age % 3 == 0) {
					farRing(level, FIRE_DUST, target.add(0, 0.15, 0), 7.0, 28, age * 0.05, 0.1);
					farRing(level, ParticleTypes.FLAME, target.add(0, 0.2, 0), 7.0 * t, 14, -age * 0.08, 0.1);
				}
				// The meteor: a ball of fire falling from high above, trailing smoke.
				Vec3 ball = target.add(direction.scale(-10.0 * (1 - t))).add(0, 30.0 * (1 - t) + 1.0, 0);
				far(ParticleTypes.FLAME, ball, 12, 0.9, 0.9, 0.9, 0.02);
				far(FIRE_CORE, ball, 4, 0.6, 0.6, 0.6, 0);
				far(ParticleTypes.LAVA, ball, 2, 0.6, 0.6, 0.6, 0);
				far(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, ball.add(direction.scale(-1.5)).add(0, 1.5, 0), 2, 0.4, 0.4, 0.4, 0.01);
				for (int i = 0; i < extra.size(); i++) {
					double lag = Math.max(0, t - 0.2 - i * 0.1) / 0.8;
					Vec3 small = extra.get(i).add(direction.scale(-6.0 * (1 - lag))).add(0, 22.0 * (1 - lag) + 1.0, 0);
					far(ParticleTypes.FLAME, small, 4, 0.4, 0.4, 0.4, 0.01);
				}
				if (age == castTicks - 6) {
					sound(level, target, SoundEvents.FIRECHARGE_USE, 2.0f, 0.5f);
				}
				return;
			}
			int step = age - castTicks;
			if (step == 0) {
				far(ParticleTypes.EXPLOSION_EMITTER, target.add(0, 1, 0), 3, 2.0, 1.0, 2.0, 0);
				far(ParticleTypes.FLAME, target.add(0, 1, 0), 120, 3.5, 1.5, 3.5, 0.25);
				far(ParticleTypes.LAVA, target.add(0, 1, 0), 30, 3.0, 1.0, 3.0, 0);
				far(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, target.add(0, 2, 0), 25, 3.0, 2.0, 3.0, 0.03);
				sound(level, target, SoundEvents.GENERIC_EXPLODE, 3.0f, 0.6f);
				sound(level, target, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5f, 1.4f);
				for (LivingEntity enemy : enemiesNear(player, target, 7.0)) {
					hit(level, player, enemy, enemy.position().distanceTo(target) <= 3.5 ? 8.0f : 6.0f);
					enemy.igniteForSeconds(5.0f);
					knock(enemy, target, 1.4, 0.7);
				}
				crater(level, player, target, 4.0);
				scatterFire(target, 7.0, 0.35f);
			}
			// The shockwave rolls outward as a ring of flame.
			if (step < 8) {
				double r = 1.5 + step * 1.0;
				farRing(level, ParticleTypes.FLAME, target.add(0, 0.6, 0), r, 18 + step * 4, step * 0.3, 0.2);
				farRing(level, ParticleTypes.LARGE_SMOKE, target.add(0, 1.0, 0), r, 10, step * 0.2, 0.3);
			}
			// The small meteors land one after another.
			for (int i = 0; i < extra.size(); i++) {
				if (step == 4 + i * 5) {
					Vec3 at = extra.get(i);
					far(ParticleTypes.EXPLOSION, at.add(0, 1, 0), 3, 1.0, 0.5, 1.0, 0);
					far(ParticleTypes.FLAME, at.add(0, 1, 0), 40, 1.5, 0.8, 1.5, 0.15);
					sound(level, at, SoundEvents.GENERIC_EXPLODE, 1.5f, 1.0f);
					for (LivingEntity enemy : enemiesNear(player, at, 3.0)) {
						hit(level, player, enemy, 3.0f);
						enemy.igniteForSeconds(3.0f);
					}
					crater(level, player, at, 2.0);
				}
			}
			if (step % 5 == 0) {
				far(ParticleTypes.FLAME, target.add(0, 0.3, 0), 14, 5.0, 0.2, 5.0, 0.01);
				far(ParticleTypes.LARGE_SMOKE, target.add(0, 1.5, 0), 4, 4.0, 1.0, 4.0, 0.01);
			}
			if (step >= 80) {
				done = true;
			}
		}

		// ---- Light: พิพากษาสุริยะ. Great rune circles form, then a wide pillar of light falls from the sky.
		void light() {
			if (age == 0) {
				sound(level, target, SoundEvents.ENCHANTMENT_TABLE_USE, 2.0f, 0.6f);
				sound(level, target, SoundEvents.BEACON_POWER_SELECT, 2.0f, 0.8f);
			}
			if (age < castTicks) {
				double grow = Math.min(1.0, 0.25 + 1.2 * age / castTicks);
				if (age % 2 == 0) {
					farRing(level, LIGHT_DUST, target.add(0, 0.1, 0), 9.0 * grow, 40, age * 0.04, 0);
					farRing(level, LIGHT_DUST, target.add(0, 0.1, 0), 7.0 * grow, 30, -age * 0.05, 0);
					farRing(level, ParticleTypes.END_ROD, target.add(0, 0.3, 0), 4.0 * grow, 12, age * 0.1, 0);
					// A second circle hangs in the air above, turning the other way.
					farRing(level, LIGHT_DUST, target.add(0, 16, 0), 6.0 * grow, 26, -age * 0.06, 0);
				}
				// Eight rays rise from the outer circle.
				if (age % 4 == 0) {
					for (int i = 0; i < 8; i++) {
						double angle = i * Math.PI / 4 + age * 0.04;
						Vec3 base = target.add(Math.cos(angle) * 9.0 * grow, 0, Math.sin(angle) * 9.0 * grow);
						far(ParticleTypes.END_ROD, base.add(0, 1.5, 0), 3, 0.05, 1.5, 0.05, 0.0);
					}
				}
				far(ParticleTypes.ENCHANT, target.add(0, 1.5, 0), 10, 6.0, 1.5, 6.0, 0.8);
				if (age == castTicks / 2) {
					sound(level, target, SoundEvents.BEACON_ACTIVATE, 2.0f, 0.7f);
				}
				return;
			}
			int step = age - castTicks;
			if (step == 0) {
				sound(level, target, SoundEvents.BEACON_ACTIVATE, 3.0f, 1.2f);
				sound(level, target, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0f, 1.8f);
				sound(level, target, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 3.0f, 0.6f);
				far(ParticleTypes.FIREWORK, target.add(0, 1, 0), 150, 4.0, 1.5, 4.0, 0.3);
				far(ParticleTypes.EXPLOSION_EMITTER, target.add(0, 1, 0), 1, 0, 0, 0, 0);
				for (LivingEntity enemy : enemiesNear(player, target, 9.0)) {
					float damage = enemy.position().distanceTo(target) <= 4.0 ? 8.0f : 6.0f;
					if (enemy.getType().is(EntityTypeTags.UNDEAD)) {
						damage *= 1.5f;
						enemy.igniteForSeconds(4.0f);
					}
					hit(level, player, enemy, damage);
					Effects.give(enemy, "blindness", 0, 40);
					Effects.give(enemy, "glowing", 0, 100);
					knock(enemy, target, 0.8, 0.5);
				}
				player.heal(6.0f);
				crater(level, player, target, 2.5);
				surface(level, target, 9.0, (pos, state) -> {
					if (state.is(Blocks.GRASS_BLOCK)) {
						BlockChanges.replace(level, player, pos, Blocks.DIRT.defaultBlockState(), true, regrow());
					} else if (state.is(BlockTags.SNOW) || state.is(Blocks.SNOW) || state.is(BlockTags.ICE)) {
						BlockChanges.breakBlock(level, player, pos, true, regrow());
					}
				});
			}
			// The pillar: a wide column of light from 40 blocks up, burning for a second and a half.
			if (step < 30) {
				double width = step < 4 ? 2.5 : 2.5 * (1.0 - (step - 4) / 30.0);
				for (double y = 0; y <= 40; y += 2.0) {
					far(ParticleTypes.END_ROD, target.add(0, y, 0), 2, width * 0.5, 1.0, width * 0.5, 0.0);
					if (step % 2 == 0) {
						far(PILLAR_DUST, target.add(0, y, 0), 1, width * 0.4, 1.0, width * 0.4, 0);
					}
				}
			}
			// Six lances of light strike around the circle after the pillar.
			if (step >= 6 && step <= 21 && (step - 6) % 3 == 0) {
				int i = (step - 6) / 3;
				double angle = i * Math.PI / 3 + 0.4;
				Vec3 at = ground(level, target.add(Math.cos(angle) * 6.0, 2.0, Math.sin(angle) * 6.0));
				for (double y = 0; y <= 20; y += 1.0) {
					far(ParticleTypes.END_ROD, at.add(0, y, 0), 1, 0.15, 0.4, 0.15, 0.0);
				}
				far(ParticleTypes.FIREWORK, at.add(0, 0.5, 0), 25, 1.0, 0.3, 1.0, 0.15);
				sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 0.6f);
				for (LivingEntity enemy : enemiesNear(player, at, 2.5)) {
					hit(level, player, enemy, 2.0f);
				}
			}
			if (step >= 40) {
				done = true;
			}
		}

		// ---- Water: คลื่นยักษ์. A wall of water rises and rolls forward, sweeping enemies away.
		void water() {
			if (age == 0) {
				sound(level, start, SoundEvents.BUCKET_EMPTY, 2.0f, 0.5f);
				sound(level, start, SoundEvents.ELDER_GUARDIAN_CURSE, 1.0f, 1.4f);
			}
			if (age < castTicks) {
				// Water spirals up around the caster.
				Vec3 at = player.position();
				double t = (double) age / castTicks;
				for (int i = 0; i < 3; i++) {
					double angle = age * 0.6 + i * Math.PI * 2 / 3;
					far(WATER_DUST, at.add(Math.cos(angle) * 2.0, t * 3.0, Math.sin(angle) * 2.0), 2, 0.1, 0.1, 0.1, 0);
				}
				far(ParticleTypes.SPLASH, at.add(0, 0.5, 0), 12, 2.0, 0.3, 2.0, 0.1);
				return;
			}
			int step = age - castTicks;
			if (step == 0) {
				sound(level, start, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 3.0f, 0.4f);
				sound(level, start, SoundEvents.GENERIC_SPLASH, 3.0f, 0.5f);
			}
			// The wave: 4 blocks tall, widening as it rolls 16 blocks forward.
			double front = Math.min(16.0, step * 1.2 + 1.0);
			double halfWidth = 2.0 + front * 0.45;
			if (front < 16.0) {
				for (double side = -halfWidth; side <= halfWidth; side += 1.2) {
					Vec3 at = start.add(direction.scale(front)).add(-direction.z * side, 0, direction.x * side);
					at = ground(level, at.add(0, 2, 0));
					double curl = 1.0 - Math.abs(side) / halfWidth;
					double height = 1.5 + 2.5 * curl;
					far(WATER_DUST, at.add(0, height * 0.5, 0), 2, 0.3, height * 0.35, 0.3, 0);
					far(ParticleTypes.SPLASH, at.add(0, height, 0), 3, 0.3, 0.3, 0.3, 0.15);
					if (((int) (side * 10)) % 3 == 0) {
						far(ParticleTypes.CLOUD, at.add(0, height + 0.3, 0), 1, 0.2, 0.1, 0.2, 0.02);
						far(ParticleTypes.FALLING_WATER, at.add(0, height, 0), 2, 0.3, 0.3, 0.3, 0);
					}
				}
				if (step % 4 == 0) {
					sound(level, start.add(direction.scale(front)), SoundEvents.GENERIC_SPLASH, 1.5f, 0.6f + step * 0.03f);
				}
				// Whatever the front passes is struck once and carried along.
				boolean wet = level.isRainingAt(player.blockPosition().above()) || player.isInWater();
				for (LivingEntity enemy : enemiesNear(player, start, front + 1.0)) {
					if (marked.contains(enemy)) {
						continue;
					}
					Vec3 to = enemy.position().subtract(start);
					double along = to.dot(direction);
					double across = Math.abs(to.x * -direction.z + to.z * direction.x);
					if (along < front - 1.8 || along > front + 1.0 || across > halfWidth + 1.0) {
						continue;
					}
					marked.add(enemy);
					hit(level, player, enemy, wet ? 8.0f : 6.0f);
					enemy.clearFire();
					Effects.give(enemy, "slowness", 1, 60);
					enemy.setDeltaMovement(direction.x * 2.2, 0.55, direction.z * 2.2);
					enemy.hurtMarked = true;
					wallCheck.add(enemy);
				}
				// The wave sweeps away plants, leaves and snow.
				for (double side = -halfWidth; side <= halfWidth; side += 1.0) {
					Vec3 at = start.add(direction.scale(front)).add(-direction.z * side, 0, direction.x * side);
					for (int dy = 0; dy <= 3; dy++) {
						BlockPos pos = BlockPos.containing(at).above(dy);
						if (BlockChanges.isSoft(level.getBlockState(pos))) {
							BlockChanges.breakBlock(level, player, pos, true, regrow());
						}
					}
				}
			} else if (step == 14) {
				// The wave breaks at the end in a great spray.
				Vec3 end = start.add(direction.scale(16.0));
				far(ParticleTypes.SPLASH, end.add(0, 2, 0), 120, halfWidth * 0.6, 1.5, halfWidth * 0.6, 0.3);
				far(ParticleTypes.CLOUD, end.add(0, 2, 0), 20, halfWidth * 0.5, 1.0, halfWidth * 0.5, 0.05);
				sound(level, end, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 3.0f, 0.5f);
			}
			// Slammed into a wall: one more hit.
			for (Iterator<LivingEntity> it = wallCheck.iterator(); it.hasNext();) {
				LivingEntity enemy = it.next();
				if (enemy.horizontalCollision && enemy.isAlive()) {
					hit(level, player, enemy, 3.0f);
					far(ParticleTypes.SPLASH, enemy.position().add(0, 1, 0), 30, 0.6, 0.8, 0.6, 0.2);
					sound(level, enemy.position(), SoundEvents.GENERIC_SPLASH, 1.5f, 0.6f);
					it.remove();
				}
			}
			if (step >= 30) {
				done = true;
			}
		}

		// ---- Death: ยมทูตเก็บวิญญาณ. The ground turns to souls, a giant scythe sweeps, life flows back.
		void death() {
			Vec3 center = player.position();
			if (age == 0) {
				sound(level, start, SoundEvents.SOUL_ESCAPE, 3.0f, 0.5f);
				sound(level, start, SoundEvents.WITHER_AMBIENT, 1.5f, 0.6f);
				surface(level, start, 8.0, (pos, state) -> {
					if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
							|| state.is(Blocks.SAND) || state.is(Blocks.PODZOL)) {
						BlockChanges.replace(level, player, pos, Blocks.SOUL_SOIL.defaultBlockState(), true, regrow());
					}
				});
			}
			if (age < castTicks) {
				double t = (double) age / castTicks;
				if (age % 2 == 0) {
					farRing(level, ParticleTypes.SOUL_FIRE_FLAME, center.add(0, 0.2, 0), 9.0, 36, age * 0.06, 0.1);
					farRing(level, DEATH_DUST, center.add(0, 0.4, 0), 8.2, 24, -age * 0.05, 0.1);
				}
				// Souls rise from the ground and gather above the caster into the scythe.
				far(ParticleTypes.SOUL, center.add(0, 0.5, 0), 6, 6.0, 0.2, 6.0, 0.05);
				far(ParticleTypes.SCULK_SOUL, center.add(0, 4.0 + t * 2, 0), 3, 0.8, 0.8, 0.8, 0.02);
				return;
			}
			int step = age - castTicks;
			if (step < 10) {
				// The scythe: a wide arc of soul fire, a fifth of the circle per tick, twice around.
				for (int i = 0; i < 10; i++) {
					double angle = (step + i / 10.0) * Math.PI / 2.5;
					for (double r = 2.0; r <= 9.0; r += 1.4) {
						far(ParticleTypes.SOUL_FIRE_FLAME, center.add(Math.cos(angle) * r, 1.0 + r * 0.08, Math.sin(angle) * r), 1, 0, 0.1, 0, 0.0);
					}
					far(DEATH_DUST, center.add(Math.cos(angle) * 9.2, 1.2, Math.sin(angle) * 9.2), 1, 0.1, 0.3, 0.1, 0);
				}
				far(ParticleTypes.SWEEP_ATTACK, center.add(Math.cos(step * Math.PI / 2.5) * 5.0, 1.2, Math.sin(step * Math.PI / 2.5) * 5.0), 3, 1.5, 0.2, 1.5, 0);
				if (step % 3 == 0) {
					sound(level, center, SoundEvents.PLAYER_ATTACK_SWEEP, 2.0f, 0.5f);
				}
			}
			if (step == 0) {
				sound(level, center, SoundEvents.WITHER_SHOOT, 2.0f, 0.5f);
				float dealt = 0;
				for (LivingEntity enemy : enemiesNear(player, center, 9.0)) {
					float before = enemy.getHealth();
					hit(level, player, enemy, 7.0f);
					Effects.give(enemy, "wither", 1, 80);
					dealt += Math.max(0, before - enemy.getHealth());
					marked.add(enemy);
					knock(enemy, center, -0.6, 0.3);
				}
				player.heal(Math.min(dealt * 0.5f, 6.0f));
			}
			// The souls of everything struck stream back into the caster.
			if (step > 0 && step < 20 && step % 2 == 0) {
				for (LivingEntity enemy : marked) {
					line(level, ParticleTypes.SOUL, enemy.position().add(0, 1.2, 0), center.add(0, 1.2, 0), 0.8);
				}
			}
			if (step >= 22) {
				done = true;
			}
		}

		// ---- Darkness: ม่านราตรี. A dome of night for five seconds, then it collapses in a sonic blast.
		void darkness() {
			if (age == 0) {
				sound(level, start, SoundEvents.SCULK_SHRIEKER_SHRIEK, 3.0f, 0.5f);
				sound(level, start, SoundEvents.WARDEN_EMERGE, 1.5f, 1.0f);
			}
			if (age < castTicks) {
				double t = (double) age / castTicks;
				farRing(level, NIGHT_DUST, start.add(0, 0.3, 0), 10.0 * t + 1.0, 30, age * 0.1, 0.2);
				return;
			}
			int step = age - castTicks;
			if (step < 100) {
				if (step % 3 == 0) {
					// The dome: big black dust in latitude rings, so it reads as a shape and not a cloud.
					for (int band = 0; band < 5; band++) {
						double lat = band * Math.PI / 10;
						double r = 10.0 * Math.cos(lat);
						farRing(level, NIGHT_DUST, start.add(0, 10.0 * Math.sin(lat), 0), r, (int) (8 + r * 2.4), step * 0.03 + band, 0.2);
					}
					far(ParticleTypes.SQUID_INK, start.add(0, 1.5, 0), 10, 6.0, 1.5, 6.0, 0.01);
					far(ParticleTypes.LARGE_SMOKE, start.add(0, 1.0, 0), 6, 7.0, 1.0, 7.0, 0.005);
				}
				if (step % 10 == 0) {
					far(ParticleTypes.SCULK_SOUL, start.add(0, 1.0, 0), 6, 7.0, 1.5, 7.0, 0.02);
				}
				if (step % 20 == 0) {
					sound(level, start, SoundEvents.WARDEN_HEARTBEAT, 2.0f, 0.6f);
					for (LivingEntity enemy : enemiesNear(player, start, 10.0)) {
						Effects.give(enemy, "darkness", 0, 80);
						Effects.give(enemy, "slowness", 0, 30);
						hit(level, player, enemy, 1.0f);
					}
				}
				return;
			}
			if (step == 100) {
				// Collapse: the dome rushes inward and the centre bursts.
				for (int i = 0; i < 40; i++) {
					double angle = i * Math.PI * 2 / 40;
					Vec3 at = start.add(Math.cos(angle) * 10.0, 1.0, Math.sin(angle) * 10.0);
					far(ParticleTypes.SQUID_INK, at, 0, -Math.cos(angle), 0.05, -Math.sin(angle), 1.2);
				}
				sound(level, start, SoundEvents.WARDEN_SONIC_BOOM, 3.0f, 0.5f);
			}
			if (step == 106) {
				for (int i = 0; i < 8; i++) {
					double angle = i * Math.PI / 4;
					for (double r = 1.5; r <= 10.0; r += 1.5) {
						far(ParticleTypes.SONIC_BOOM, start.add(Math.cos(angle) * r, 1.0, Math.sin(angle) * r), 1, 0, 0, 0, 0);
					}
				}
				far(ParticleTypes.EXPLOSION_EMITTER, start.add(0, 1, 0), 1, 0, 0, 0, 0);
				far(ParticleTypes.LARGE_SMOKE, start.add(0, 1, 0), 40, 2.0, 1.0, 2.0, 0.15);
				sound(level, start, SoundEvents.GENERIC_EXPLODE, 3.0f, 0.5f);
				for (LivingEntity enemy : enemiesNear(player, start, 10.0)) {
					hit(level, player, enemy, 5.0f);
					knock(enemy, start, 1.2, 0.6);
				}
				crater(level, player, start.add(direction.scale(3.0)), 3.0);
				done = true;
			}
		}

		// ---- Lightning: พิโรธอัสนี. Dash forward, then lightning jumps between up to five enemies.
		void lightning() {
			Vec3 at = player.position();
			if (age == 0) {
				sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.5f, 1.4f);
				sound(level, at, SoundEvents.TRIDENT_THUNDER, 1.5f, 1.2f);
			}
			if (age < castTicks) {
				// Charge: sparks spin up the caster's body.
				for (int i = 0; i < 3; i++) {
					double angle = age * 0.9 + i * Math.PI * 2 / 3;
					far(ParticleTypes.ELECTRIC_SPARK, at.add(Math.cos(angle) * 1.2, age * 0.15, Math.sin(angle) * 1.2), 3, 0.1, 0.1, 0.1, 0.05);
				}
				far(SPARK_DUST, at.add(0, 1, 0), 2, 0.8, 1.0, 0.8, 0);
				return;
			}
			int step = age - castTicks;
			if (step == 0) {
				player.setDeltaMovement(direction.x * 2.6, 0.2, direction.z * 2.6);
				player.hurtMarked = true;
				sound(level, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5f, 1.6f);
			}
			if (step < 6) {
				far(ParticleTypes.ELECTRIC_SPARK, at.add(0, 0.9, 0), 20, 0.4, 0.6, 0.4, 0.1);
				far(SPARK_DUST, at.add(0, 0.9, 0), 3, 0.3, 0.5, 0.3, 0);
				for (LivingEntity enemy : enemiesNear(player, at, 2.5)) {
					if (!marked.contains(enemy)) {
						marked.add(enemy);
						hit(level, player, enemy, 3.0f); // Run through on the dash.
					}
				}
			}
			if (step == 5) {
				// Pick the chain: nearest enemy, then the nearest one to it that is not hit yet.
				Vec3 from = at;
				List<LivingEntity> chain = new ArrayList<>();
				for (int i = 0; i < 5; i++) {
					Vec3 origin = from;
					LivingEntity next = enemiesNear(player, origin, 12.0).stream()
							.filter(e -> !chain.contains(e))
							.min(Comparator.comparingDouble(e -> e.position().distanceTo(origin)))
							.orElse(null);
					if (next == null) {
						break;
					}
					chain.add(next);
					from = next.position();
				}
				if (chain.size() == 1) {
					chain.add(chain.get(0)); // A lone enemy is struck twice.
				}
				bolts.addAll(chain);
				if (chain.isEmpty()) {
					// Nobody near: the storm still strikes three times ahead.
					for (int i = 1; i <= 3; i++) {
						extra.add(ground(level, at.add(direction.scale(i * 4.0)).add(0, 2, 0)));
					}
				}
			}
			if (step >= 5 && (step - 5) % 4 == 0) {
				int index = (step - 5) / 4;
				if (index < bolts.size()) {
					LivingEntity enemy = bolts.get(index);
					if (enemy.isAlive()) {
						strike(enemy.position());
						hit(level, player, enemy, 4.0f);
						Effects.give(enemy, "slowness", 2, 20);
						Vec3 previous = index == 0 ? at : bolts.get(index - 1).position();
						line(level, ParticleTypes.ELECTRIC_SPARK, previous.add(0, 1.0, 0), enemy.position().add(0, 1.0, 0), 0.3);
						line(level, SPARK_DUST, previous.add(0, 1.0, 0), enemy.position().add(0, 1.0, 0), 0.8);
						BlockChanges.placeBriefly(level, player, enemy.blockPosition(), Blocks.FIRE.defaultBlockState(), 60);
					}
				} else if (index - bolts.size() < extra.size()) {
					Vec3 spot = extra.get(index - bolts.size());
					strike(spot);
					for (LivingEntity enemy : enemiesNear(player, spot, 3.0)) {
						hit(level, player, enemy, 4.0f);
					}
					crater(level, player, spot, 1.5);
				}
			}
			if (step > 5 + 4 * 6) {
				done = true;
			}
		}

		/** A visual lightning bolt with a flash of sparks where it lands. */
		void strike(Vec3 at) {
			LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.setPos(at.x, at.y, at.z);
				bolt.setVisualOnly(true);
				level.addFreshEntity(bolt);
			}
			far(ParticleTypes.ELECTRIC_SPARK, at.add(0, 0.5, 0), 40, 1.5, 0.5, 1.5, 0.3);
			farRing(level, SPARK_DUST, at.add(0, 0.2, 0), 2.5, 14, 0, 0.1);
		}

		// ---- Ice: คุกน้ำแข็ง. A blizzard bursts out, ice spires rise in rings, then everything shatters.
		void ice() {
			if (age == 0) {
				sound(level, start, SoundEvents.PLAYER_HURT_FREEZE, 2.0f, 0.5f);
				sound(level, start, SoundEvents.ELDER_GUARDIAN_CURSE, 1.0f, 0.8f);
			}
			if (age < castTicks) {
				double t = (double) age / castTicks;
				if (age % 2 == 0) {
					farRing(level, ICE_DUST, start.add(0, 0.2, 0), 9.0 * t + 1.0, 32, age * 0.07, 0.1);
				}
				far(ParticleTypes.SNOWFLAKE, start.add(0, 1.5, 0), 14, 8.0, 1.5, 8.0, 0.02);
				return;
			}
			int step = age - castTicks;
			if (step == 0) {
				sound(level, start, SoundEvents.GLASS_PLACE, 2.0f, 0.5f);
				sound(level, start, SoundEvents.GENERIC_EXPLODE, 1.5f, 1.6f);
				for (LivingEntity enemy : enemiesNear(player, start, 9.0)) {
					hit(level, player, enemy, 5.0f);
					Effects.give(enemy, "slowness", 9, 50);
					enemy.setTicksFrozen(Math.max(enemy.getTicksFrozen(), enemy.getTicksRequiredToFreeze() + 80));
					marked.add(enemy);
					// An ice prison grows around each trapped enemy, two blocks tall.
					BlockPos feet = enemy.blockPosition();
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							if (dx != 0 || dz != 0) {
								BlockChanges.placeBriefly(level, player, feet.offset(dx, 0, dz), Blocks.PACKED_ICE.defaultBlockState(), 50);
								BlockChanges.placeBriefly(level, player, feet.offset(dx, 1, dz), Blocks.ICE.defaultBlockState(), 50);
							}
						}
					}
				}
				// Water freezes, plants and snow are swept away.
				surface(level, start, 9.0, (pos, state) -> {
					if (state.is(Blocks.WATER)) {
						BlockChanges.replace(level, player, pos, Blocks.FROSTED_ICE.defaultBlockState(), true, regrow());
					} else if (BlockChanges.isSoft(state)) {
						BlockChanges.breakBlock(level, player, pos, true, regrow());
					}
				});
				far(ParticleTypes.SNOWFLAKE, start.add(0, 1, 0), 150, 6.0, 2.0, 6.0, 0.15);
			}
			// Three rings of ice spires burst up one after another, taller further out.
			for (int ringIndex = 0; ringIndex < 3; ringIndex++) {
				if (step == ringIndex * 4) {
					double r = 3.5 + ringIndex * 2.5;
					int count = 8 + ringIndex * 4;
					for (int i = 0; i < count; i++) {
						double angle = i * Math.PI * 2 / count + ringIndex * 0.3 + level.random.nextDouble() * 0.2;
						BlockPos base = BlockPos.containing(ground(level, start.add(Math.cos(angle) * r, 2.0, Math.sin(angle) * r)));
						int height = 2 + ringIndex + level.random.nextInt(2);
						for (int y = 0; y < height; y++) {
							BlockChanges.placeBriefly(level, player, base.above(y),
									(y == height - 1 ? Blocks.BLUE_ICE : Blocks.PACKED_ICE).defaultBlockState(), 50 - ringIndex * 4);
						}
						far(ICE_DUST, Vec3.atBottomCenterOf(base).add(0, height * 0.5, 0), 2, 0.3, height * 0.3, 0.3, 0);
					}
					sound(level, start, SoundEvents.GLASS_PLACE, 2.0f, 0.6f + ringIndex * 0.2f);
					for (LivingEntity enemy : enemiesNear(player, start, r + 1.0)) {
						if (enemy.position().distanceTo(start) > r - 1.5) {
							hit(level, player, enemy, 1.5f);
						}
					}
				}
			}
			if (step < 50 && step % 4 == 0) {
				far(ParticleTypes.SNOWFLAKE, start.add(0, 3, 0), 12, 9.0, 3.0, 9.0, 0.01);
			}
			if (step == 50) {
				sound(level, start, SoundEvents.GLASS_BREAK, 3.0f, 0.6f);
				far(ParticleTypes.ITEM_SNOWBALL, start.add(0, 1, 0), 120, 8.0, 1.5, 8.0, 0.2);
				for (LivingEntity enemy : marked) {
					if (enemy.isAlive() && enemy.position().distanceTo(start) <= 12.0) {
						hit(level, player, enemy, 4.0f);
						far(ParticleTypes.ITEM_SNOWBALL, enemy.position().add(0, 1, 0), 30, 0.6, 0.8, 0.6, 0.15);
					}
				}
				done = true;
			}
		}

		// ---- Blood: พันธสัญญาโลหิต. Pay 3 hearts for five seconds of power, then a crimson eruption.
		void blood() {
			Vec3 at = player.position();
			if (age == 0) {
				player.setHealth(Math.max(1.0f, player.getHealth() - 6.0f));
				sound(level, at, SoundEvents.WARDEN_HEARTBEAT, 3.0f, 0.8f);
				sound(level, at, SoundEvents.ELDER_GUARDIAN_CURSE, 1.0f, 0.6f);
			}
			if (age < castTicks) {
				// Blood spirals up out of the caster.
				for (int i = 0; i < 6; i++) {
					double t = (age * 6 + i) / (castTicks * 6.0);
					double angle = t * Math.PI * 8;
					far(BLOOD_BIG, at.add(Math.cos(angle) * 1.6, t * 4.0, Math.sin(angle) * 1.6), 1, 0, 0, 0, 0);
				}
				return;
			}
			int step = age - castTicks;
			if (step == 0) {
				BLOOD.put(player.getUUID(), new BloodPact());
				farRing(level, BLOOD_BIG, at.add(0, 0.2, 0), 4.0, 30, 0, 0.1);
			}
			if (step < 100) {
				if (step % 2 == 0) {
					double angle = step * 0.4;
					far(BLOOD_BIG, at.add(Math.cos(angle) * 1.0, 1.0, Math.sin(angle) * 1.0), 1, 0.05, 0.4, 0.05, 0);
					far(BLOOD_RED, at.add(-Math.cos(angle) * 1.0, 1.4, -Math.sin(angle) * 1.0), 2, 0.05, 0.4, 0.05, 0);
				}
				if (step % 20 == 0) {
					sound(level, at, SoundEvents.WARDEN_HEARTBEAT, 2.0f, 1.0f + step * 0.004f);
				}
				return;
			}
			if (step == 100) {
				BloodPact pact = BLOOD.remove(player.getUUID());
				float bonus = pact == null ? 0 : pact.healed * 0.5f;
				float damage = Math.min(5.0f + bonus, 9.0f);
				far(ParticleTypes.EXPLOSION_EMITTER, at.add(0, 1, 0), 1, 0, 0, 0, 0);
				far(BLOOD_BIG, at.add(0, 1.5, 0), 120, 3.5, 2.0, 3.5, 0);
				far(ParticleTypes.DAMAGE_INDICATOR, at.add(0, 1, 0), 30, 3.0, 1.0, 3.0, 0.2);
				sound(level, at, SoundEvents.GENERIC_EXPLODE, 3.0f, 0.5f);
				sound(level, at, SoundEvents.WARDEN_ROAR, 1.5f, 1.2f);
				for (LivingEntity enemy : enemiesNear(player, at, 8.0)) {
					hit(level, player, enemy, damage);
					Effects.give(enemy, "weakness", 0, 100);
					knock(enemy, at, 1.3, 0.7);
				}
				crater(level, player, at, 3.0);
			}
			// Crimson spikes burst outward in eight lines.
			if (step > 100 && step <= 108) {
				double r = (step - 100) * 1.0;
				for (int i = 0; i < 8; i++) {
					double angle = i * Math.PI / 4;
					Vec3 p = at.add(Math.cos(angle) * r, 0.3, Math.sin(angle) * r);
					far(BLOOD_BIG, p, 3, 0.15, 0.8, 0.15, 0);
				}
				farRing(level, BLOOD_RED, at.add(0, 0.5, 0), r, 12 + (step - 100) * 4, step * 0.2, 0.2);
			}
			if (step > 108) {
				done = true;
			}
		}

		// ---- shared bits for the skills above

		/** Particles seen from far away, up to the client's long particle range. */
		void far(ParticleOptions particle, Vec3 at, int count, double dx, double dy, double dz, double speed) {
			level.sendParticles(particle, true, true, at.x, at.y, at.z, count, dx, dy, dz, speed);
		}

		/** Pushes an enemy away from a point (or toward it, with a negative strength). */
		void knock(LivingEntity enemy, Vec3 from, double strength, double up) {
			Vec3 away = enemy.position().subtract(from);
			Vec3 flat = new Vec3(away.x, 0, away.z);
			Vec3 push = flat.lengthSqr() < 0.01 ? Vec3.ZERO : flat.normalize().scale(strength);
			enemy.setDeltaMovement(enemy.getDeltaMovement().add(push.x, up, push.z));
			enemy.hurtMarked = true;
		}

		/** Sets brief fires on the ground in a disc. */
		void scatterFire(Vec3 center, double radius, float chance) {
			surface(level, center, radius, (pos, state) -> {
				if (level.random.nextFloat() < chance) {
					BlockChanges.placeBriefly(level, player, pos.above(), Blocks.FIRE.defaultBlockState(), 60 + level.random.nextInt(40));
				}
			});
		}

		// ---- Spirit King: พิพากษาแปดธาตุ. A great circle in the sky, eight elements merge and fall like a
		// bomb: a blinding flash, a shockwave rolling out to the full radius (150 blocks by default),
		// a mushroom cloud, and burning, poisoned ground afterwards.
		void king() {
			int radius = SkillConfig.ultimateRadius;
			double circle = Math.min(radius * 0.85, 45.0);
			Vec3 sky = start.add(0, 40, 0);
			if (age == 0) {
				MinecraftServer server = level.getServer();
				Component text = SoulManager.msg("king_casting", player.getName(),
						(int) start.x, (int) start.y, (int) start.z).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
				server.getPlayerList().broadcastSystemMessage(text, false);
				everyone(SoundEvents.WITHER_SPAWN, 0.5f, 0.8f, -1);
			}
			if (age < castTicks) {
				if (player.getHealth() < startHealth - 12.0f) {
					say(player, "king_interrupted");
					cancel();
					sync(player);
					done = true;
					return;
				}
				Effects.keep(player, "slowness", 3, 5);
				Effects.keep(player, "slow_falling", 0, 5);
				double progress = (double) age / castTicks;
				double ringRadius = circle * Math.min(1.0, 0.4 + progress * 1.5);
				// The circle in the sky: three rings and an eight-point star, turning slowly.
				double phase = age * 0.01;
				if (age % 2 == 0) {
					farRing(level, KING_GOLD, sky, ringRadius, 20, phase + level.random.nextDouble(), 0.3);
					farRing(level, KING_GOLD, sky, ringRadius * 0.88, 14, -phase + level.random.nextDouble(), 0.3);
					farRing(level, KING_GOLD, sky, ringRadius * 0.45, 8, phase * 2 + level.random.nextDouble(), 0.2);
				}
				if (age % 10 == 0) {
					for (int i = 0; i < 8; i++) {
						double a = phase + i * Math.PI / 4;
						double b = a + Math.PI * 3 / 4;
						Vec3 from = sky.add(Math.cos(a) * ringRadius * 0.88, 0, Math.sin(a) * ringRadius * 0.88);
						Vec3 to = sky.add(Math.cos(b) * ringRadius * 0.88, 0, Math.sin(b) * ringRadius * 0.88);
						for (int k = 0; k <= 10; k++) {
							far(KING_GOLD, from.add(to.subtract(from).scale(k / 10.0)), 1, 0, 0, 0, 0);
						}
					}
					// The shadow on the ground shows how far the blast will reach.
					farRing(level, KING_SHADOW, start.add(0, 0.5, 0), radius, Math.min(160, radius), phase, 0.0);
				}
				// Beams from the circle down to the caster, one per element.
				if (age % 5 == 0) {
					Spirit[] spirits = Spirit.values();
					Spirit beam = spirits[(age / 5) % spirits.length];
					double angle = age * 0.05 + (age / 5) * Math.PI / 4;
					Vec3 top = sky.add(Math.cos(angle) * ringRadius, 0, Math.sin(angle) * ringRadius);
					line(level, new DustParticleOptions(beam.color, 2.0f), top, start.add(0, 2, 0), 2.0);
				}
				// The eight elements orbit, faster and faster, and spiral in during the last part.
				double spin = age * (0.02 + 0.12 * progress * progress);
				double orbit = progress < 0.6 ? ringRadius * 1.05 : ringRadius * 1.05 * Mth.clamp((1.0 - progress) / 0.4, 0, 1);
				Spirit[] spirits = Spirit.values();
				for (int i = 0; i < spirits.length; i++) {
					double angle = spin + i * Math.PI / 4;
					Vec3 at = sky.add(Math.cos(angle) * orbit, 0, Math.sin(angle) * orbit);
					far(spiritParticle(spirits[i]), at, 2, 0.6, 0.6, 0.6, 0.01);
					far(new DustParticleOptions(spirits[i].color, 4.0f), at, 1, 0.8, 0.8, 0.8, 0);
				}
				// The last two seconds: the core drinks in the world. Enemies near the centre are dragged in.
				if (castTicks - age <= 40) {
					double pull = radius * 0.3;
					for (LivingEntity enemy : enemiesNear(player, start, pull)) {
						knock(enemy, start, -0.12, 0.0);
						Effects.give(enemy, "slowness", 1, 10);
					}
					if (age % 2 == 0) {
						for (int i = 0; i < 12; i++) {
							double angle = level.random.nextDouble() * Math.PI * 2;
							double r = pull * (0.5 + level.random.nextDouble() * 0.5);
							Vec3 from = start.add(Math.cos(angle) * r, 1.0 + level.random.nextDouble() * 6, Math.sin(angle) * r);
							Vec3 inward = start.add(0, 3, 0).subtract(from).normalize();
							far(ParticleTypes.END_ROD, from, 0, inward.x, inward.y, inward.z, 1.5);
						}
					}
				}
				if (age % 20 == 0) {
					sound(level, start, SoundEvents.BEACON_AMBIENT, 6.0f, 0.5f + (float) progress);
				}
				if (age == castTicks - 20) {
					everyone(SoundEvents.ENDER_DRAGON_GROWL, 1.0f, 0.5f, radius * 2);
				}
				return;
			}
			int step = age - castTicks;
			double front = Math.min(radius, step * (radius / 40.0));
			if (step == 0) {
				// Impact: the merged core falls as a pillar, and the sky goes white.
				for (double y = 0; y <= 40; y += 1.0) {
					far(KING_CORE, start.add(0, y, 0), 3, 1.0, 0.3, 1.0, 0);
					far(ParticleTypes.END_ROD, start.add(0, y, 0), 2, 0.6, 0.3, 0.6, 0.05);
				}
				far(ParticleTypes.EXPLOSION_EMITTER, start.add(0, 2, 0), 12, 6.0, 3.0, 6.0, 0);
				far(KING_CORE, start.add(0, 8, 0), 200, 10.0, 8.0, 10.0, 0);
				far(ParticleTypes.FIREWORK, start.add(0, 6, 0), 200, 6.0, 6.0, 6.0, 0.6);
				everyone(SoundEvents.GENERIC_EXPLODE, 1.0f, 0.4f, radius * 3);
				everyone(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0f, 0.5f, radius * 3);
				everyone(SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 0.5f, radius * 2);
				// The flash blinds everyone else who is looking on from inside the blast.
				for (ServerPlayer other : level.players()) {
					if (other != player && !other.isCreative() && !other.isSpectator() && other.position().distanceTo(start) <= radius * 1.2) {
						Effects.give(other, "blindness", 0, 40);
						Effects.give(other, "nausea", 0, 100);
					}
				}
			}
			// The shockwave front rolls outward over two seconds; three blasts strike as it passes.
			if (step <= 40 && step % 2 == 0) {
				Spirit[] spirits = Spirit.values();
				int points = (int) Math.min(220, 24 + front * 1.4);
				for (int i = 0; i < points; i++) {
					double angle = i * Math.PI * 2 / points + step * 0.02;
					Vec3 at = start.add(Math.cos(angle) * front, 1.5, Math.sin(angle) * front);
					far(new DustParticleOptions(spirits[i % spirits.length].color, 4.0f), at, 1, 0.6, 1.2, 0.6, 0);
					if (i % 4 == 0) {
						far(ParticleTypes.CLOUD, at.add(0, -1.0, 0), 1, 0.5, 0.3, 0.5, 0.08);
					}
					if (i % 16 == 0) {
						far(ParticleTypes.EXPLOSION, at, 1, 0.5, 0.5, 0.5, 0);
					}
				}
			}
			double[] waves = {0.3, 0.6, 1.0};
			for (int w = 0; w < 3; w++) {
				if (step == (int) Math.round(waves[w] * 40)) {
					wave(w, radius * waves[w], w == 0 ? 0 : radius * waves[w - 1], radius);
				}
			}
			// The mushroom cloud: a stem of smoke and fire rising to 60 blocks, then a wide cap.
			if (step < 140 && step % 2 == 0) {
				double rise = Math.min(1.0, step / 60.0);
				double top = 60.0 * rise;
				for (double y = 2; y < top; y += 4) {
					far(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, start.add(0, y, 0), 1, 2.5, 1.5, 2.5, 0.01);
					if (y < top * 0.5) {
						far(FIRE_CORE, start.add(0, y, 0), 1, 2.0, 1.5, 2.0, 0);
					}
				}
				double cap = 8.0 + 18.0 * rise;
				for (int band = 0; band < 3; band++) {
					double r = cap * (1.0 - band * 0.3);
					farRing(level, band == 0 ? ASH_DUST : FIRE_DUST, start.add(0, top + band * 3.0, 0), r, (int) (10 + r * 1.2), step * 0.05 + band, 2.0);
				}
				far(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, start.add(0, top + 4, 0), 6, cap * 0.5, 2.0, cap * 0.5, 0.02);
			}
			// Fallout: ash drifts down and the inner ground stays poisonous for ten seconds.
			if (step >= 40 && step < 240) {
				if (step % 4 == 0) {
					far(ParticleTypes.WHITE_ASH, start.add(0, 20, 0), 60, radius * 0.4, 10.0, radius * 0.4, 0.0);
					far(ParticleTypes.ASH, start.add(0, 10, 0), 60, radius * 0.3, 6.0, radius * 0.3, 0.0);
				}
				if (step % 20 == 0) {
					for (LivingEntity enemy : enemiesNear(player, start, radius * 0.5)) {
						Effects.give(enemy, "wither", 1, 40);
						Effects.give(enemy, "hunger", 1, 40);
					}
				}
				if (step == 60) {
					// Fires break out across the scorched ground.
					for (int i = 0; i < 80; i++) {
						double angle = level.random.nextDouble() * Math.PI * 2;
						double r = radius * Math.sqrt(level.random.nextDouble()) * 0.8;
						Vec3 spot = ground(level, start.add(Math.cos(angle) * r, 10, Math.sin(angle) * r));
						BlockChanges.placeBriefly(level, player, BlockPos.containing(spot), Blocks.FIRE.defaultBlockState(), 200 + level.random.nextInt(200));
					}
				}
			}
			if (step >= 240) {
				done = true;
			}
		}

		/** Plays a sound at every player in this dimension within range (any distance when range is negative). */
		void everyone(net.minecraft.sounds.SoundEvent sound, float volume, float pitch, double range) {
			for (ServerPlayer other : level.players()) {
				if (range < 0 || other.position().distanceTo(start) <= range) {
					level.playSound(null, other.getX(), other.getY(), other.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
				}
			}
		}

		void everyone(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound, float volume, float pitch, double range) {
			for (ServerPlayer other : level.players()) {
				if (range < 0 || other.position().distanceTo(start) <= range) {
					level.playSound(null, other.getX(), other.getY(), other.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
				}
			}
		}

		void wave(int index, double radius, double inner, double full) {
			// Eight hearts a wave. Half of it is magic, which no armor stops, so nobody survives three waves.
			for (LivingEntity enemy : enemiesNear(player, start, radius)) {
				hit(level, player, enemy, 8.0f);
				enemy.invulnerableTime = 0;
				enemy.hurtServer(level, level.damageSources().magic(), 8.0f);
				enemy.igniteForSeconds(4.0f);
				knock(enemy, start, 1.6, 0.8);
			}
			if (SkillConfig.ultimateBreaksBlocks) {
				BlockChanges.queue(new CraterJob(level, player, start, inner, radius, full,
						SkillConfig.ultimateRegrowSeconds * 20 + index * 40));
			}
		}
	}

	/**
	 * The ultimate's crater, one ring of ground at a time, nearest columns first so it spreads outward:
	 * a bowl up to 20 blocks deep in the middle, and everything up to 30 blocks above the centre carved away.
	 * Both scale with the radius, and the bowl is sharper than the cut above, so the far edge only loses
	 * what stands on the ground.
	 */
	private static final class CraterJob implements BlockChanges.Job {
		private final ServerLevel level;
		private final ServerPlayer owner;
		private final List<int[]> columns = new ArrayList<>();
		private final int baseY;
		private final int regrowTicks;
		private int column;
		private int y;
		private int bottom;

		CraterJob(ServerLevel level, ServerPlayer owner, Vec3 center, double inner, double outer, double full, int regrowTicks) {
			this.level = level;
			double maxDepth = Mth.clamp(full / 7.5, 8, 20);
			double maxHeight = Mth.clamp(full / 5.0, 12, 30);
			this.owner = owner;
			this.baseY = (int) Math.floor(center.y);
			this.regrowTicks = regrowTicks;
			int cx = (int) Math.floor(center.x);
			int cz = (int) Math.floor(center.z);
			int r = (int) Math.ceil(outer);
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > inner && d <= outer) {
						double shape = 1.0 - (d / full) * (d / full);
						int depth = (int) Math.round(maxDepth * shape * shape);
						int height = (int) Math.round(maxHeight * Math.sqrt(shape));
						columns.add(new int[] {cx + dx, cz + dz, baseY + height, baseY - depth, (int) (d * 100)});
					}
				}
			}
			columns.sort(Comparator.comparingInt(c -> c[4]));
			this.column = -1;
		}

		@Override
		public BlockPos next() {
			while (column < 0 || y < bottom) {
				column++;
				if (column >= columns.size()) {
					return null;
				}
				int[] c = columns.get(column);
				y = c[2];
				bottom = c[3];
			}
			int[] c = columns.get(column);
			return new BlockPos(c[0], y--, c[1]);
		}

		@Override
		public ServerLevel level() {
			return level;
		}

		@Override
		public ServerPlayer owner() {
			return owner;
		}

		@Override
		public boolean naturalOnly() {
			return false;
		}

		@Override
		public int regrowTicks() {
			return regrowTicks;
		}
	}
}
