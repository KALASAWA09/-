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

	/** A small bowl of natural blocks, at most radius 2, under a point. */
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
		final Vec3 target;
		final Vec3 start;
		final Vec3 direction;
		final float startHealth;
		final List<LivingEntity> marked = new ArrayList<>();
		final List<LivingEntity> wallCheck = new ArrayList<>();
		final List<LivingEntity> bolts = new ArrayList<>();
		int age;
		boolean done;

		Cast(ServerPlayer player, ServerLevel level, Skill skill) {
			this.player = player;
			this.level = level;
			this.skill = skill;
			this.castTicks = skill == Skill.KING ? SkillConfig.ultimateCastSeconds * 20 : skill.castTicks;
			this.start = player.position();
			Vec3 look = player.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			this.direction = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
			this.target = switch (skill) {
				case FIRE -> aim(player, 16);
				case LIGHT -> aim(player, 12);
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

		// ---- Fire: อุกกาบาตเพลิง. A fireball falls on the marked spot.
		void fire() {
			if (age == 0) {
				sound(level, start, SoundEvents.BLAZE_SHOOT, 1.0f, 0.7f);
			}
			if (age < castTicks) {
				if (age % 2 == 0) {
					ring(level, ParticleTypes.FLAME, target, 3.0, 28, 0.1, age * 0.05);
				}
				double t = (double) age / castTicks;
				Vec3 ball = target.add(direction.scale(-3.0 * (1 - t))).add(0, 12.0 * (1 - t) + 0.5, 0);
				level.sendParticles(ParticleTypes.FLAME, ball.x, ball.y, ball.z, 8, 0.3, 0.3, 0.3, 0.01);
				level.sendParticles(ParticleTypes.LAVA, ball.x, ball.y, ball.z, 1, 0.2, 0.2, 0.2, 0);
				level.sendParticles(ParticleTypes.SMOKE, ball.x, ball.y + 0.6, ball.z, 3, 0.2, 0.4, 0.2, 0.01);
				return;
			}
			if (age == castTicks) {
				level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 0.5, target.z, 1, 0, 0, 0, 0);
				level.sendParticles(ParticleTypes.FLAME, target.x, target.y + 0.5, target.z, 70, 1.5, 0.6, 1.5, 0.12);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, target.x, target.y + 1.0, target.z, 25, 1.5, 0.8, 1.5, 0.03);
				sound(level, target, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.9f);
				for (LivingEntity enemy : enemiesNear(player, target, 3.0)) {
					hit(level, player, enemy, 6.0f);
					enemy.igniteForSeconds(3.0f);
					Vec3 push = enemy.position().subtract(target).normalize().scale(0.6);
					enemy.push(push.x, 0.35, push.z);
					enemy.hurtMarked = true;
				}
				crater(level, player, target, 2.0);
			}
			// Flames linger on the ground for three seconds.
			if (age % 4 == 0) {
				level.sendParticles(ParticleTypes.FLAME, target.x, target.y + 0.2, target.z, 10, 2.5, 0.1, 2.5, 0.01);
			}
			if (age >= castTicks + 60) {
				done = true;
			}
		}

		// ---- Light: พิพากษาสุริยะ. Two rune rings form, then a pillar of light strikes.
		void light() {
			if (age == 0) {
				sound(level, target, SoundEvents.ENCHANTMENT_TABLE_USE, 1.5f, 0.8f);
			}
			if (age < castTicks) {
				double grow = Math.min(1.0, 0.3 + age / (double) castTicks);
				ring(level, GOLD, target, 4.0 * grow, 40, 0.05, age * 0.06);
				ring(level, GOLD, target, 3.1 * grow, 32, 0.05, -age * 0.06);
				ring(level, ParticleTypes.END_ROD, target, 2.6 * grow, 10, 1.6, -age * 0.12);
				level.sendParticles(ParticleTypes.ENCHANT, target.x, target.y + 1.2, target.z, 12, 2.5, 1.0, 2.5, 0.6);
				if (age == castTicks / 2) {
					sound(level, target, SoundEvents.BEACON_POWER_SELECT, 1.2f, 1.4f);
				}
				return;
			}
			if (age == castTicks) {
				for (double y = 0; y < 14; y += 0.4) {
					level.sendParticles(ParticleTypes.END_ROD, target.x, target.y + y, target.z, 2, 0.25, 0.1, 0.25, 0);
				}
				level.sendParticles(ParticleTypes.FIREWORK, target.x, target.y + 1.0, target.z, 60, 2.0, 1.0, 2.0, 0.15);
				sound(level, target, SoundEvents.BEACON_ACTIVATE, 1.5f, 1.2f);
				sound(level, target, SoundEvents.FIREWORK_ROCKET_BLAST, 1.5f, 0.8f);
				for (LivingEntity enemy : enemiesNear(player, target, 4.0)) {
					float damage = enemy.position().distanceTo(target) <= 2.0 ? 8.0f : 6.0f;
					if (enemy.getType().is(EntityTypeTags.UNDEAD)) {
						damage *= 1.5f;
					}
					hit(level, player, enemy, damage);
					Effects.give(enemy, "blindness", 0, 20);
				}
				player.heal(4.0f);
				surface(level, target, 4.0, (pos, state) -> {
					if (state.is(Blocks.GRASS_BLOCK)) {
						BlockChanges.replace(level, player, pos, Blocks.DIRT.defaultBlockState(), true, regrow());
					} else if (state.is(BlockTags.SNOW) || state.is(Blocks.SNOW) || state.is(BlockTags.ICE)) {
						BlockChanges.breakBlock(level, player, pos, true, regrow());
					}
				});
			}
			if (age % 3 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, target.x, target.y + 2.0, target.z, 4, 2.5, 1.5, 2.5, 0.02);
			}
			if (age >= castTicks + 20) {
				done = true;
			}
		}

		// ---- Water: คลื่นยักษ์. A cone of water sweeps forward and slams enemies into walls.
		void water() {
			Vec3 origin = player.position();
			if (age == 0) {
				sound(level, origin, SoundEvents.BUCKET_EMPTY, 1.2f, 0.7f);
			}
			if (age < castTicks) {
				level.sendParticles(ParticleTypes.SPLASH, origin.x, origin.y + 0.8, origin.z, 10, 1.0, 0.6, 1.0, 0.05);
				level.sendParticles(ParticleTypes.BUBBLE_POP, origin.x, origin.y + 1.0, origin.z, 4, 0.8, 0.5, 0.8, 0.02);
				return;
			}
			int step = age - castTicks;
			if (step == 0) {
				sound(level, origin, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.5f, 0.6f);
				sound(level, origin, SoundEvents.GENERIC_SPLASH, 1.5f, 0.8f);
				boolean wet = level.isRainingAt(player.blockPosition().above()) || player.isInWater();
				for (LivingEntity enemy : enemiesNear(player, start, 6.0)) {
					Vec3 to = enemy.position().subtract(start);
					Vec3 flat = new Vec3(to.x, 0, to.z);
					if (flat.lengthSqr() < 0.01 || flat.normalize().dot(direction) < 0.7) {
						continue;
					}
					hit(level, player, enemy, wet ? 8.0f : 6.0f);
					enemy.clearFire();
					Effects.give(enemy, "slowness", 0, 40);
					enemy.setDeltaMovement(direction.x * 1.4, 0.35, direction.z * 1.4);
					enemy.hurtMarked = true;
					wallCheck.add(enemy);
				}
				// The wave sweeps away plants, leaves and snow in front.
				for (int d = 1; d <= 6; d++) {
					for (int side = -d / 2 - 1; side <= d / 2 + 1; side++) {
						Vec3 at = start.add(direction.scale(d)).add(-direction.z * side, 0, direction.x * side);
						for (int dy = 0; dy <= 2; dy++) {
							BlockPos pos = BlockPos.containing(at).above(dy);
							if (BlockChanges.isSoft(level.getBlockState(pos))) {
								BlockChanges.breakBlock(level, player, pos, true, regrow());
							}
						}
					}
				}
			}
			// The wave front rolls forward over eight ticks.
			double front = Math.min(6.0, (step + 1) * 0.8);
			for (int side = -4; side <= 4; side++) {
				double spread = front * 0.75 * side / 4.0;
				Vec3 at = start.add(direction.scale(front)).add(-direction.z * spread, 0, direction.x * spread);
				level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.8, at.z, 6, 0.3, 0.6, 0.3, 0.1);
				level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y + 1.2, at.z, 2, 0.3, 0.4, 0.3, 0.02);
				level.sendParticles(ParticleTypes.FALLING_WATER, at.x, at.y + 1.8, at.z, 2, 0.3, 0.2, 0.3, 0);
			}
			// Slammed into a wall: one more hit.
			for (Iterator<LivingEntity> it = wallCheck.iterator(); it.hasNext();) {
				LivingEntity enemy = it.next();
				if (enemy.horizontalCollision && enemy.isAlive()) {
					hit(level, player, enemy, 2.0f);
					level.sendParticles(ParticleTypes.SPLASH, enemy.getX(), enemy.getY() + 1.0, enemy.getZ(), 20, 0.4, 0.6, 0.4, 0.1);
					it.remove();
				}
			}
			if (step >= 10) {
				done = true;
			}
		}

		// ---- Death: ยมทูตเก็บวิญญาณ. Souls sweep around the caster and bring life back.
		void death() {
			if (age == 0) {
				sound(level, start, SoundEvents.SOUL_ESCAPE, 2.0f, 0.6f);
				surface(level, start, 4.0, (pos, state) -> {
					if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)) {
						BlockChanges.replace(level, player, pos, Blocks.SOUL_SOIL.defaultBlockState(), true, regrow());
					}
				});
			}
			Vec3 center = player.position();
			if (age < castTicks) {
				if (age % 2 == 0) {
					ring(level, ParticleTypes.SOUL, center, 4.0, 24, 0.2, age * 0.08);
				}
				level.sendParticles(ParticleTypes.SOUL, center.x, center.y + 1.0, center.z, 3, 3.0, 0.5, 3.0, 0.01);
				return;
			}
			int step = age - castTicks;
			if (step < 6) {
				// The scythe: a sweeping arc of soul fire, one sixth of the circle per tick.
				for (int i = 0; i < 12; i++) {
					double angle = (step + i / 12.0) * Math.PI / 3;
					for (double r = 2.8; r <= 4.0; r += 0.6) {
						level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x + Math.cos(angle) * r, center.y + 1.0,
								center.z + Math.sin(angle) * r, 1, 0, 0.3, 0, 0.0);
					}
				}
			}
			if (step == 0) {
				sound(level, center, SoundEvents.WITHER_SHOOT, 1.0f, 0.6f);
				float dealt = 0;
				for (LivingEntity enemy : enemiesNear(player, center, 4.0)) {
					float before = enemy.getHealth();
					hit(level, player, enemy, 7.0f);
					Effects.give(enemy, "wither", 0, 60);
					dealt += Math.max(0, before - enemy.getHealth());
					line(level, ParticleTypes.SOUL, enemy.position().add(0, 1.2, 0), center.add(0, 1.2, 0), 0.5);
				}
				player.heal(Math.min(dealt * 0.5f, 4.0f));
			}
			if (step >= 8) {
				done = true;
			}
		}

		// ---- Darkness: ม่านราตรี. A ring of black mist for five seconds, then it collapses.
		void darkness() {
			if (age == 0) {
				sound(level, start, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.5f, 0.7f);
			}
			if (age < castTicks) {
				ring(level, ParticleTypes.SMOKE, start, 5.0, 40, 0.2, age * 0.1);
				return;
			}
			int step = age - castTicks;
			if (step < 100) {
				if (step % 2 == 0) {
					level.sendParticles(ParticleTypes.SQUID_INK, start.x, start.y + 1.2, start.z, 20, 3.0, 1.0, 3.0, 0.01);
					level.sendParticles(ParticleTypes.LARGE_SMOKE, start.x, start.y + 1.0, start.z, 10, 3.2, 1.0, 3.2, 0.005);
					level.sendParticles(SHADOW, start.x, start.y + 1.5, start.z, 10, 3.0, 1.2, 3.0, 0);
					ring(level, ParticleTypes.SMOKE, start, 5.0, 24, 0.3, step * 0.05);
				}
				if (step % 10 == 0) {
					level.sendParticles(ParticleTypes.SCULK_SOUL, start.x, start.y + 1.0, start.z, 3, 3.0, 0.8, 3.0, 0.02);
				}
				if (step % 20 == 0) {
					for (LivingEntity enemy : enemiesNear(player, start, 5.0)) {
						Effects.give(enemy, "darkness", 0, 60);
						hit(level, player, enemy, 1.0f);
					}
				}
				return;
			}
			// Collapse: the mist rushes inward and bites a small hole in the ground.
			for (int i = 0; i < 48; i++) {
				double angle = i * Math.PI * 2 / 48;
				double x = start.x + Math.cos(angle) * 5.0;
				double z = start.z + Math.sin(angle) * 5.0;
				level.sendParticles(ParticleTypes.SQUID_INK, x, start.y + 1.0, z, 0, -Math.cos(angle), 0.05, -Math.sin(angle), 0.6);
			}
			level.sendParticles(ParticleTypes.LARGE_SMOKE, start.x, start.y + 0.5, start.z, 30, 0.8, 0.5, 0.8, 0.05);
			sound(level, start, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 0.6f);
			for (LivingEntity enemy : enemiesNear(player, start, 5.0)) {
				hit(level, player, enemy, 4.0f);
			}
			crater(level, player, start, 1.5);
			done = true;
		}

		// ---- Lightning: พิโรธอัสนี. Dash forward, then lightning jumps between up to three enemies.
		void lightning() {
			Vec3 at = player.position();
			if (age == 0) {
				sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.6f);
			}
			if (age < castTicks) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, 8, 0.6, 0.8, 0.6, 0.05);
				return;
			}
			int step = age - castTicks;
			if (step == 0) {
				player.setDeltaMovement(direction.x * 1.6, 0.15, direction.z * 1.6);
				player.hurtMarked = true;
			}
			if (step < 4) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.8, at.z, 12, 0.3, 0.5, 0.3, 0.02);
			}
			if (step == 3) {
				// Pick the chain: nearest enemy, then the nearest one to it that is not hit yet.
				Vec3 from = at;
				List<LivingEntity> chain = new ArrayList<>();
				for (int i = 0; i < 3; i++) {
					Vec3 origin = from;
					LivingEntity next = enemiesNear(player, origin, 6.0).stream()
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
			}
			if (step >= 3 && (step - 3) % 6 == 0) {
				int index = (step - 3) / 6;
				if (index >= bolts.size()) {
					done = true;
					return;
				}
				LivingEntity enemy = bolts.get(index);
				if (enemy.isAlive()) {
					LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
					if (bolt != null) {
						bolt.setPos(enemy.getX(), enemy.getY(), enemy.getZ());
						bolt.setVisualOnly(true);
						level.addFreshEntity(bolt);
					}
					hit(level, player, enemy, 4.0f);
					Vec3 previous = index == 0 ? at : bolts.get(index - 1).position();
					line(level, ParticleTypes.ELECTRIC_SPARK, previous.add(0, 1.0, 0), enemy.position().add(0, 1.0, 0), 0.4);
					BlockChanges.placeBriefly(level, player, enemy.blockPosition(), Blocks.FIRE.defaultBlockState(), 60);
				}
			}
			if (step > 3 + 6 * 3) {
				done = true;
			}
		}

		// ---- Ice: คุกน้ำแข็ง. Frost bursts out, ice spikes trap enemies, then the ice shatters.
		void ice() {
			if (age == 0) {
				sound(level, start, SoundEvents.PLAYER_HURT_FREEZE, 1.5f, 0.6f);
			}
			if (age < castTicks) {
				if (age % 2 == 0) {
					ring(level, ParticleTypes.SNOWFLAKE, start, 4.0, 32, 0.2, age * 0.07);
				}
				level.sendParticles(ParticleTypes.SNOWFLAKE, start.x, start.y + 1.0, start.z, 8, 3.0, 0.6, 3.0, 0.01);
				return;
			}
			int step = age - castTicks;
			if (step == 0) {
				sound(level, start, SoundEvents.GLASS_PLACE, 1.5f, 0.6f);
				for (LivingEntity enemy : enemiesNear(player, start, 4.0)) {
					hit(level, player, enemy, 5.0f);
					Effects.give(enemy, "slowness", 9, 30);
					enemy.setTicksFrozen(Math.max(enemy.getTicksFrozen(), enemy.getTicksRequiredToFreeze() + 40));
					marked.add(enemy);
					// Ice grows up around the trapped enemy.
					BlockPos feet = enemy.blockPosition();
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							if ((dx != 0 || dz != 0) && level.random.nextFloat() < 0.6f) {
								BlockChanges.placeBriefly(level, player, feet.offset(dx, 0, dz), Blocks.PACKED_ICE.defaultBlockState(), 30);
							}
						}
					}
				}
				// Spikes around the caster.
				for (int i = 0; i < 10; i++) {
					double angle = i * Math.PI * 2 / 10 + level.random.nextDouble() * 0.3;
					BlockPos pos = BlockPos.containing(ground(level, start.add(Math.cos(angle) * 3.0, 1.0, Math.sin(angle) * 3.0)));
					BlockChanges.placeBriefly(level, player, pos, Blocks.PACKED_ICE.defaultBlockState(), 30);
					if (level.random.nextBoolean()) {
						BlockChanges.placeBriefly(level, player, pos.above(), Blocks.PACKED_ICE.defaultBlockState(), 30);
					}
				}
				// Water freezes, plants and snow are swept away.
				surface(level, start, 4.0, (pos, state) -> {
					if (state.is(Blocks.WATER)) {
						BlockChanges.replace(level, player, pos, Blocks.FROSTED_ICE.defaultBlockState(), true, regrow());
					} else if (BlockChanges.isSoft(state)) {
						BlockChanges.breakBlock(level, player, pos, true, regrow());
					}
				});
				level.sendParticles(ParticleTypes.SNOWFLAKE, start.x, start.y + 1.0, start.z, 80, 3.0, 1.0, 3.0, 0.05);
			}
			if (step == 30) {
				sound(level, start, SoundEvents.GLASS_BREAK, 1.5f, 0.8f);
				for (LivingEntity enemy : marked) {
					if (enemy.isAlive() && enemy.position().distanceTo(start) <= 7.0) {
						hit(level, player, enemy, 3.0f);
						level.sendParticles(ParticleTypes.ITEM_SNOWBALL, enemy.getX(), enemy.getY() + 1.0, enemy.getZ(), 30, 0.5, 0.6, 0.5, 0.1);
					}
				}
				done = true;
			}
		}

		// ---- Blood: พันธสัญญาโลหิต. Pay 3 hearts for five seconds of power, then burst.
		void blood() {
			Vec3 at = player.position();
			if (age == 0) {
				player.setHealth(Math.max(1.0f, player.getHealth() - 6.0f));
				sound(level, at, SoundEvents.WARDEN_HEARTBEAT, 2.0f, 1.0f);
			}
			if (age < castTicks) {
				for (int i = 0; i < 4; i++) {
					double t = (age * 4 + i) / (castTicks * 4.0);
					double angle = t * Math.PI * 6;
					level.sendParticles(BLOOD_RED, at.x + Math.cos(angle), at.y + t * 2.2, at.z + Math.sin(angle), 1, 0, 0, 0, 0);
				}
				return;
			}
			int step = age - castTicks;
			if (step == 0) {
				BLOOD.put(player.getUUID(), new BloodPact());
			}
			if (step < 100) {
				if (step % 2 == 0) {
					double angle = step * 0.4;
					level.sendParticles(BLOOD_RED, at.x + Math.cos(angle) * 0.8, at.y + 1.0, at.z + Math.sin(angle) * 0.8, 2, 0.05, 0.4, 0.05, 0);
				}
				if (step % 20 == 0) {
					sound(level, at, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 1.2f);
				}
				return;
			}
			BloodPact pact = BLOOD.remove(player.getUUID());
			float bonus = pact == null ? 0 : pact.healed * 0.5f;
			float damage = Math.min(4.0f + bonus, 8.0f);
			ring(level, BLOOD_RED, at, 3.0, 48, 0.4, 0);
			ring(level, BLOOD_RED, at, 2.2, 36, 0.8, 0.3);
			level.sendParticles(BLOOD_RED, at.x, at.y + 0.8, at.z, 80, 1.8, 0.6, 1.8, 0);
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y + 1.0, at.z, 15, 1.5, 0.5, 1.5, 0.1);
			sound(level, at, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.6f);
			for (LivingEntity enemy : enemiesNear(player, at, 3.0)) {
				hit(level, player, enemy, damage);
			}
			crater(level, player, at, 1.2);
			done = true;
		}

		// ---- Spirit King: พิพากษาแปดธาตุ. A great circle in the sky, eight elements merge, three waves.
		void king() {
			int radius = SkillConfig.ultimateRadius;
			Vec3 sky = start.add(0, 30, 0);
			if (age == 0) {
				MinecraftServer server = level.getServer();
				Component text = SoulManager.msg("king_casting", player.getName(),
						(int) start.x, (int) start.y, (int) start.z).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
				server.getPlayerList().broadcastSystemMessage(text, false);
				for (ServerPlayer other : server.getPlayerList().getPlayers()) {
					if (other.level() == level) {
						level.playSound(null, other.getX(), other.getY(), other.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.5f, 0.8f);
					}
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
				double ringRadius = radius * 0.85 * Math.min(1.0, 0.4 + progress * 1.5);
				// The circle in the sky: three rings and an eight-point star, turning slowly.
				double phase = age * 0.01;
				// Every other tick only: dust lingers long enough, and fewer particles keep the frame rate up.
				if (age % 2 == 0) {
					farRing(level, KING_GOLD, sky, ringRadius, 16, phase + level.random.nextDouble(), 0.3);
					farRing(level, KING_GOLD, sky, ringRadius * 0.88, 12, -phase + level.random.nextDouble(), 0.3);
					farRing(level, KING_GOLD, sky, ringRadius * 0.45, 8, phase * 2 + level.random.nextDouble(), 0.2);
				}
				if (age % 10 == 0) {
					for (int i = 0; i < 8; i++) {
						double a = phase + i * Math.PI / 4;
						double b = a + Math.PI * 3 / 4;
						Vec3 from = sky.add(Math.cos(a) * ringRadius * 0.88, 0, Math.sin(a) * ringRadius * 0.88);
						Vec3 to = sky.add(Math.cos(b) * ringRadius * 0.88, 0, Math.sin(b) * ringRadius * 0.88);
						for (int k = 0; k <= 8; k++) {
							Vec3 p = from.add(to.subtract(from).scale(k / 8.0));
							level.sendParticles(KING_GOLD, true, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
						}
					}
					// The shadow on the ground shows how far the blast will reach.
					farRing(level, KING_SHADOW, start.add(0, 0.3, 0), radius, 64, phase, 0.0);
				}
				// The eight elements orbit, faster and faster, and spiral in during the last part.
				double spin = age * (0.02 + 0.12 * progress * progress);
				double orbit = progress < 0.6 ? ringRadius * 1.05 : ringRadius * 1.05 * Mth.clamp((1.0 - progress) / 0.4, 0, 1);
				Spirit[] spirits = Spirit.values();
				for (int i = 0; i < spirits.length; i++) {
					double angle = spin + i * Math.PI / 4;
					double x = sky.x + Math.cos(angle) * orbit;
					double z = sky.z + Math.sin(angle) * orbit;
					level.sendParticles(spiritParticle(spirits[i]), true, true, x, sky.y, z, 2, 0.6, 0.6, 0.6, 0.01);
					level.sendParticles(new DustParticleOptions(spirits[i].color, 3.0f), true, true, x, sky.y, z, 1, 0.8, 0.8, 0.8, 0);
				}
				if (age % 20 == 0) {
					sound(level, start, SoundEvents.BEACON_AMBIENT, 4.0f, 0.5f + (float) progress);
				}
				return;
			}
			int step = age - castTicks;
			if (step == 0) {
				// The merged core falls as a pillar of light.
				for (double y = 0; y <= 30; y += 1.0) {
					level.sendParticles(KING_CORE, true, true, start.x, start.y + y, start.z, 3, 0.6, 0.2, 0.6, 0);
					level.sendParticles(ParticleTypes.END_ROD, true, true, start.x, start.y + y, start.z, 2, 0.4, 0.2, 0.4, 0.02);
				}
				for (ServerPlayer other : level.getServer().getPlayerList().getPlayers()) {
					if (other.level() == level) {
						level.playSound(null, other.getX(), other.getY(), other.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0f, 0.5f);
					}
				}
			}
			double[] waves = {0.4, 0.7, 1.0};
			for (int w = 0; w < 3; w++) {
				if (step == 4 + w * 20) {
					wave(w, radius * waves[w], w == 0 ? 0 : radius * waves[w - 1]);
				}
			}
			if (step >= 4 + 2 * 20 + 10) {
				done = true;
			}
		}

		void wave(int index, double radius, double inner) {
			for (int i = 0; i < 4; i++) {
				sound(level, start.add((level.random.nextDouble() - 0.5) * radius, 0, (level.random.nextDouble() - 0.5) * radius),
						SoundEvents.GENERIC_EXPLODE, 4.0f, 0.5f + index * 0.1f);
			}
			// A shockwave ring of every element's color, with blasts along it.
			Spirit[] spirits = Spirit.values();
			int points = (int) (radius * 2.5);
			for (int i = 0; i < points; i++) {
				double angle = i * Math.PI * 2 / points;
				double x = start.x + Math.cos(angle) * radius;
				double z = start.z + Math.sin(angle) * radius;
				level.sendParticles(new DustParticleOptions(spirits[i % spirits.length].color, 3.0f), true, true, x, start.y + 1.5, z, 2, 0.8, 1.2, 0.8, 0);
				if (i % 6 == 0) {
					level.sendParticles(ParticleTypes.EXPLOSION, true, true, x, start.y + 1.0, z, 1, 0.5, 0.5, 0.5, 0);
				}
				if (i % 3 == 0) {
					level.sendParticles(ParticleTypes.CLOUD, true, true, x, start.y + 0.5, z, 2, 0.6, 0.3, 0.6, 0.05);
				}
			}
			// Eight hearts a wave. Half of it is magic, which no armor stops, so nobody survives three waves.
			for (LivingEntity enemy : enemiesNear(player, start, radius)) {
				hit(level, player, enemy, 8.0f);
				enemy.invulnerableTime = 0;
				enemy.hurtServer(level, level.damageSources().magic(), 8.0f);
				Vec3 away = enemy.position().subtract(start);
				Vec3 flat = new Vec3(away.x, 0, away.z);
				Vec3 push = flat.lengthSqr() < 0.01 ? Vec3.ZERO : flat.normalize().scale(1.2);
				enemy.setDeltaMovement(push.x, 0.6, push.z);
				enemy.hurtMarked = true;
			}
			if (SkillConfig.ultimateBreaksBlocks) {
				BlockChanges.queue(new CraterJob(level, player, start, inner, radius, SkillConfig.ultimateRadius,
						SkillConfig.ultimateRegrowSeconds * 20 + index * 40));
			}
		}
	}

	/**
	 * The ultimate's crater, one ring of ground at a time, nearest columns first so it spreads outward:
	 * a bowl 8 blocks deep in the middle, and everything up to 12 blocks above the centre carved away.
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
						int depth = (int) Math.round(8 * shape);
						int height = (int) Math.round(12 * shape);
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
