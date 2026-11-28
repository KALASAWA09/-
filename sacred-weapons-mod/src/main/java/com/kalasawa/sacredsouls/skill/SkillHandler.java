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
import net.minecraft.world.level.levelgen.Heightmap;
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
import net.minecraft.world.damagesource.DamageTypes;
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
 * Damage is owned by the caster so kills are credited; see hit() for how armor treats it.
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
		if (other == self || !(other instanceof LivingEntity living) || !living.isAlive()) {
			return false;
		}
		// Teammates are spared only when their team turns friendly fire off. Many servers put every player
		// in one scoreboard team for name tags, and a plain ally check would make skills miss everyone.
		net.minecraft.world.scores.PlayerTeam team = self.getTeam();
		if (team != null && other.getTeam() == team && !team.isAllowFriendlyFire()) {
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

	/**
	 * A skill hit. Against players most of it is a soul strike that goes through armor and enchantments,
	 * like the Warden's sonic boom, so full netherite does not shrug it off; the rest is explosion damage.
	 * The caster is credited for kills but is not the direct attacker, so melee-only powers do not trigger.
	 * Each hit is written to the server log so a hit that does nothing can be traced.
	 */
	private static void hit(ServerLevel level, ServerPlayer player, LivingEntity target, float amount) {
		float total = amount * (float) SkillConfig.damageMultiplier;
		float trueShare = target instanceof Player ? 0.7f : 0.4f;
		float before = target.getHealth() + target.getAbsorptionAmount();
		target.invulnerableTime = 0;
		boolean first = target.hurtServer(level, level.damageSources().explosion(null, player), total * (1 - trueShare));
		boolean second = false;
		if (target.isAlive()) {
			target.invulnerableTime = 0;
			second = target.hurtServer(level, level.damageSources().source(DamageTypes.SONIC_BOOM, null, player), total * trueShare);
		}
		float after = target.isAlive() ? target.getHealth() + target.getAbsorptionAmount() : 0;
		com.kalasawa.sacredsouls.SacredSouls.LOGGER.info("[skill] {} hit {} for {} (applied: {}/{}, health {} -> {})",
				player.getName().getString(), target.getName().getString(), total, first, second, before, after);
		// Make the hit felt: a heavy thud and a burst at the victim.
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.2f, 0.7f);
		level.sendParticles(ParticleTypes.CRIT, true, true, target.getX(), target.getY() + 1.0, target.getZ(), 12, 0.4, 0.5, 0.4, 0.3);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, true, true, target.getX(), target.getY() + 1.2, target.getZ(), 4, 0.3, 0.3, 0.3, 0.1);
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

	/** Makes every skill ready at once, the ultimate included. For admins testing skills. */
	public static void resetCooldowns(ServerPlayer player) {
		READY.remove(player.getUUID());
		SHARED_READY.remove(player.getUUID());
		player.setAttached(ModAttachments.ULTIMATE_READY, 0L);
		sync(player);
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
				cast.fx.killAll();
				it.remove();
			}
		}
		BlockChanges.tick(server);
		// Sweep up show entities left by a crash or an unloaded cast, whenever no ultimate is running.
		if (server.getTickCount() % 400 == 0 && CASTS.stream().noneMatch(cast -> cast.skill == Skill.KING)) {
			KingShow.cleanupAll(server);
		}
		if (server.getTickCount() % 400 == 200 && CASTS.isEmpty()) {
			FxStage.cleanupAll(server);
		}
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
		/** The staging of the Spirit King ultimate: circles, storm, boss bar, cracks, sounds. */
		KingShow show;
		/** The 3D models and magic circles of this cast. */
		final FxStage fx;
		/** Where this cast's ground circle was placed. */
		Vec3 circleAt;
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
			this.fx = new FxStage(level);
		}

		void cancel() {
			if (skill == Skill.KING && age < castTicks) {
				setReady(player, Skill.KING, level.getGameTime() + Skill.KING.cooldownTicks / 2);
			}
			if (show != null) {
				show.stop();
				show = null;
			}
			BLOOD.remove(player.getUUID());
			fx.killAll();
		}

		void tick() {
			fx.tick();
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
			// The four smaller meteors fall on their own clock, landing one after another after the great one.
			if (age % 3 == 0) {
				for (int i = 0; i < extra.size(); i++) {
					if (age > castTicks + 4 + i * 5) {
						continue;
					}
					int next = age + 3;
					fx.move("small" + i, FxStage.axis(1, 0, -1, next * 0.4 + i), smallAt(i, next).subtract(extra.get(i)), 2.0, 3);
					if (age > 0) {
						far(ParticleTypes.FLAME, smallAt(i, age), 2, 0.4, 0.4, 0.4, 0.01);
					}
				}
			}
			if (age == 0) {
				sound(level, start, SoundEvents.BLAZE_SHOOT, 2.0f, 0.5f);
				sound(level, target, SoundEvents.ENDER_DRAGON_GROWL, 1.5f, 1.6f);
				for (int i = 0; i < 4; i++) {
					double angle = i * Math.PI / 2 + level.random.nextDouble();
					double r = 4.0 + level.random.nextDouble() * 3.0;
					extra.add(ground(level, target.add(Math.cos(angle) * r, 2.0, Math.sin(angle) * r)));
				}
				circle("fire_circle", target, 15, 8);
				fx.model("meteor", "fire_meteor", target, FxStage.yaw(0), meteorAt(0).subtract(target), 0.5);
				for (int i = 0; i < extra.size(); i++) {
					fx.model("small" + i, "fire_meteor", extra.get(i), FxStage.yaw(i), smallAt(i, 0).subtract(extra.get(i)), 0.3);
				}
			}
			if (age < castTicks) {
				// The circle of fire turns under the meteor and follows its target.
				if (age > 8 && age % 4 == 0) {
					turnCircle(target, 15, age * 0.06 + 0.6, 4);
				}
				// The meteor: a burning rock falling from high above, turning as it comes, trailing fire and smoke.
				if (age % 3 == 0) {
					int next = age + 3;
					fx.move("meteor", FxStage.axis(1, 0.3, 1, next * 0.3), meteorAt(next).subtract(target), 5.0, 3);
				}
				Vec3 ball = meteorAt(age);
				far(ParticleTypes.FLAME, ball, 6, 1.4, 1.4, 1.4, 0.02);
				far(ParticleTypes.LAVA, ball, 1, 0.8, 0.8, 0.8, 0);
				far(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, ball.add(direction.scale(-2.5)).add(0, 2.5, 0), 2, 0.6, 0.6, 0.6, 0.01);
				if (age == castTicks - 6) {
					sound(level, target, SoundEvents.FIRECHARGE_USE, 2.0f, 0.5f);
				}
				return;
			}
			int step = age - castTicks;
			if (step == 0) {
				fx.kill("meteor");
				endCircle(18, 20);
				shockwave("wave", "fire_wave", target, 9.0, 8);
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
					fx.kill("small" + i);
					shockwave("swave" + i, "fire_wave", at, 3.5, 5);
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
				circle("light_circle", target, 19, 10);
				// A second circle hangs in the air, and a sword of light forms above it.
				fx.model("sky", "light_circle", target.add(0, 16, 0), FxStage.flat(0), Vec3.ZERO, 0.1);
				fx.move("sky", FxStage.flat(-0.6), Vec3.ZERO, 13, 10);
				fx.model("sword", "light_sword", target, FxStage.yaw(0), new Vec3(0, 46, 0), 0.2);
				fx.move("sword", FxStage.yaw(1.2), new Vec3(0, 34, 0), 4.5, castTicks - 4);
			}
			if (age < castTicks) {
				double grow = Math.min(1.0, 0.25 + 1.2 * age / castTicks);
				if (age > 10 && age % 5 == 0) {
					Vec3 shift = target.add(0, 0.12, 0).subtract(circleAt);
					turnCircle(target, 19, age * 0.05 + 0.6, 5);
					fx.move("sky", FxStage.flat(-age * 0.07 - 0.6), shift, 13, 5);
				}
				if (age == castTicks - 4) {
					fx.move("sword", FxStage.yaw(1.6), target.subtract(circleAt).add(0, 34.12, 0), 4.5, 4);
				}
				if (age % 2 == 0) {
					farRing(level, ParticleTypes.END_ROD, target.add(0, 0.3, 0), 4.0 * grow, 12, age * 0.1, 0);
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
				// The sword falls and buries itself in the marked ground; a beam of light comes down with it.
				Vec3 shift = target.subtract(circleAt).add(0, 0.12, 0);
				fx.move("sword", FxStage.yaw(1.6), shift.add(0, 4.5 * 1.5 - 1.5, 0), 4.5, 2);
				fx.model("beam", "king_pillar", target, FxStage.yaw(0), new Vec3(0, 40, 0), 3.5, 40 / 1.5, 3.5);
				fx.move("beam", FxStage.yaw(0.8), new Vec3(0, 40, 0), 0.05, 40 / 1.5, 0.05, 26);
				fx.killLater("beam", 28);
				fx.move("sky", FxStage.flat(-3.0), shift, 0.01, 8);
				fx.killLater("sky", 9);
				endCircle(22, 28);
				fx.later(26, () -> fx.move("sword", FxStage.yaw(1.6), shift.add(0, -3, 0), 0.01, 6));
				fx.killLater("sword", 33);
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
			if (step < 30 && step % 2 == 0) {
				double width = step < 4 ? 2.5 : 2.5 * (1.0 - (step - 4) / 30.0);
				for (double y = 0; y <= 40; y += 4.0) {
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
				// A smaller sword drops out of the sky and strikes.
				String lance = "lance" + i;
				fx.model(lance, "light_sword", at, FxStage.yaw(angle), new Vec3(0, 14, 0), 1.6);
				fx.move(lance, FxStage.yaw(angle), new Vec3(0, 1.6 * 1.5 - 0.6, 0), 1.6, 2);
				fx.later(14, () -> fx.move(lance, FxStage.yaw(angle), new Vec3(0, -1, 0), 0.01, 5));
				fx.killLater(lance, 20);
				for (double y = 0; y <= 20; y += 3.0) {
					far(ParticleTypes.END_ROD, at.add(0, y, 0), 1, 0.15, 0.4, 0.15, 0.0);
				}
				far(ParticleTypes.FIREWORK, at.add(0, 0.5, 0), 25, 1.0, 0.3, 1.0, 0.15);
				sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 0.6f);
				for (LivingEntity enemy : enemiesNear(player, at, 2.5)) {
					hit(level, player, enemy, 2.0f);
				}
			}
			if (step >= 44) {
				done = true;
			}
		}

		/** Where the great meteor is at a tick of the fire cast: it falls from 40 blocks up and 14 back. */
		Vec3 meteorAt(int tick) {
			double fall = 1 - Math.min(1.0, (double) tick / castTicks);
			return target.add(direction.scale(-14.0 * fall)).add(0, 40.0 * fall + 1.5, 0);
		}

		/** Where small meteor i is: each lands 4 + 5i ticks after the great one. */
		Vec3 smallAt(int i, int tick) {
			double fall = 1 - Math.min(1.0, tick / (double) (castTicks + 4 + i * 5));
			return extra.get(i).add(direction.scale(-9.0 * fall)).add(0, 28.0 * fall + 0.6, 0);
		}

		// ---- Water: มังกรวารี. A dragon of water rises out of a circle, coils once around the caster, then dives
		// forward through everything in its path and crashes into the ground in a great splash.
		static final int DRAGON_LENGTH = 12;
		final List<Vec3> trail = new ArrayList<>();

		void water() {
			if (age == 0) {
				sound(level, start, SoundEvents.BUCKET_EMPTY, 2.0f, 0.5f);
				sound(level, start, SoundEvents.ELDER_GUARDIAN_CURSE, 1.0f, 1.4f);
				circle("water_circle", start, 9, 8);
				Vec3 rise = dragonAt(0).subtract(start).add(0, -2, 0);
				fx.model("head", "water_dragon_head", start, FxStage.yaw(0), rise, 0.1);
				for (int i = 0; i < DRAGON_LENGTH; i++) {
					fx.model("body" + i, segment(i), start, FxStage.yaw(0), rise, 0.1);
				}
			}
			trail.add(dragonAt(age));
			if (age > 0 && age % 2 == 0) {
				moveDragon();
			}
			Vec3 head = trail.get(trail.size() - 1);
			if (age < castTicks + 14) {
				far(ParticleTypes.SPLASH, head, 8, 0.8, 0.8, 0.8, 0.15);
				if (age % 3 == 0) {
					far(ParticleTypes.CLOUD, head, 1, 0.4, 0.4, 0.4, 0.01);
					// Water streams off its body as it flies.
					for (int i = 2; i < DRAGON_LENGTH; i += 3) {
						far(ParticleTypes.FALLING_WATER, back(1.7 + i * 1.3), 3, 0.5, 0.3, 0.5, 0.0);
					}
				}
			}
			if (age < castTicks) {
				far(ParticleTypes.SPLASH, start.add(0, 0.5, 0), 8, 2.0, 0.3, 2.0, 0.1);
				if (age == castTicks / 2) {
					sound(level, start, SoundEvents.ENDER_DRAGON_GROWL, 1.0f, 1.8f);
				}
				return;
			}
			int step = age - castTicks;
			if (step == 0) {
				endCircle(11, 6);
				sound(level, start, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 3.0f, 0.4f);
				sound(level, start, SoundEvents.ENDER_DRAGON_GROWL, 1.5f, 1.4f);
			}
			// The dragon dives along the line, 1.2 blocks a tick, striking whatever it passes once.
			if (step <= 13) {
				double front = Math.min(16.0, step * 1.2 + 1.0);
				if (step % 4 == 0) {
					sound(level, start.add(direction.scale(front)), SoundEvents.GENERIC_SPLASH, 1.5f, 0.6f + step * 0.03f);
				}
				boolean wet = level.isRainingAt(player.blockPosition().above()) || player.isInWater();
				for (LivingEntity enemy : enemiesNear(player, start, front + 1.0)) {
					if (marked.contains(enemy)) {
						continue;
					}
					Vec3 to = enemy.position().subtract(start);
					double along = to.dot(direction);
					double across = Math.abs(to.x * -direction.z + to.z * direction.x);
					if (along < front - 1.8 || along > front + 1.0 || across > 3.5) {
						continue;
					}
					marked.add(enemy);
					hit(level, player, enemy, wet ? 8.0f : 6.0f);
					enemy.clearFire();
					Effects.give(enemy, "slowness", 1, 60);
					enemy.setDeltaMovement(direction.x * 1.8, 0.6, direction.z * 1.8);
					enemy.hurtMarked = true;
					wallCheck.add(enemy);
				}
				for (double side = -2.0; side <= 2.0; side += 1.0) {
					Vec3 at = start.add(direction.scale(front)).add(-direction.z * side, 0, direction.x * side);
					for (int dy = 0; dy <= 3; dy++) {
						BlockPos pos = BlockPos.containing(at).above(dy);
						if (BlockChanges.isSoft(level.getBlockState(pos))) {
							BlockChanges.breakBlock(level, player, pos, true, regrow());
						}
					}
				}
			}
			if (step == 13) {
				// It crashes into the ground in a great splash.
				Vec3 end = ground(level, start.add(direction.scale(16.0)).add(0, 2, 0));
				shockwave("ring", "water_ring", end, 6.0, 8);
				far(ParticleTypes.SPLASH, end.add(0, 2, 0), 160, 3.0, 1.5, 3.0, 0.4);
				far(ParticleTypes.CLOUD, end.add(0, 2, 0), 25, 2.5, 1.0, 2.5, 0.05);
				sound(level, end, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 3.0f, 0.5f);
				sound(level, end, SoundEvents.GENERIC_EXPLODE, 1.5f, 1.2f);
				for (LivingEntity enemy : enemiesNear(player, end, 5.0)) {
					hit(level, player, enemy, 3.0f);
					knock(enemy, end, 1.0, 0.6);
				}
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
			if (step >= 13 + DRAGON_LENGTH + 4) {
				done = true;
			}
		}

		/** Where the dragon's head is at a tick: coiling up around the caster, then diving forward and into the ground. */
		Vec3 dragonAt(int tick) {
			if (tick <= castTicks) {
				double u = (double) tick / castTicks;
				double end = Math.atan2(-direction.z, -direction.x);
				double a = end - Math.PI * 2.5 * (1 - u);
				double r = 1.5 + 1.5 * u;
				return start.add(Math.cos(a) * r, 0.5 + 6.0 * u, Math.sin(a) * r);
			}
			int step = tick - castTicks;
			if (step <= 13) {
				double k = step / 13.0;
				double front = Math.min(16.0, step * 1.2 + 1.0);
				Vec3 line = start.add(direction.scale(front)).add(0, 6.5 * (1 - k) * (1 - k) + 1.0 + 2.0 * Math.sin(k * Math.PI), 0);
				// Swing out of the coil smoothly over the first few ticks of the dive.
				double blend = Math.min(1.0, (step + 1) / 4.0);
				return dragonAt(castTicks).scale(1 - blend).add(line.scale(blend));
			}
			int under = step - 13;
			return start.add(direction.scale(16.0 + under * 0.8)).add(0, 1.0 - under * 1.4, 0);
		}

		/** The head goes where the path says; the body is laid along the path behind it, so it never breaks apart however fast it flies. */
		void moveDragon() {
			fx.move("head", headingAt(0), back(0).subtract(start), 3.0, 2);
			double dist = 2.0;
			for (int i = 0; i < DRAGON_LENGTH; i++) {
				double size = 2.3 - 1.3 * i / (DRAGON_LENGTH - 1.0);
				fx.move("body" + i, headingAt(dist), back(dist).subtract(start), size, 2);
				dist += size * 0.75;
			}
		}

		/** The point a distance back along the path from the head. */
		Vec3 back(double dist) {
			int i = trail.size() - 1;
			Vec3 at = trail.get(i);
			double left = Math.max(0, dist);
			while (i > 0) {
				Vec3 prev = trail.get(i - 1);
				double len = at.distanceTo(prev);
				if (len >= left && len > 1.0E-6) {
					return at.add(prev.subtract(at).scale(left / len));
				}
				left -= len;
				at = prev;
				i--;
			}
			return at;
		}

		/** The rotation that points a model's +x along the path at a distance back from the head. */
		float[] headingAt(double dist) {
			Vec3 d = back(dist - 0.5).subtract(back(dist + 0.5));
			if (d.lengthSqr() < 1.0E-4) {
				d = direction;
			}
			double flat = Math.sqrt(d.x * d.x + d.z * d.z);
			return FxStage.mul(FxStage.yaw(-Math.atan2(d.z, d.x)), FxStage.axis(0, 0, 1, Math.atan2(d.y, flat)));
		}

		/** The dragon's body: clawed legs at the front and back, a fanned tail at the end. */
		static String segment(int i) {
			if (i == DRAGON_LENGTH - 1) {
				return "water_dragon_tail";
			}
			return i == 1 || i == 7 ? "water_dragon_legs" : "water_dragon_body";
		}

		// ---- Death: ดอกไม้แห่งยมโลก. Spider lilies of the underworld bloom across a graveyard that rises around the caster.
		// A lily blooms under every enemy and drinks its soul, and the great lily behind the caster closes over them all.
		static final double LILY = 4.0;
		static final double BLOOM = 1.8;
		static final double GRAVE = 1.8;
		static final double YARD = 9.5;

		void death() {
			Vec3 center = player.position();
			Vec3 lilyAt = ground(level, start.add(direction.scale(-3.5)).add(0, 2, 0));
			Vec3 flower = lilyAt.add(0, LILY * 21 / 16.0, 0);
			if (age == 0) {
				sound(level, start, SoundEvents.SOUL_ESCAPE, 3.0f, 0.5f);
				sound(level, start, SoundEvents.WITHER_AMBIENT, 1.5f, 0.6f);
				surface(level, start, 8.0, (pos, state) -> {
					if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
							|| state.is(Blocks.SAND) || state.is(Blocks.PODZOL)) {
						BlockChanges.replace(level, player, pos, Blocks.SOUL_SOIL.defaultBlockState(), true, regrow());
					}
				});
				circle("death_circle", start, 19, 12);
				// The great lily grows out of the ground behind the caster, two flowers turned against each other.
				for (int k = 0; k < 2; k++) {
					float[] q = FxStage.yaw(k * Math.PI / 4);
					fx.model("lily" + k, "death_lily", lilyAt, q, new Vec3(0, 0.15, 0), 0.1);
					fx.move("lily" + k, q, new Vec3(0, 1.5 * LILY, 0), LILY, castTicks);
				}
				// Gravestones rise in a ring, each facing the caster, with smaller lilies between them.
				for (int i = 0; i < 8; i++) {
					double a = i * Math.PI / 4 + Math.PI / 8;
					Vec3 at = ground(level, start.add(Math.cos(a) * YARD, 2, Math.sin(a) * YARD));
					float[] q = FxStage.yaw(Math.atan2(-Math.cos(a), -Math.sin(a)));
					String name = "grave" + i;
					fx.model(name, "death_grave", at, q, new Vec3(0, 0.5 * GRAVE - 2.6, 0), GRAVE);
					fx.later(2 + i, () -> fx.move(name, q, new Vec3(0, 0.5 * GRAVE, 0), GRAVE, 8));
					double b = i * Math.PI / 4;
					Vec3 spot = ground(level, start.add(Math.cos(b) * 6.5, 2, Math.sin(b) * 6.5));
					float[] turn = FxStage.yaw(b * 3);
					String flowerName = "flower" + i;
					fx.model(flowerName, "death_lily", spot, turn, new Vec3(0, 0.15, 0), 0.1);
					fx.later(4 + i, () -> fx.move(flowerName, turn, new Vec3(0, 1.5 * 1.3, 0), 1.3, 8));
				}
			}
			if (age < castTicks) {
				if (age % 4 == 0) {
					farRing(level, ParticleTypes.SOUL_FIRE_FLAME, center.add(0, 0.2, 0), 9.0, 24, age * 0.06, 0.1);
					if (age > 12) {
						turnCircle(null, 19, age * 0.06 + 0.6, 4);
					}
				}
				far(ParticleTypes.SOUL, center.add(0, 0.5, 0), 6, 6.0, 0.2, 6.0, 0.05);
				far(ParticleTypes.CRIMSON_SPORE, flower, 6, 2.0, 1.0, 2.0, 0.0);
				return;
			}
			int step = age - castTicks;
			if (step == 0) {
				// A lily bursts open under every enemy in reach and holds it there.
				sound(level, center, SoundEvents.WITHER_SHOOT, 2.0f, 0.5f);
				sound(level, flower, SoundEvents.WARDEN_SONIC_CHARGE, 2.0f, 0.6f);
				float dealt = 0;
				int blooms = 0;
				for (LivingEntity enemy : enemiesNear(player, center, 9.0)) {
					float before = enemy.getHealth();
					hit(level, player, enemy, 7.0f);
					Effects.give(enemy, "wither", 1, 80);
					Effects.give(enemy, "slowness", 6, 30);
					dealt += Math.max(0, before - enemy.getHealth());
					marked.add(enemy);
					enemy.setDeltaMovement(0, Math.min(0, enemy.getDeltaMovement().y), 0);
					enemy.hurtMarked = true;
					if (blooms < 8) {
						String name = "bloom" + blooms++;
						float[] q = FxStage.yaw(level.random.nextDouble() * Math.PI * 2);
						fx.model(name, "death_lily", enemy.position(), q, new Vec3(0, 0.15, 0), 0.1);
						fx.move(name, q, new Vec3(0, 1.5 * BLOOM, 0), BLOOM, 3);
						far(ParticleTypes.CRIMSON_SPORE, enemy.position().add(0, 0.5, 0), 20, 0.8, 0.4, 0.8, 0.02);
						far(ParticleTypes.SOUL, enemy.position().add(0, 0.5, 0), 12, 0.6, 0.3, 0.6, 0.05);
					}
				}
				player.heal(Math.min(dealt * 0.5f, 6.0f));
			}
			// The souls of everything struck stream up into the great lily.
			if (step > 0 && step < 18 && step % 2 == 0) {
				for (LivingEntity enemy : marked) {
					line(level, ParticleTypes.SOUL, enemy.position().add(0, 1.2, 0), flower, 0.8);
				}
				far(ParticleTypes.SCULK_SOUL, flower, 4, 0.8, 0.5, 0.8, 0.02);
			}
			if (step == 18) {
				// The great lily closes over the souls it drank.
				for (int k = 0; k < 2; k++) {
					float[] q = FxStage.yaw(k * Math.PI / 4 + 0.6);
					fx.move("lily" + k, q, new Vec3(0, 1.5 * LILY * 1.15, 0), LILY * 0.45, LILY * 1.15, LILY * 0.45, 4);
				}
				sound(level, flower, SoundEvents.EVOKER_FANGS_ATTACK, 2.0f, 0.5f);
			}
			if (step == 22) {
				far(ParticleTypes.SOUL, flower, 60, 1.5, 1.5, 1.5, 0.15);
				far(ParticleTypes.CRIMSON_SPORE, center.add(0, 1, 0), 80, 8.0, 1.0, 8.0, 0.02);
				sound(level, flower, SoundEvents.SOUL_ESCAPE, 3.0f, 0.4f);
				for (int k = 0; k < 2; k++) {
					fx.move("lily" + k, FxStage.yaw(k * Math.PI / 4 + 1.2), new Vec3(0, 0.15, 0), 0.01, 8);
				}
				for (int i = 0; i < 8; i++) {
					double a = i * Math.PI / 4 + Math.PI / 8;
					float[] q = FxStage.yaw(Math.atan2(-Math.cos(a), -Math.sin(a)));
					fx.move("grave" + i, q, new Vec3(0, 0.5 * GRAVE - 2.6, 0), GRAVE, 8);
					fx.move("flower" + i, FxStage.yaw(i * Math.PI / 4 * 3), new Vec3(0, 0.15, 0), 0.01, 6);
					fx.move("bloom" + i, FxStage.yaw(0), new Vec3(0, 0.15, 0), 0.01, 6);
				}
				endCircle(21, 8);
			}
			if (step >= 32) {
				done = true;
			}
		}

		// ---- Darkness: หลุมดำ. A black hole opens over the battlefield and drags everything toward it for five
		// seconds, then collapses into a point and bursts.
		static final double HOLE = 5.0;
		static final double HALO = 7.5;
		static final double DISK = 20.0;

		void darkness() {
			Vec3 core = start.add(0, 5.0, 0);
			if (age == 0) {
				sound(level, start, SoundEvents.SCULK_SHRIEKER_SHRIEK, 3.0f, 0.5f);
				sound(level, start, SoundEvents.WARDEN_EMERGE, 1.5f, 1.0f);
				circle("dark_circle", start, 21, castTicks);
				fx.model("hole", "dark_hole", core, FxStage.yaw(0), Vec3.ZERO, 0.1);
				fx.move("hole", FxStage.axis(1, 1, 0, 1.0), Vec3.ZERO, HOLE, castTicks);
				fx.model("halo", "dark_halo", core, FxStage.yaw(0), Vec3.ZERO, 0.1, 0.1, 0.1, true);
				fx.move("halo", FxStage.yaw(0), Vec3.ZERO, HALO, castTicks);
				fx.model("disk", "dark_disk", core, disk(0), Vec3.ZERO, 0.1);
				fx.move("disk", disk(1.2), Vec3.ZERO, DISK, castTicks);
				fx.model("disk2", "dark_disk", core, disk2(0), Vec3.ZERO, 0.1);
				fx.move("disk2", disk2(-1.0), Vec3.ZERO, DISK * 0.7, castTicks);
			}
			if (age < castTicks) {
				far(ParticleTypes.REVERSE_PORTAL, core, 10, 4.0, 2.0, 4.0, 0.05);
				return;
			}
			int step = age - castTicks;
			if (step < 100) {
				// The disk whirls and the hole throbs.
				if (step % 4 == 0) {
					fx.move("disk", disk(1.2 + (step + 4) * 0.35), Vec3.ZERO, DISK, 4);
					fx.move("disk2", disk2(-1.0 - (step + 4) * 0.25), Vec3.ZERO, DISK * 0.7, 4);
					fx.move("hole", FxStage.axis(1, 1, 0, 1.0 + (step + 4) * 0.1), Vec3.ZERO, HOLE * (1 + 0.05 * Math.sin(step * 0.3)), 4);
				}
				if (step % 10 == 0) {
					turnCircle(null, 21, step * 0.04 + 0.6, 10);
				}
				// Darkness spirals in from all around.
				if (step % 2 == 0) {
					for (int i = 0; i < 3; i++) {
						double a = level.random.nextDouble() * Math.PI * 2;
						double r = 6 + level.random.nextDouble() * 5;
						Vec3 from = core.add(Math.cos(a) * r, (level.random.nextDouble() - 0.5) * 3, Math.sin(a) * r);
						Vec3 in = core.subtract(from).normalize();
						Vec3 swirl = in.add(new Vec3(-in.z, 0, in.x).scale(1.2)).normalize();
						far(ParticleTypes.SQUID_INK, from, 0, swirl.x, swirl.y, swirl.z, 0.7);
					}
				}
				// Everything near is dragged toward the hole, harder the closer it gets.
				for (LivingEntity enemy : enemiesNear(player, start, 12.0)) {
					Vec3 pull = core.subtract(enemy.position());
					double d = pull.length();
					if (d > 1.5) {
						Vec3 v = pull.normalize().scale(Math.min(0.12, 0.6 / d));
						enemy.setDeltaMovement(enemy.getDeltaMovement().add(v.x, v.y * 0.5, v.z));
						enemy.hurtMarked = true;
					}
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
				// Collapse: the disk is swallowed and the hole shrinks to a point.
				fx.move("disk", disk(40), Vec3.ZERO, 0.01, 6);
				fx.move("disk2", disk2(-30), Vec3.ZERO, 0.01, 6);
				fx.move("halo", FxStage.yaw(0), Vec3.ZERO, 0.01, 6);
				fx.move("hole", FxStage.axis(1, 1, 0, 14), Vec3.ZERO, 0.3, 6);
				for (int i = 0; i < 40; i++) {
					double angle = i * Math.PI * 2 / 40;
					Vec3 at = core.add(Math.cos(angle) * 10.0, 0, Math.sin(angle) * 10.0);
					far(ParticleTypes.SQUID_INK, at, 0, -Math.cos(angle), 0, -Math.sin(angle), 1.2);
				}
				sound(level, start, SoundEvents.WARDEN_SONIC_BOOM, 3.0f, 0.5f);
			}
			if (step == 106) {
				fx.kill("hole");
				fx.kill("halo");
				fx.kill("disk");
				fx.kill("disk2");
				fx.kill("circle");
				shockwave("wave", "dark_wave", start, 11.0, 7);
				for (int i = 0; i < 8; i++) {
					double angle = i * Math.PI / 4;
					for (double r = 1.5; r <= 10.0; r += 1.5) {
						far(ParticleTypes.SONIC_BOOM, start.add(Math.cos(angle) * r, 1.0, Math.sin(angle) * r), 1, 0, 0, 0, 0);
					}
				}
				far(ParticleTypes.EXPLOSION_EMITTER, core, 1, 0, 0, 0, 0);
				far(ParticleTypes.LARGE_SMOKE, core, 40, 2.0, 1.0, 2.0, 0.15);
				sound(level, start, SoundEvents.GENERIC_EXPLODE, 3.0f, 0.5f);
				for (LivingEntity enemy : enemiesNear(player, start, 10.0)) {
					hit(level, player, enemy, 5.0f);
					knock(enemy, start, 1.2, 0.6);
				}
				crater(level, player, start.add(direction.scale(3.0)), 3.0);
			}
			if (step >= 115) {
				done = true;
			}
		}

		float[] disk(double spin) {
			return FxStage.mul(FxStage.axis(1, 0, 0.4, 0.35), FxStage.flat(spin));
		}

		float[] disk2(double spin) {
			return FxStage.mul(FxStage.axis(-0.3, 0, 1, 0.5), FxStage.flat(spin));
		}

		// ---- Lightning: พิโรธอัสนี. A storm gathers over the caster, they dash through the line, then lightning leaps
		// from enemy to enemy, each strike a spear from the cloud, and a last great bolt lands on the final one.
		int finale = -1;
		Vec3 lastSpot;

		void lightning() {
			Vec3 at = player.position();
			if (age == 0) {
				sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.5f, 1.4f);
				sound(level, at, SoundEvents.TRIDENT_THUNDER, 1.5f, 1.2f);
				sound(level, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0f, 0.5f);
				circle("thunder_circle", at, 8, castTicks);
				fx.model("cloud", "thunder_cloud", start.add(0, 14, 0), FxStage.flat(0), Vec3.ZERO, 0.5);
				fx.move("cloud", FxStage.flat(0.8), Vec3.ZERO, 30, castTicks);
			}
			if (age < castTicks) {
				// Charge: sparks spin up the caster's body, and the cloud strikes them once.
				for (int i = 0; i < 3; i++) {
					double angle = age * 0.9 + i * Math.PI * 2 / 3;
					far(ParticleTypes.ELECTRIC_SPARK, at.add(Math.cos(angle) * 1.2, age * 0.15, Math.sin(angle) * 1.2), 3, 0.1, 0.1, 0.1, 0.05);
				}
				far(SPARK_DUST, at.add(0, 1, 0), 2, 0.8, 1.0, 0.8, 0);
				if (age == castTicks - 2) {
					arc("charge", start.add(0, 14, 0), at.add(0, 1, 0), 6);
				}
				return;
			}
			int step = age - castTicks;
			if (step == 0) {
				endCircle(10, 6);
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
			if (step % 6 == 0 && (finale < 0 || step < finale)) {
				fx.move("cloud", FxStage.flat(0.8 + (step + 6) * 0.08), Vec3.ZERO, 30, 6);
			}
			if (step == 5) {
				// The dash leaves a streak of lightning behind.
				arc("dash", start.add(0, 1, 0), at.add(0, 1, 0), 8);
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
				finale = 5 + 4 * (bolts.size() + extra.size()) + 2;
			}
			if (step >= 5 && (step - 5) % 4 == 0) {
				int index = (step - 5) / 4;
				if (index < bolts.size()) {
					LivingEntity enemy = bolts.get(index);
					if (enemy.isAlive()) {
						Vec3 previous = index == 0 ? at : bolts.get(index - 1).position();
						strike(enemy.position());
						spear(enemy.position(), index);
						arc("arc" + index, previous.add(0, 1.0, 0), enemy.position().add(0, 1.0, 0), 8);
						hit(level, player, enemy, 4.5f);
						Effects.give(enemy, "slowness", 2, 20);
						BlockChanges.placeBriefly(level, player, enemy.blockPosition(), Blocks.FIRE.defaultBlockState(), 60);
						lastSpot = enemy.position();
					}
				} else if (index - bolts.size() < extra.size()) {
					Vec3 spot = extra.get(index - bolts.size());
					strike(spot);
					spear(spot, index);
					for (LivingEntity enemy : enemiesNear(player, spot, 3.0)) {
						hit(level, player, enemy, 4.5f);
					}
					crater(level, player, spot, 1.5);
					lastSpot = spot;
				}
			}
			// The last great bolt: a spear twice the size, a ring of thunder and a blast.
			if (step == finale && lastSpot != null) {
				Vec3 spot = lastSpot;
				fx.model("judgment", "thunder_spear", spot, FxStage.yaw(0.7), new Vec3(0, 30, 0), 5.0);
				fx.move("judgment", FxStage.yaw(0.7), new Vec3(0, 5.0 * 1.5 - 0.8, 0), 5.0, 2);
				fx.later(14, () -> fx.move("judgment", FxStage.yaw(0.7), new Vec3(0, 2, 0), 0.01, 6));
				fx.killLater("judgment", 21);
				shockwave("jring", "thunder_ring", spot, 6.0, 7);
				fx.move("cloud", FxStage.flat(5.0), Vec3.ZERO, 34, 3);
				fx.later(10, () -> fx.move("cloud", FxStage.flat(6.0), Vec3.ZERO, 0.01, 10));
				fx.killLater("cloud", 21);
				for (int i = 0; i < 3; i++) {
					double a = i * Math.PI * 2 / 3;
					strike(spot.add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5));
				}
				far(ParticleTypes.EXPLOSION_EMITTER, spot.add(0, 1, 0), 1, 0, 0, 0, 0);
				far(ParticleTypes.ELECTRIC_SPARK, spot.add(0, 1, 0), 80, 3.0, 1.0, 3.0, 0.5);
				sound(level, spot, SoundEvents.LIGHTNING_BOLT_THUNDER, 3.0f, 0.8f);
				sound(level, spot, SoundEvents.GENERIC_EXPLODE, 2.0f, 1.2f);
				for (LivingEntity enemy : enemiesNear(player, spot, 4.5)) {
					hit(level, player, enemy, 4.0f);
					knock(enemy, spot, 1.0, 0.6);
				}
				crater(level, player, spot, 1.5);
			}
			if (finale > 0 && step > finale + 22) {
				done = true;
			}
		}

		/** A crackling bolt of lightning stretched between two points, twisting as it flickers. */
		void arc(String name, Vec3 from, Vec3 to, int life) {
			Vec3 d = to.subtract(from);
			double length = d.length();
			if (length < 0.5) {
				return;
			}
			double flat = Math.sqrt(d.x * d.x + d.z * d.z);
			float[] q = FxStage.mul(FxStage.yaw(-Math.atan2(d.z, d.x)), FxStage.axis(0, 0, 1, Math.atan2(d.y, flat)));
			fx.model(name, "thunder_arc", from, q, Vec3.ZERO, length, 3.0, 3.0);
			fx.later(2, () -> fx.move(name, FxStage.mul(q, FxStage.axis(1, 0, 0, 2.0)), Vec3.ZERO, length, 2.5, 2.5, 0));
			fx.later(4, () -> fx.move(name, FxStage.mul(q, FxStage.axis(1, 0, 0, 4.0)), Vec3.ZERO, length, 3.0, 3.0, 0));
			fx.killLater(name, life);
		}

		/** A spear of lightning drops onto a spot and stands there crackling, with a ring of thunder under it. */
		void spear(Vec3 at, int index) {
			String name = "spear" + index;
			float[] spin = FxStage.yaw(index * 1.3);
			fx.model(name, "thunder_spear", at, spin, new Vec3(0, 20, 0), 3.2);
			fx.move(name, spin, new Vec3(0, 3.2 * 1.5 - 0.5, 0), 3.2, 1);
			fx.later(10, () -> fx.move(name, spin, new Vec3(0, 1, 0), 0.01, 4));
			fx.killLater(name, 15);
			String ring = "ring" + index;
			fx.model(ring, "thunder_circle", at.add(0, 0.15, 0), FxStage.flat(index), Vec3.ZERO, 0.5);
			fx.move(ring, FxStage.flat(index + 1.2), Vec3.ZERO, 8, 4);
			fx.later(5, () -> fx.move(ring, FxStage.flat(index + 2.4), Vec3.ZERO, 0.01, 8));
			fx.killLater(ring, 14);
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
				circle("ice_circle", start, 19, castTicks);
			}
			if (age < castTicks) {
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
					int count = 6 + ringIndex * 3;
					double size = 1.6 + ringIndex * 0.4;
					for (int i = 0; i < count; i++) {
						double angle = i * Math.PI * 2 / count + ringIndex * 0.3 + level.random.nextDouble() * 0.2;
						Vec3 base = ground(level, start.add(Math.cos(angle) * r, 2.0, Math.sin(angle) * r));
						// A crystal of ice bursts up out of the ground, leaning outward.
						String name = "spire" + ringIndex + "_" + i;
						float[] lean = FxStage.tiltOut(angle, 0.2 + ringIndex * 0.1, level.random.nextDouble() * 6);
						fx.model(name, "ice_spire", base, lean, new Vec3(0, -0.5, 0), size, 0.01, size);
						fx.move(name, lean, new Vec3(0, size * 0.9, 0), size, size, size, 3);
						far(ICE_DUST, base.add(0, size, 0), 2, 0.3, size * 0.6, 0.3, 0);
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
				// Everything shatters at once.
				for (int ringIndex = 0; ringIndex < 3; ringIndex++) {
					for (int i = 0; i < 6 + ringIndex * 3; i++) {
						fx.kill("spire" + ringIndex + "_" + i);
					}
				}
				fx.kill("circle");
				shockwave("wave", "ice_wave", start, 10.0, 6);
				sound(level, start, SoundEvents.GLASS_BREAK, 3.0f, 0.6f);
				far(ParticleTypes.ITEM_SNOWBALL, start.add(0, 1, 0), 120, 8.0, 1.5, 8.0, 0.2);
				for (LivingEntity enemy : marked) {
					if (enemy.isAlive() && enemy.position().distanceTo(start) <= 12.0) {
						hit(level, player, enemy, 4.0f);
						far(ParticleTypes.ITEM_SNOWBALL, enemy.position().add(0, 1, 0), 30, 0.6, 0.8, 0.6, 0.15);
					}
				}
			}
			if (step >= 57) {
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
				circle("blood_circle", at, 9, castTicks);
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
				// The circle of the pact follows the caster and beats like a heart.
				if (step % 5 == 0) {
					turnCircle(at, step % 20 == 0 ? 10.5 : 9, step * 0.05 + 0.6, step % 20 == 0 ? 2 : 5);
				}
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
				fx.kill("circle");
				shockwave("wave", "blood_wave", at, 8.5, 8);
			}
			// Thorns of blood burst out of the ground in eight lines, the far ones a moment later.
			if (step == 100 || step == 102 || step == 104) {
				int k = (step - 100) / 2;
				double r = 2.5 + k * 2.5;
				double size = 1.4 + k * 0.5;
				for (int i = 0; i < 8; i++) {
					double angle = i * Math.PI / 4 + k * 0.12;
					Vec3 base = ground(level, at.add(Math.cos(angle) * r, 2.0, Math.sin(angle) * r));
					String name = "thorn" + k + "_" + i;
					float[] lean = FxStage.tiltOut(angle, 0.35, level.random.nextDouble() * 6);
					fx.model(name, "blood_thorn", base, lean, new Vec3(0, -0.5, 0), size, 0.01, size);
					fx.move(name, lean, new Vec3(0, size * 0.9, 0), size, size, size, 2);
					fx.later(22 - k * 2, () -> fx.move(name, lean, new Vec3(0, -0.5, 0), size, 0.01, size, 6));
					fx.killLater(name, 29 - k * 2);
				}
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
			if (step > 130) {
				done = true;
			}
		}

		// ---- shared bits for the skills above

		/** A ground circle that grows in, then turns slowly; returns its scale. */
		void circle(String model, Vec3 at, double scale, int grow) {
			circleAt = at.add(0, 0.12, 0);
			fx.model("circle", model, circleAt, FxStage.flat(0), Vec3.ZERO, 0.1);
			fx.move("circle", FxStage.flat(0.6), Vec3.ZERO, scale, grow);
		}

		/** Turns the ground circle (every few ticks), optionally following a point. */
		void turnCircle(Vec3 follow, double scale, double turn, int ticks) {
			Vec3 offset = follow == null ? Vec3.ZERO : follow.add(0, 0.12, 0).subtract(circleAt);
			fx.move("circle", FxStage.flat(turn), offset, scale, ticks);
		}

		/** The circle flares and fades away. */
		void endCircle(double flare, int ticks) {
			fx.move("circle", FxStage.flat(age * 0.05 + 1.0), Vec3.ZERO, flare, 3);
			fx.later(3, () -> fx.move("circle", FxStage.flat(age * 0.05 + 2.0), Vec3.ZERO, 0.01, ticks));
			fx.killLater("circle", ticks + 4);
		}

		/** A flat ring of force that expands over the ground and is gone. */
		void shockwave(String name, String model, Vec3 at, double radius, int ticks) {
			fx.model(name, model, at.add(0, 0.3, 0), FxStage.flat(level.random.nextDouble() * 6), Vec3.ZERO, 0.5);
			fx.move(name, FxStage.flat(level.random.nextDouble() * 6), Vec3.ZERO, radius * 2.05, ticks);
			fx.killLater(name, ticks + 2);
		}

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

		// ---- Spirit King: พิพากษาแปดธาตุ. The clients draw the spectacle (see KingEffects); the server runs the
		// timeline: the warning, the pull, the silence, the flash, three waves that strike as they pass, the
		// crater, and the poisoned, burning ground afterwards.
		void king() {
			int radius = SkillConfig.ultimateRadius;
			int pauseStart = castTicks - KingShow.PAUSE;
			if (age == 0) {
				show = new KingShow(level, player, start, radius, castTicks);
				MinecraftServer server = level.getServer();
				Component text = SoulManager.msg("king_casting", player.getName(),
						(int) start.x, (int) start.y, (int) start.z).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
				server.getPlayerList().broadcastSystemMessage(text, false);
				com.kalasawa.sacredsouls.network.KingCastPayload payload =
						new com.kalasawa.sacredsouls.network.KingCastPayload(start.x, start.y, start.z, radius, castTicks);
				for (ServerPlayer other : level.players()) {
					if (other.position().distanceTo(start) <= radius * 3 + 128
							&& ServerPlayNetworking.canSend(other, com.kalasawa.sacredsouls.network.KingCastPayload.TYPE)) {
						ServerPlayNetworking.send(other, payload);
					}
				}
				everyone(SoundEvents.WITHER_SPAWN, 0.6f, 0.5f, -1);
			}
			if (show != null) {
				show.late();
				show.lateWaves();
				if (age < castTicks) {
					show.cast(age);
				} else {
					show.after(age - castTicks);
				}
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
				// A heartbeat that quickens as the circle fills.
				int beat = Math.max(5, (int) (24 - 18 * progress));
				if (age < pauseStart && age % beat == 0) {
					everyone(SoundEvents.WARDEN_HEARTBEAT, 0.7f, 0.6f + (float) progress * 0.5f, radius * 1.5);
				}
				if (age == castTicks / 2) {
					everyone(SoundEvents.ENDER_DRAGON_GROWL, 1.0f, 0.5f, radius * 2);
				}
				// The last part of the cast drags enemies near the centre inwards.
				if (age >= castTicks - 50 && age < pauseStart) {
					for (LivingEntity enemy : enemiesNear(player, start, radius * 0.3)) {
						knock(enemy, start, -0.12, 0.0);
						Effects.give(enemy, "slowness", 1, 10);
					}
				}
				return;
			}
			int step = age - castTicks;
			if (step == 0) {
				everyone(SoundEvents.GENERIC_EXPLODE, 1.0f, 0.35f, radius * 3);
				everyone(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0f, 0.5f, radius * 3);
				everyone(SoundEvents.END_PORTAL_SPAWN, 1.0f, 0.6f, radius * 3);
				everyone(SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 0.5f, radius * 2);
			}
			// Three waves, 22 ticks apart. Each rolls out over 14 ticks and strikes what its front passes.
			double[] reach = {radius / 3.0, radius * 2.0 / 3.0, radius};
			for (int w = 0; w < 3; w++) {
				int local = step - w * 22;
				if (local < 0 || local > 14) {
					continue;
				}
				double from = w == 0 ? 0 : reach[w - 1];
				double front = from + (reach[w] - from) * Math.sqrt(local / 14.0);
				if (local == 0) {
					marked.clear();
					if (w > 0) {
						everyone(SoundEvents.GENERIC_EXPLODE, 1.0f, 0.45f + w * 0.05f, radius * 3);
						everyone(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 0.6f, radius * 3);
					}
					if (SkillConfig.ultimateBreaksBlocks) {
						BlockChanges.queue(new CraterJob(level, player, start, from, reach[w], radius,
								SkillConfig.ultimateRegrowSeconds * 20 + w * 40));
					}
				}
				// Each wave strikes everything inside its front once, so the centre takes all three.
				for (LivingEntity enemy : enemiesNear(player, start, front + 1.0)) {
					if (!marked.contains(enemy)) {
						marked.add(enemy);
						strikeWave(enemy, w);
					}
				}
			}
			// Fallout: the inner ground stays poisonous for ten seconds, and fires break out.
			if (step >= 60 && step < 260) {
				if (step % 20 == 0) {
					for (LivingEntity enemy : enemiesNear(player, start, radius * 0.5)) {
						Effects.give(enemy, "wither", 1, 40);
						Effects.give(enemy, "hunger", 1, 40);
					}
				}
				if (step == 70) {
					for (int i = 0; i < 80; i++) {
						double angle = level.random.nextDouble() * Math.PI * 2;
						double r = radius * Math.sqrt(level.random.nextDouble()) * 0.8;
						Vec3 spot = ground(level, start.add(Math.cos(angle) * r, 10, Math.sin(angle) * r));
						BlockChanges.placeBriefly(level, player, BlockPos.containing(spot), Blocks.FIRE.defaultBlockState(), 200 + level.random.nextInt(200));
					}
				}
			}
			if (step >= 260) {
				if (show != null) {
					show.finish();
					show = null;
				}
				done = true;
			}
		}

		/** One wave's blow: eight hearts, half of it magic that no armor stops, flung outward and set alight. */
		void strikeWave(LivingEntity enemy, int index) {
			hit(level, player, enemy, 8.0f);
			if (enemy.isAlive()) {
				enemy.invulnerableTime = 0;
				enemy.hurtServer(level, level.damageSources().magic(), 8.0f);
			}
			enemy.igniteForSeconds(4.0f);
			knock(enemy, start, 1.4 + index * 0.3, 0.7);
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

	}

	/**
	 * The ultimate's crater, one ring of ground at a time, nearest columns first so it spreads outward:
	 * a bowl up to 20 blocks deep in the middle, and everything up to 30 blocks above the centre carved away.
	 * Both scale with the radius, and the bowl is sharper than the cut above, so the far edge only loses
	 * what stands on the ground.
	 */
	private static final class CraterJob implements BlockChanges.Job {
		/** How far above the caster's feet the blast still flattens the land. */
		private static final int SURFACE_CAP = 80;
		/** How far below the bowl the blast still drains water and lava. */
		private static final int FLUID_REACH = 64;
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
						int top = baseY + height;
						BlockPos column = new BlockPos(cx + dx, baseY, cz + dz);
						if (level.isLoaded(column)) {
							// Everything standing in the blast goes: hills, trees and buildings, up to the real surface
							// (capped so a mountain is cut down, not erased), and no time is wasted on empty air above it.
							int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx + dx, cz + dz) - 1;
							top = Math.min(surface, baseY + SURFACE_CAP);
						}
						if (top >= baseY - depth) {
							columns.add(new int[] {cx + dx, cz + dz, top, baseY - depth, (int) (d * 100)});
						}
					}
				}
			}
			columns.sort(Comparator.comparingInt(c -> c[4]));
			this.column = -1;
		}

		@Override
		public BlockPos next() {
			// Below the bowl, keep going while there is water or lava, so a lake or the sea is drained to its bed.
			while (column < 0 || (y < bottom && !fluidBelow())) {
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

		/** Whether the block under the bowl at the current point still holds a fluid, within reach of the blast. */
		private boolean fluidBelow() {
			int[] c = columns.get(column);
			if (y < c[3] - FLUID_REACH || y < level.getMinY()) {
				return false;
			}
			return !level.getFluidState(new BlockPos(c[0], y, c[1])).isEmpty();
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

		@Override
		public boolean everything() {
			return true;
		}
	}
}
