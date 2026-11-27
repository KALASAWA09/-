package com.kalasawa.sacredsouls.power;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.kalasawa.sacredsouls.SacredSouls;
import com.kalasawa.sacredsouls.soul.Effects;
import com.kalasawa.sacredsouls.soul.LootSources;
import com.kalasawa.sacredsouls.soul.SoulData;
import com.kalasawa.sacredsouls.soul.SoulManager;
import com.kalasawa.sacredsouls.spirit.SpiritHandler;
import com.kalasawa.sacredsouls.title.SoulTitle;
import com.kalasawa.sacredsouls.title.TitleManager;

/** Runs the 50 item powers and the 9 title abilities. All of them are passive or trigger on their own. */
public final class PowerHandler {
	private static final Identifier ROOT_KNOCKBACK = SacredSouls.id("root_knockback");
	private static final Identifier BERSERKER_KNOCKBACK = SacredSouls.id("berserker_knockback");
	/** Most health one hit can give back through lifesteal (1 heart). damageTaken is already after armor. */
	private static final float HEAL_CAP_PER_HIT = 2.0f;
	/** Share of max health the time crystal restores. */
	private static final float REWIND_HEAL = 0.35f;
	private static final Identifier IRON_SKIN_ARMOR = SacredSouls.id("iron_skin_armor");
	private static final Identifier GIANT_BONE_HEALTH = SacredSouls.id("giant_bone_health");
	private static final Identifier SWIFT_HOOF_SPEED = SacredSouls.id("swift_hoof_speed");
	private static final Identifier BLOOD_MOON_DAMAGE = SacredSouls.id("blood_moon_damage");

	/** Short-lived state that does not need to survive a restart. */
	private static final class State {
		final Map<String, Long> cooldowns = new HashMap<>();
		final ArrayDeque<Snapshot> history = new ArrayDeque<>();
		final Set<UUID> bellNear = new HashSet<>();
		Vec3 lastTickPos;
		int stillTicks;
		int sneakStillTicks;
		boolean chameleonHidden;
		double prevFall;
		int airJumps;
		Vec3 lastSecondPos;
		ResourceKey<Level> lastDimension;
		double pendingDistance;
		String disabledPower;
		long disabledUntil;
		long guardianReadyAt;
		UUID comboTarget;
		int comboCount;
		long comboLast;
		long lastHurt;
		int prevFood = -1;
	}

	private record Snapshot(ResourceKey<Level> dimension, Vec3 pos, float health) {
	}

	private record Chain(UUID holder, long until) {
	}

	private record Temp(ServerLevel level, BlockPos pos, long until) {
	}

	private record Summon(Entity entity, long until) {
	}

	private final Map<UUID, State> states = new HashMap<>();
	private final Map<UUID, Chain> chains = new HashMap<>();
	private final Map<String, Long> stealCooldowns = new HashMap<>();
	private final List<Temp> webs = new ArrayList<>();
	private final List<Summon> summons = new ArrayList<>();

	private State state(Player player) {
		return states.computeIfAbsent(player.getUUID(), id -> new State());
	}

	private static long now(Entity entity) {
		return entity.level().getGameTime();
	}

	private boolean ready(Player player, String key) {
		return state(player).cooldowns.getOrDefault(key, 0L) <= now(player);
	}

	private void cooldown(Player player, String key, int ticks) {
		state(player).cooldowns.put(key, now(player) + ticks);
	}

	private static int pick(int level, int a, int b, int c) {
		return level >= 3 ? c : level == 2 ? b : a;
	}

	private static float pick(int level, float a, float b, float c) {
		return level >= 3 ? c : level == 2 ? b : a;
	}

	/** Soul energy 0-10 makes chances a little weaker or stronger (5 = normal). */
	private static boolean roll(ServerPlayer player, SoulData data, float chance) {
		float scaled = chance * (0.8f + 0.04f * data.energy);
		return player.getRandom().nextFloat() < scaled;
	}

	/** Level of a power the player can use right now, or 0. */
	public int level(ServerPlayer player, SoulPower power) {
		SoulData data = SoulManager.get(player);
		if (data.isSoulless()) {
			return 0;
		}
		State state = state(player);
		if (power.id.equals(state.disabledPower) && state.disabledUntil > now(player)) {
			return 0;
		}
		return data.level(power);
	}

	private static SoulTitle title(ServerPlayer player) {
		SoulData data = SoulManager.get(player);
		return data.isSoulless() ? null : data.soulTitle();
	}

	private static boolean enemy(ServerPlayer self, Entity other) {
		return other != self && other instanceof LivingEntity living && living.isAlive() && !self.isAlliedTo(other)
				&& !(other instanceof Player player && (player.isSpectator() || player.isCreative()));
	}

	private static boolean enemyPlayer(ServerPlayer self, Entity other) {
		return other instanceof ServerPlayer && enemy(self, other);
	}

	/** Night in the overworld (other dimensions have no night). */
	private static boolean isNight(Level level) {
		if (level.dimension() != Level.OVERWORLD) {
			return false;
		}
		long t = level.getDayTime() % 24000L;
		return t >= 13000L && t < 23000L;
	}

	/** Daytime in the overworld with open sky above the player. */
	private static boolean inSunlight(Player player) {
		Level level = player.level();
		return level.dimension() == Level.OVERWORLD && !isNight(level) && level.canSeeSky(player.blockPosition().above());
	}

	/** Daylight under open sky, or standing in very bright light (13+). */
	private static boolean inBrightLight(Player player) {
		return inSunlight(player) || player.level().getMaxLocalRawBrightness(player.blockPosition()) >= 13;
	}

	/** Near water: an ocean, beach or river biome. */
	private static boolean byWater(Player player) {
		net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = player.level().getBiome(player.blockPosition());
		return biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_BEACH)
				|| biome.is(net.minecraft.tags.BiomeTags.IS_RIVER);
	}

	/** The block the player is aiming at is dirt, sand, gravel or stone. */
	private static boolean aimingAtEarth(ServerPlayer player) {
		if (!(player.pick(5.0, 0.0f, false) instanceof net.minecraft.world.phys.BlockHitResult hit)
				|| hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
			return false;
		}
		net.minecraft.world.level.block.state.BlockState state = player.level().getBlockState(hit.getBlockPos());
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(net.minecraft.world.level.block.Blocks.GRAVEL)
				|| state.is(BlockTags.BASE_STONE_OVERWORLD);
	}

	/** Standing in water, on mud, or among leaves: roots take hold faster there. */
	private static boolean onRootingGround(ServerPlayer player) {
		net.minecraft.world.level.block.state.BlockState below = player.level().getBlockState(player.getOnPos());
		net.minecraft.world.level.block.state.BlockState at = player.level().getBlockState(player.blockPosition());
		return player.isInWater() || below.is(net.minecraft.world.level.block.Blocks.MUD) || below.is(BlockTags.LEAVES) || at.is(BlockTags.LEAVES);
	}

	private static void setTransientModifier(ServerPlayer player, Identifier id, double amount, boolean on) {
		setModifier(player, Attributes.KNOCKBACK_RESISTANCE, id, amount, AttributeModifier.Operation.ADD_VALUE, on);
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

	// ---------------------------------------------------------------- ticking

	public void tick(MinecraftServer server) {
		long time = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player);
			if (time % 20 == 0) {
				secondPlayer(player, time);
			}
		}
		tickChains(server);
		cleanup(time);
		if (time % 100 == 0) {
			TitleManager.validate(server);
		}
	}

	private void tickPlayer(ServerPlayer player) {
		State state = state(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 pos = player.position();
		boolean moved = state.lastTickPos != null && state.lastTickPos.distanceToSqr(pos) > 0.0004;
		state.lastTickPos = pos;
		state.stillTicks = moved ? 0 : state.stillTicks + 1;
		state.sneakStillTicks = (!moved && player.isShiftKeyDown()) ? state.sneakStillTicks + 1 : 0;
		if (player.onGround()) {
			state.airJumps = 0;
		}

		if (title(player) == SoulTitle.REAPER && player.hasEffect(Effects.get("wither"))) {
			player.removeEffect(Effects.get("wither"));
		}

		// รากไม้โบราณ: stand still for 1.5 seconds, or half a second in water, on mud or in leaves.
		int root = level(player, SoulPower.ANCIENT_ROOT);
		boolean rooted = root > 0 && state.stillTicks >= (onRootingGround(player) ? 10 : 30);
		setTransientModifier(player, ROOT_KNOCKBACK, 1.0, rooted);
		if (rooted && state.stillTicks % 20 == 0) {
			Effects.keep(player, "regeneration", root >= 3 ? 1 : 0, 30);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 0.2, player.getZ(), 3, 0.4, 0.1, 0.4, 0.0);
		}

		// เกล็ดกิ้งก่า: crouch still, then vanish until moving or attacking.
		int chameleon = level(player, SoulPower.CHAMELEON_SCALE);
		if (chameleon > 0 && !state.chameleonHidden && state.sneakStillTicks >= pick(chameleon, 30, 25, 20)) {
			state.chameleonHidden = true;
			level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.3, 0.5, 0.3, 0.01);
		}
		if (state.chameleonHidden) {
			if (chameleon == 0 || moved) {
				state.chameleonHidden = false;
				player.removeEffect(Effects.get("invisibility"));
			} else {
				Effects.keep(player, "invisibility", 0, 10);
				// A faint shimmer so a hidden player can still be spotted up close.
				if (player.tickCount % 10 == 0) {
					level.sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.3, 0.5, 0.3, 0.0);
				}
			}
		}

		// ลูกแก้วแรงโน้มถ่วง: landing after a fall of 4+ blocks.
		double fall = player.fallDistance;
		int gravity = level(player, SoulPower.GRAVITY_ORB);
		if (gravity > 0 && player.onGround() && state.prevFall >= 4.0 && ready(player, "gravity")) {
			cooldown(player, "gravity", 100);
			double radius = pick(gravity, 4, 5, 6);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius), e -> enemy(player, e))) {
				Vec3 pull = player.position().subtract(target.position()).normalize().scale(0.6);
				target.setDeltaMovement(pull.x, 0.2, pull.z);
				target.hurtMarked = true;
				Effects.give(target, "levitation", 0, 30);
			}
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 0.5, player.getZ(), 60, radius / 2, 0.5, radius / 2, 0.05);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 0.6f);
		}
		state.prevFall = fall;

		tickNewPowers(player, level, state);
		SpiritHandler.tick(player, level, level.getGameTime());

		// เขาแพะหิน: ram while sprinting, or by diving onto an enemy from above.
		int horn = level(player, SoulPower.STONE_HORN);
		boolean diving = !player.onGround() && player.fallDistance >= 2.0 && player.getDeltaMovement().y < 0;
		if (horn > 0 && (player.isSprinting() || diving) && ready(player, "horn")) {
			Vec3 look = player.getLookAngle();
			AABB front = diving && !player.isSprinting() ? player.getBoundingBox().move(0, -0.8, 0).inflate(0.4)
					: player.getBoundingBox().move(look.x * 0.6, 0, look.z * 0.6).inflate(0.2);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, front, e -> enemy(player, e))) {
				boolean chained = level(player, SoulPower.SHACKLE) > 0 && isChainedBy(target, player);
				float strength = pick(horn, 1.5f, 2.0f, 2.5f) * (chained ? 2.0f : 1.0f);
				target.knockback(strength, player.getX() - target.getX(), player.getZ() - target.getZ());
				target.hurtMarked = true;
				target.hurtServer(level, level.damageSources().playerAttack(player), chained ? 4.0f : 2.0f);
				Effects.give(target, "slowness", 4, 20);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GOAT_RAM_IMPACT, SoundSource.PLAYERS, 1.0f, 1.0f);
				cooldown(player, "horn", 60);
				break;
			}
		}
	}

	private void secondPlayer(ServerPlayer player, long time) {
		State state = state(player);
		ServerLevel level = (ServerLevel) player.level();
		SoulData data = SoulManager.get(player);

		trackTravel(player, state, data);
		LootSources.checkStats(player);
		SoulManager.updateSoullessHealth(player, data);

		if (!data.slots.isEmpty() && !data.isSoulless()) {
			player.causeFoodExhaustion(0.004f * data.slots.size());
		}
		if (data.slots.contains(SoulPower.TIME_CRYSTAL.id)) {
			state.history.addLast(new Snapshot(level.dimension(), player.position(), player.getHealth()));
			while (state.history.size() > 5) {
				state.history.removeFirst();
			}
		}

		applyTitleBuffs(player, level);
		secondNewPowers(player, level, state);
		SpiritHandler.second(player, level);

		int eye = level(player, SoulPower.SERPENT_EYE);
		if (eye > 0) {
			double radius = pick(eye, 16, 20, 24);
			for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(radius), e -> enemyPlayer(player, e))) {
				if (other.isInvisible() || other.isShiftKeyDown()) {
					Effects.keep(other, "glowing", 0, 30);
				}
			}
		}

		int bell = level(player, SoulPower.SPIRIT_BELL);
		if (bell > 0) {
			double radius = pick(bell, 20, 26, 32);
			Set<UUID> now = new HashSet<>();
			for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(radius), e -> enemyPlayer(player, e))) {
				now.add(other.getUUID());
				if (!state.bellNear.contains(other.getUUID())) {
					Effects.give(other, "glowing", 0, 60);
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0f, 1.0f);
				}
			}
			state.bellNear.clear();
			state.bellNear.addAll(now);
		}

		if (level(player, SoulPower.EMBER_COAL) > 0) {
			Effects.keep(player, "fire_resistance", 0, 40);
		}

		int bat = level(player, SoulPower.BAT_WING);
		if (bat > 0 && (isNight(level) || level.getMaxLocalRawBrightness(player.blockPosition()) <= 7)) {
			Effects.keep(player, "speed", 1, 40);
			Effects.keep(player, "night_vision", 0, 260);
		}

		MutableComponent bar = Component.empty();
		int compass = level(player, SoulPower.HAUNTED_COMPASS);
		if (compass > 0) {
			bar.append(nearestPlayerText(player, level, pick(compass, 200, 400, 800)));
		}
		int miner = level(player, SoulPower.MINER_CORE);
		if (miner > 0) {
			if (compass > 0) {
				bar.append("   ");
			}
			bar.append(nearestOreText(player, level, pick(miner, 6, 8, 10)));
		}
		if (compass > 0 || miner > 0) {
			player.displayClientMessage(bar.withStyle(ChatFormatting.LIGHT_PURPLE), true);
		}

		if (time % 100 == 0) {
			TitleManager.checkConditions(player);
		}
	}

	private void trackTravel(ServerPlayer player, State state, SoulData data) {
		ResourceKey<Level> dimension = player.level().dimension();
		Vec3 pos = player.position();
		if (state.lastSecondPos != null && dimension.equals(state.lastDimension)) {
			double dx = pos.x - state.lastSecondPos.x;
			double dz = pos.z - state.lastSecondPos.z;
			double moved = Math.sqrt(dx * dx + dz * dz);
			if (moved < 100) {
				state.pendingDistance += moved;
			}
		}
		state.lastSecondPos = pos;
		state.lastDimension = dimension;
		int bit = dimension.equals(Level.OVERWORLD) ? 1 : dimension.equals(Level.NETHER) ? 2 : dimension.equals(Level.END) ? 4 : 8;
		if (state.pendingDistance >= 50 || (data.dimensions & bit) == 0) {
			SoulData copy = data.copy();
			copy.distance += (long) state.pendingDistance;
			copy.dimensions |= bit;
			state.pendingDistance -= (long) state.pendingDistance;
			SoulManager.set(player, copy);
		}
	}

	private static String arrow(ServerPlayer player, double x, double z) {
		double angle = Math.toDegrees(Math.atan2(z - player.getZ(), x - player.getX())) - 90.0;
		double relative = Mth.wrapDegrees(angle - player.getYRot());
		String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
		int index = (int) Math.floorMod(Math.round(relative / 45.0), 8);
		return arrows[index];
	}

	private static Component nearestPlayerText(ServerPlayer player, ServerLevel level, double range) {
		ServerPlayer best = null;
		double bestDist = range * range;
		for (ServerPlayer other : level.players()) {
			if (enemyPlayer(player, other)) {
				double dist = other.distanceToSqr(player);
				if (dist < bestDist) {
					bestDist = dist;
					best = other;
				}
			}
		}
		if (best == null) {
			return SoulManager.msg("compass_none");
		}
		return SoulManager.msg("compass", arrow(player, best.getX(), best.getZ()), (int) Math.sqrt(bestDist));
	}

	private static Component nearestOreText(ServerPlayer player, ServerLevel level, int radius) {
		BlockPos center = player.blockPosition();
		BlockPos best = null;
		String name = "";
		double bestDist = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
			BlockState state = level.getBlockState(pos);
			String ore = state.is(BlockTags.DIAMOND_ORES) ? "diamond" : state.is(Blocks.ANCIENT_DEBRIS) ? "debris"
					: state.is(BlockTags.EMERALD_ORES) ? "emerald" : state.is(BlockTags.GOLD_ORES) ? "gold" : null;
			if (ore != null) {
				double dist = pos.distSqr(center);
				if (dist < bestDist) {
					bestDist = dist;
					best = pos.immutable();
					name = ore;
				}
			}
		}
		if (best == null) {
			return SoulManager.msg("ore_none");
		}
		int dy = best.getY() - center.getY();
		Component vertical = dy > 0 ? SoulManager.msg("ore_up", dy) : dy < 0 ? SoulManager.msg("ore_down", -dy) : Component.empty();
		return SoulManager.msg("ore", SoulManager.msg("ore_" + name), arrow(player, best.getX() + 0.5, best.getZ() + 0.5), (int) Math.sqrt(bestDist), vertical);
	}

	private void applyTitleBuffs(ServerPlayer player, ServerLevel level) {
		SoulTitle title = title(player);
		setTransientModifier(player, BERSERKER_KNOCKBACK, 0.5, title == SoulTitle.BERSERKER);
		if (title == null) {
			return;
		}
		switch (title) {
			case WAR_GOD -> Effects.keep(player, "resistance", 0, 40);
			case HEIR -> Effects.keep(player, "health_boost", 0, 40);
			case NAMELESS_SHADOW -> {
				Effects.keep(player, "night_vision", 0, 260);
				Effects.keep(player, "speed", 0, 40);
			}
			case GUARDIAN -> {
				Effects.keep(player, "hero_of_the_village", 0, 40);
				for (ServerPlayer ally : allies(player, level)) {
					Effects.keep(ally, "regeneration", 0, 40);
				}
			}
			case BORDERLESS -> Effects.keep(player, "speed", 0, 40);
			default -> {
			}
		}
	}

	private static List<ServerPlayer> allies(ServerPlayer player, ServerLevel level) {
		return level.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(8.0),
				e -> e != player && player.getTeam() != null && player.isAlliedTo(e));
	}

	// ---------------------------------------------------------------- chains, webs, summons

	private boolean isChainedBy(LivingEntity target, ServerPlayer holder) {
		Chain chain = chains.get(target.getUUID());
		return chain != null && chain.holder().equals(holder.getUUID()) && chain.until() > now(target);
	}

	private void tickChains(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Chain>> it = chains.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Chain> entry = it.next();
			ServerPlayer holder = server.getPlayerList().getPlayer(entry.getValue().holder());
			if (holder == null) {
				it.remove();
				continue;
			}
			ServerLevel level = (ServerLevel) holder.level();
			Entity entity = level.getEntity(entry.getKey());
			if (!(entity instanceof LivingEntity target) || !target.isAlive() || entry.getValue().until() < level.getGameTime()) {
				it.remove();
				continue;
			}
			double dist = target.distanceTo(holder);
			if (dist > 6.0) {
				Vec3 pull = holder.position().subtract(target.position()).normalize().scale(Math.min(1.0, (dist - 6.0) * 0.3 + 0.2));
				target.setDeltaMovement(target.getDeltaMovement().add(pull));
				target.hurtMarked = true;
			}
			if (level.getGameTime() % 5 == 0) {
				Vec3 mid = holder.position().add(target.position()).scale(0.5);
				level.sendParticles(ParticleTypes.CRIT, mid.x, mid.y + 1.0, mid.z, 3, 0.3, 0.3, 0.3, 0.0);
			}
		}
	}

	private void cleanup(long time) {
		webs.removeIf(temp -> {
			if (temp.until() > temp.level().getGameTime()) {
				return false;
			}
			if (temp.level().getBlockState(temp.pos()).is(Blocks.COBWEB)) {
				temp.level().setBlockAndUpdate(temp.pos(), Blocks.AIR.defaultBlockState());
			}
			return true;
		});
		summons.removeIf(summon -> {
			if (summon.entity().isAlive() && summon.until() > summon.entity().level().getGameTime()) {
				return false;
			}
			if (summon.entity().isAlive() && summon.entity().level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.SOUL, summon.entity().getX(), summon.entity().getY() + 0.5, summon.entity().getZ(), 8, 0.3, 0.3, 0.3, 0.02);
			}
			summon.entity().discard();
			return true;
		});
		if (time % 1200 == 0) {
			stealCooldowns.values().removeIf(until -> until < time);
		}
	}

	// ---------------------------------------------------------------- damage hooks

	/** Called from the LivingEntity mixin before damage is applied. */
	public float modifyIncomingDamage(LivingEntity victim, DamageSource source, float amount) {
		if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker
				&& !source.is(net.minecraft.world.damagesource.DamageTypes.THORNS)) {
			int ember = level(attacker, SoulPower.EMBER_COAL);
			if (ember > 0 && victim.isOnFire()) {
				amount *= pick(ember, 1.3f, 1.4f, 1.5f);
			}
			amount = meleeBonus(attacker, victim, amount);
			amount = SpiritHandler.modifyOutgoingDamage(attacker, victim, amount);
			amount = com.kalasawa.sacredsouls.skill.SkillHandler.modifyOutgoing(attacker, amount);
		}
		if (victim instanceof ServerPlayer player && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			amount = damageReduction(player, source, amount);
			amount = SpiritHandler.modifyIncomingDamage(player, source, amount);
		}
		if (victim instanceof ServerPlayer player) {
			int heart = level(player, SoulPower.STONE_HEART);
			if (heart > 0 && amount > 12.0f && ready(player, "stone_heart") && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				amount = 12.0f;
				cooldown(player, "stone_heart", pick(heart, 300, 240, 180));
				((ServerLevel) player.level()).sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(), 15, 0.4, 0.6, 0.4, 0.1);
			}
		}
		return amount;
	}

	public boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		ServerLevel level = (ServerLevel) player.level();
		SoulData data = SoulManager.get(player);
		int glass = level(player, SoulPower.MIRROR_GLASS);
		if (glass > 0 && source.is(DamageTypeTags.IS_PROJECTILE) && source.getEntity() instanceof LivingEntity shooter
				&& shooter != player && roll(player, data, pick(glass, 0.30f, 0.40f, 0.50f))) {
			shooter.hurtServer(level, level.damageSources().thorns(player), amount);
			level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
			return false;
		}
		if (title(player) == SoulTitle.BORDERLESS && source.getEntity() != null && roll(player, data, 0.15f)) {
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5, player.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
			player.displayClientMessage(SoulManager.msg("dodge").withStyle(ChatFormatting.WHITE), true);
			return false;
		}
		return true;
	}

	public void afterDamage(LivingEntity victim, DamageSource source, float damageTaken) {
		if (damageTaken <= 0) {
			return;
		}
		if (victim instanceof ServerPlayer player) {
			onHurt(player, source, damageTaken);
		}
		if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker && attacker != victim
				&& !source.is(net.minecraft.world.damagesource.DamageTypes.THORNS)) {
			onHit(attacker, victim, damageTaken);
		}
	}

	private void onHurt(ServerPlayer player, DamageSource source, float damageTaken) {
		State state = state(player);
		ServerLevel level = (ServerLevel) player.level();
		SoulData data = SoulManager.get(player);
		if (state.chameleonHidden) {
			state.chameleonHidden = false;
			player.removeEffect(Effects.get("invisibility"));
		}
		state.lastHurt = now(player);
		float health = player.getHealth();
		if (health <= 0) {
			return;
		}
		onHurtNewPowers(player, state, level, data, source, damageTaken);

		int shadow = level(player, SoulPower.MIRROR_SHADOW);
		if (shadow > 0 && health < 8.0f && ready(player, "shadow")) {
			cooldown(player, "shadow", pick(shadow, 1200, 1000, 800));
			Effects.give(player, "invisibility", 0, pick(shadow, 40, 60, 80));
			Effects.give(player, "speed", 0, pick(shadow, 40, 60, 80));
			level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.8, 0.4, 0.02);
			level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.8, 0.3, 0.01);
		}

		int crystal = level(player, SoulPower.TIME_CRYSTAL);
		if (crystal > 0 && health < 4.0f && ready(player, "crystal") && state.history.size() >= 4) {
			Snapshot past = state.history.peekFirst();
			if (past != null && past.dimension().equals(level.dimension())) {
				cooldown(player, "crystal", pick(crystal, 6000, 4800, 3600));
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
				player.teleportTo(past.pos().x, past.pos().y, past.pos().z);
				// Only part of the health comes back, and the rewind leaves you dazed for a moment.
				player.setHealth(Math.max(health, player.getMaxHealth() * REWIND_HEAL));
				player.resetFallDistance();
				Effects.give(player, "slowness", 2, 20);
				Effects.give(player, "nausea", 0, 40);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.5f);
				player.displayClientMessage(SoulManager.msg("rewind").withStyle(ChatFormatting.AQUA), true);
			}
		}

		int flute = level(player, SoulPower.WILD_FLUTE);
		if (flute > 0 && health < player.getMaxHealth() / 2 && ready(player, "flute")) {
			cooldown(player, "flute", pick(flute, 1800, 1500, 1200));
			LivingEntity attacker = source.getEntity() instanceof LivingEntity living && living != player ? living : null;
			int count = pick(flute, 2, 2, 3);
			for (int i = 0; i < count; i++) {
				Wolf wolf = EntityType.WOLF.create(level, EntitySpawnReason.MOB_SUMMONED);
				if (wolf == null) {
					continue;
				}
				wolf.setPos(player.getX() + (i - count / 2.0), player.getY(), player.getZ() + 1.0);
				wolf.tame(player);
				wolf.setCustomName(SoulManager.msg("spirit_wolf").withStyle(ChatFormatting.AQUA));
				if (attacker != null) {
					wolf.setTarget(attacker);
				}
				level.addFreshEntity(wolf);
				summons.add(new Summon(wolf, level.getGameTime() + 300));
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.0f, 1.0f);
		}

		if (title(player) == SoulTitle.BERSERKER && source.getEntity() instanceof LivingEntity attacker && attacker != player
				&& !source.is(net.minecraft.world.damagesource.DamageTypes.THORNS) && roll(player, data, 0.20f)) {
			attacker.hurtServer(level, level.damageSources().thorns(player), damageTaken * 0.3f);
		}
	}

	private void onHit(ServerPlayer attacker, LivingEntity victim, float damageTaken) {
		State state = state(attacker);
		ServerLevel level = (ServerLevel) attacker.level();
		SoulData data = SoulManager.get(attacker);
		if (state.chameleonHidden) {
			state.chameleonHidden = false;
			attacker.removeEffect(Effects.get("invisibility"));
		}
		if (data.isSoulless()) {
			return;
		}

		int ice = level(attacker, SoulPower.ETERNAL_ICE);
		if (ice > 0 && roll(attacker, data, pick(ice, 0.15f, 0.20f, 0.25f))) {
			int ticks = pick(ice, 20, 30, 40);
			Effects.give(victim, "slowness", 9, ticks);
			Effects.give(victim, "mining_fatigue", 2, ticks);
			victim.setTicksFrozen(victim.getTicksRequiredToFreeze() + ticks);
			level.sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY() + 1.0, victim.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
		}

		int shackle = level(attacker, SoulPower.SHACKLE);
		if (shackle > 0 && roll(attacker, data, pick(shackle, 0.15f, 0.20f, 0.25f))) {
			chains.put(victim.getUUID(), new Chain(attacker.getUUID(), level.getGameTime() + 80));
			level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 0.8f);
		}

		int fang = level(attacker, SoulPower.SPIDER_FANG);
		if (fang > 0) {
			BlockPos feet = victim.blockPosition();
			if (roll(attacker, data, 0.20f) && level.isEmptyBlock(feet)) {
				level.setBlockAndUpdate(feet, Blocks.COBWEB.defaultBlockState());
				webs.add(new Temp(level, feet, level.getGameTime() + 60));
			}
			if (roll(attacker, data, 0.10f)) {
				Effects.give(victim, "poison", 0, 40);
			}
		}

		onHitNewPowers(attacker, victim, level, data, damageTaken);
		SpiritHandler.onHit(attacker, victim);
		com.kalasawa.sacredsouls.skill.SkillHandler.onHit(attacker, victim, damageTaken);

		int mask = level(attacker, SoulPower.THIEF_MASK);
		if (mask > 0 && roll(attacker, data, pick(mask, 0.10f, 0.15f, 0.20f))) {
			for (MobEffectInstance effect : List.copyOf(victim.getActiveEffects())) {
				if (effect.getEffect().value().isBeneficial() && effect.getDuration() > 20) {
					victim.removeEffect(effect.getEffect());
					attacker.addEffect(new MobEffectInstance(effect.getEffect(), effect.getDuration(), effect.getAmplifier()));
					level.sendParticles(ParticleTypes.WITCH, attacker.getX(), attacker.getY() + 1.0, attacker.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
					break;
				}
			}
		}

		SoulTitle title = data.soulTitle();
		if (title == null) {
			return;
		}
		switch (title) {
			case WAR_GOD -> {
				if (roll(attacker, data, 0.25f)) {
					attacker.heal(Math.min(damageTaken * 0.25f, HEAL_CAP_PER_HIT));
					level.sendParticles(ParticleTypes.HEART, attacker.getX(), attacker.getY() + 1.8, attacker.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
				}
			}
			case NAMELESS_SHADOW -> {
				if (roll(attacker, data, 0.10f)) {
					Effects.give(victim, "blindness", 0, 40);
				}
			}
			case HEIR -> {
				if (victim instanceof ServerPlayer target && roll(attacker, data, 0.15f)) {
					SoulData targetData = SoulManager.get(target);
					if (!targetData.slots.isEmpty()) {
						State targetState = state(target);
						targetState.disabledPower = targetData.slots.get(target.getRandom().nextInt(targetData.slots.size()));
						targetState.disabledUntil = level.getGameTime() + 100;
						SoulPower off = SoulPower.byId(targetState.disabledPower);
						target.displayClientMessage(SoulManager.msg("power_disabled", off == null ? Component.empty() : off.displayName()).withStyle(ChatFormatting.RED), true);
					}
				}
			}
			case DEVOURER -> {
				if (victim instanceof ServerPlayer target && !SoulManager.get(target).isSoulless()) {
					String key = attacker.getUUID() + ">" + target.getUUID();
					if (stealCooldowns.getOrDefault(key, 0L) <= level.getGameTime() && roll(attacker, data, 0.20f)) {
						stealCooldowns.put(key, level.getGameTime() + 12000);
						SoulManager.changeEnergy(target, -1);
						SoulManager.changeEnergy(attacker, 1);
						attacker.displayClientMessage(SoulManager.msg("energy_stolen", target.getName()).withStyle(ChatFormatting.DARK_PURPLE), true);
					}
				}
			}
			default -> {
			}
		}
	}


	// ---------------------------------------------------------------- powers 21-50

	private void tickNewPowers(ServerPlayer player, ServerLevel level, State state) {
		// แม่เหล็กหิน: pull dropped items and experience. Sneak to stop it.
		int magnet = level(player, SoulPower.MAGNET_STONE);
		if (magnet > 0 && !player.isShiftKeyDown() && level.getGameTime() % 2 == 0) {
			AABB area = player.getBoundingBox().inflate(pick(magnet, 4, 6, 8));
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, e -> !e.hasPickUpDelay())) {
				pullTo(item, player);
			}
			for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area, e -> true)) {
				pullTo(orb, player);
			}
		}

		// ขนนกฮูก: hold sneak while falling to glide.
		int owl = level(player, SoulPower.OWL_FEATHER);
		if (owl > 0 && !player.onGround() && !player.getAbilities().flying && player.isShiftKeyDown() && player.getDeltaMovement().y < -0.08) {
			Effects.keep(player, "slow_falling", 0, 10);
			if (owl >= 3) {
				Effects.keep(player, "speed", 0, 10);
			}
		}

		// รอยเท้าน้ำแข็ง: freeze still water around your feet.
		int frost = level(player, SoulPower.FROST_STEP);
		if (frost > 0 && player.onGround()) {
			int radius = pick(frost, 1, 2, 3);
			BlockPos feet = player.blockPosition();
			for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-radius, -1, -radius), feet.offset(radius, -1, radius))) {
				if (pos.distToCenterSqr(player.getX(), pos.getY() + 0.5, player.getZ()) > (radius + 0.5) * (radius + 0.5)) {
					continue;
				}
				if (level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).isSource() && level.getBlockState(pos.above()).isAir()) {
					BlockPos fixed = pos.immutable();
					level.setBlockAndUpdate(fixed, Blocks.FROSTED_ICE.defaultBlockState());
					level.scheduleTick(fixed, Blocks.FROSTED_ICE, Mth.nextInt(player.getRandom(), 60, 120));
				}
			}
		}

		// ดอกชีวิต: eating also heals.
		int food = player.getFoodData().getFoodLevel();
		int bloom = level(player, SoulPower.LIFEBLOOM);
		if (bloom > 0 && state.prevFood >= 0 && food > state.prevFood && ready(player, "lifebloom")) {
			cooldown(player, "lifebloom", 20);
			player.heal(pick(bloom, 2, 4, 6));
			level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.8, player.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
		}
		state.prevFood = food;
	}

	private static void pullTo(Entity entity, ServerPlayer player) {
		Vec3 pull = player.position().add(0, 0.5, 0).subtract(entity.position());
		if (pull.lengthSqr() < 1.0) {
			return;
		}
		entity.setDeltaMovement(pull.normalize().scale(0.35));
		entity.hurtMarked = true;
	}

	private void secondNewPowers(ServerPlayer player, ServerLevel level, State state) {
		int skin = level(player, SoulPower.IRON_SKIN);
		setModifier(player, Attributes.ARMOR, IRON_SKIN_ARMOR, pick(skin, 1.0f, 1.5f, 2.0f), AttributeModifier.Operation.ADD_VALUE, skin > 0);

		int bone = level(player, SoulPower.GIANT_BONE);
		setModifier(player, Attributes.MAX_HEALTH, GIANT_BONE_HEALTH, pick(bone, 2, 4, 6), AttributeModifier.Operation.ADD_VALUE, bone > 0);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}

		int hoof = level(player, SoulPower.SWIFT_HOOF);
		setModifier(player, Attributes.MOVEMENT_SPEED, SWIFT_HOOF_SPEED, pick(hoof, 0.10f, 0.15f, 0.20f), AttributeModifier.Operation.ADD_MULTIPLIED_BASE, hoof > 0);

		int moon = level(player, SoulPower.BLOOD_MOON);
		setModifier(player, Attributes.ATTACK_DAMAGE, BLOOD_MOON_DAMAGE, pick(moon, 1, 2, 3), AttributeModifier.Operation.ADD_VALUE, moon > 0 && (isNight(level) || level.dimension() == Level.NETHER));

		int tide = level(player, SoulPower.TIDE_PEARL);
		if (tide > 0 && player.isInWater()) {
			Effects.keep(player, "water_breathing", 0, 40);
			if (tide >= 2) {
				Effects.keep(player, "dolphins_grace", 0, 40);
			}
			if (tide >= 3) {
				Effects.keep(player, "conduit_power", 0, 40);
			}
		}

		int mole = level(player, SoulPower.MOLE_CLAW);
		if (mole > 0 && ((player.getY() < 50 && !level.canSeeSky(player.blockPosition().above())) || aimingAtEarth(player))) {
			Effects.keep(player, "haste", mole - 1, 40);
		}

		int courage = level(player, SoulPower.COURAGE_BANNER);
		if (courage > 0) {
			AABB area = player.getBoundingBox().inflate(8.0);
			int foes = level.getEntitiesOfClass(ServerPlayer.class, area, e -> enemyPlayer(player, e)).size()
					+ level.getEntitiesOfClass(Mob.class, area, m -> m.isAlive() && m.getTarget() == player).size();
			if (foes >= 2) {
				Effects.keep(player, "resistance", 0, 40);
				if (courage >= 2) {
					Effects.keep(player, "speed", 0, 40);
				}
				if (courage >= 3) {
					Effects.keep(player, "strength", 0, 40);
				}
			}
		}

		int shield = level(player, SoulPower.SHIELD_CRYSTAL);
		if (shield > 0 && player.getAbsorptionAmount() <= 0 && ready(player, "shield_crystal")) {
			cooldown(player, "shield_crystal", pick(shield, 600, 500, 400));
			Effects.give(player, "absorption", 0, 600);
			level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.4, 0.6, 0.4, 0.02);
		}

		int purity = level(player, SoulPower.PURITY_CHARM);
		if (purity > 0 && ready(player, "purity")) {
			for (MobEffectInstance effect : List.copyOf(player.getActiveEffects())) {
				if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
					player.removeEffect(effect.getEffect());
					cooldown(player, "purity", pick(purity, 240, 180, 120));
					level.sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
					break;
				}
			}
		}

		int lotus = level(player, SoulPower.CALM_LOTUS);
		if (lotus > 0 && now(player) - state.lastHurt >= pick(lotus, 200, 160, 120) && player.getHealth() < player.getMaxHealth()) {
			Effects.keep(player, "regeneration", 0, 40);
		}
	}

	/** Extra damage from melee powers (กรงเล็บหมี, ใบมีดตั๊กแตน, ขวานเพชฌฆาต, กลองฟ้าร้อง). */
	private float meleeBonus(ServerPlayer attacker, LivingEntity victim, float amount) {
		float base = amount;
		int claw = level(attacker, SoulPower.BEAR_CLAW);
		if (claw > 0 && attacker.fallDistance > 0 && !attacker.onGround() && !attacker.onClimbable() && !attacker.isInWater()) {
			amount += base * pick(claw, 0.10f, 0.17f, 0.25f);
		}
		int mantis = level(attacker, SoulPower.MANTIS_BLADE);
		if (mantis > 0 && victim.getHealth() >= victim.getMaxHealth()) {
			amount += base * pick(mantis, 0.10f, 0.17f, 0.25f);
		}
		int axe = level(attacker, SoulPower.EXECUTIONER_AXE);
		if (axe > 0 && victim.getHealth() < victim.getMaxHealth() * 0.3f) {
			amount += base * pick(axe, 0.10f, 0.17f, 0.25f);
		}
		int drum = level(attacker, SoulPower.THUNDER_DRUM);
		if (drum > 0 && victim.invulnerableTime <= 10) {
			State state = state(attacker);
			long time = now(attacker);
			if (victim.getUUID().equals(state.comboTarget) && time - state.comboLast <= 60) {
				state.comboCount++;
			} else {
				state.comboTarget = victim.getUUID();
				state.comboCount = 1;
			}
			state.comboLast = time;
			if (state.comboCount >= 4) {
				state.comboCount = 0;
				amount += pick(drum, 3.0f, 4.0f, 5.0f);
				ServerLevel level = (ServerLevel) attacker.level();
				level.sendParticles(ParticleTypes.SONIC_BOOM, victim.getX(), victim.getY() + 1.0, victim.getZ(), 1, 0, 0, 0, 0);
				level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5f, 1.6f);
			}
		}
		return amount;
	}

	/** Damage cuts (กระดองเต่า, เศษออบซิเดียน, ขนนกฮูก). */
	private float damageReduction(ServerPlayer player, DamageSource source, float amount) {
		int turtle = level(player, SoulPower.TURTLE_PLATE);
		if (turtle > 0 && player.isShiftKeyDown()) {
			amount *= 1.0f - pick(turtle, 0.08f, 0.12f, 0.18f);
		}
		int obsidian = level(player, SoulPower.OBSIDIAN_SHARD);
		if (obsidian > 0 && source.is(DamageTypeTags.IS_EXPLOSION)) {
			amount *= 1.0f - pick(obsidian, 0.15f, 0.25f, 0.35f);
		}
		int owl = level(player, SoulPower.OWL_FEATHER);
		if (owl >= 2 && source.is(DamageTypeTags.IS_FALL)) {
			amount *= owl >= 3 ? 0.0f : 0.5f;
		}
		return amount;
	}

	private void onHurtNewPowers(ServerPlayer player, State state, ServerLevel level, SoulData data, DamageSource source, float damageTaken) {
		LivingEntity melee = source.getEntity() instanceof LivingEntity living && living != player && source.getDirectEntity() == living ? living : null;
		boolean thorns = source.is(net.minecraft.world.damagesource.DamageTypes.THORNS);

		int vine = level(player, SoulPower.THORN_VINE);
		if (vine > 0 && melee != null && !thorns) {
			melee.hurtServer(level, level.damageSources().thorns(player), pick(vine, 2.0f, 3.0f, 4.0f));
		}

		int ender = level(player, SoulPower.ENDER_SHARD);
		if (ender > 0 && melee != null && ready(player, "ender_shard") && roll(player, data, pick(ender, 0.10f, 0.15f, 0.20f))) {
			Vec3 look = melee.getLookAngle();
			Vec3 behind = melee.position().subtract(look.x * 1.5, 0, look.z * 1.5);
			AABB box = player.getBoundingBox().move(behind.subtract(player.position()));
			if (level.noCollision(player, box)) {
				cooldown(player, "ender_shard", 160);
				level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.3, 0.6, 0.3, 0.1);
				player.teleportTo(behind.x, behind.y, behind.z);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.2f);
			}
		}

		int mark = level(player, SoulPower.HUNTER_MARK);
		if (mark > 0 && source.getEntity() instanceof ServerPlayer hunter && hunter != player) {
			Effects.give(hunter, "glowing", 0, pick(mark, 60, 100, 140));
		}

		int quake = level(player, SoulPower.QUAKE_STONE);
		if (quake > 0 && damageTaken >= 6.0f && ready(player, "quake")) {
			cooldown(player, "quake", 200);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(pick(quake, 3, 4, 5)), e -> enemy(player, e))) {
				target.knockback(1.2, player.getX() - target.getX(), player.getZ() - target.getZ());
				target.hurtMarked = true;
			}
			level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.2, player.getZ(), 6, 1.5, 0.1, 1.5, 0.0);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7f, 0.7f);
		}
	}

	private void onHitNewPowers(ServerPlayer attacker, LivingEntity victim, ServerLevel level, SoulData data, float damageTaken) {
		int tooth = level(attacker, SoulPower.VAMPIRE_TOOTH);
		if (tooth > 0) {
			attacker.heal(Math.min(damageTaken * pick(tooth, 0.06f, 0.09f, 0.12f), HEAL_CAP_PER_HIT));
		}

		int storm = level(attacker, SoulPower.STORM_ROD);
		if (storm > 0 && (level.isRainingAt(attacker.blockPosition().above()) || byWater(attacker)) && ready(attacker, "storm_rod")
				&& roll(attacker, data, pick(storm, 0.15f, 0.20f, 0.25f))) {
			cooldown(attacker, "storm_rod", 60);
			LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.setPos(victim.getX(), victim.getY(), victim.getZ());
				bolt.setVisualOnly(true);
				level.addFreshEntity(bolt);
			}
			victim.hurtServer(level, level.damageSources().lightningBolt(), pick(storm, 4.0f, 5.0f, 6.0f));
		}

		int sun = level(attacker, SoulPower.SUN_SHARD);
		if (sun > 0 && inBrightLight(attacker)) {
			victim.igniteForSeconds(pick(sun, 2.0f, 3.0f, 4.0f));
		}
	}

	// ---------------------------------------------------------------- death

	/** Returns false to cancel the death (ผู้พิทักษ์). */
	public boolean allowDeath(LivingEntity entity, DamageSource source) {
		if (entity instanceof ServerPlayer spiritOwner && !SpiritHandler.allowDeath(spiritOwner)) {
			return false;
		}
		if (!(entity instanceof ServerPlayer player) || title(player) != SoulTitle.GUARDIAN) {
			return true;
		}
		State state = state(player);
		long time = now(player);
		if (state.guardianReadyAt > time) {
			return true;
		}
		state.guardianReadyAt = time + 24000;
		player.setHealth(1.0f);
		player.removeAllEffects();
		Effects.give(player, "regeneration", 1, 900);
		Effects.give(player, "absorption", 1, 100);
		Effects.give(player, "fire_resistance", 0, 800);
		player.level().broadcastEntityEvent(player, (byte) 35);
		player.displayClientMessage(SoulManager.msg("guardian_saved").withStyle(ChatFormatting.GOLD), false);
		return false;
	}

	public void afterDeath(LivingEntity entity, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer killer && killer != entity) {
			SpiritHandler.onKill(killer, entity);
		}
		// แมลงทองคำ: extra experience from mobs you kill.
		if (source.getEntity() instanceof ServerPlayer hunter && !(entity instanceof Player) && entity.level() instanceof ServerLevel mobLevel) {
			int scarab = level(hunter, SoulPower.GOLDEN_SCARAB);
			if (scarab > 0) {
				ExperienceOrb.award(mobLevel, entity.position(), pick(scarab, 2, 4, 6));
			}
		}
		if (!(entity instanceof ServerPlayer victim)) {
			return;
		}
		ServerLevel level = (ServerLevel) victim.level();
		// ขนอีกา: a player dying nearby feeds you a shield.
		for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, victim.getBoundingBox().inflate(16.0), e -> e != victim)) {
			int crow = level(other, SoulPower.CROW_FEATHER);
			if (crow > 0) {
				Effects.give(other, "absorption", crow - 1, 600);
				level.sendParticles(ParticleTypes.SOUL, other.getX(), other.getY() + 1.0, other.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
			}
		}
		ServerPlayer killer = source.getEntity() instanceof ServerPlayer p && p != victim ? p : null;
		if (killer != null) {
			SoulManager.onPlayerKill(killer, victim);
			if (title(killer) == SoulTitle.REAPER) {
				killer.heal(10.0f);
			}
		} else {
			SoulData data = SoulManager.get(victim).copy();
			data.killStreak = 0;
			SoulManager.set(victim, data);
		}
		states.remove(victim.getUUID());
		chains.remove(victim.getUUID());
	}

	// ---------------------------------------------------------------- misc

	/** Double jump request from the client (ขนนกลม). */
	public void onAirJump(ServerPlayer player) {
		int feather = level(player, SoulPower.WIND_FEATHER);
		if (feather <= 0 || player.onGround() || player.getAbilities().flying) {
			return;
		}
		State state = state(player);
		if (state.airJumps >= maxAirJumps(player.level(), player, feather, level(player, SoulPower.BAT_WING) > 0)) {
			return;
		}
		state.airJumps++;
		player.resetFallDistance();
		((ServerLevel) player.level()).sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 8, 0.3, 0.05, 0.3, 0.02);
	}

	/** Shared with the client so both sides agree on how many air jumps are allowed. */
	public static int maxAirJumps(Level level, Player player, int featherLevel, boolean hasBatWing) {
		int jumps = featherLevel >= 3 ? 2 : 1;
		if (hasBatWing && level.getMaxLocalRawBrightness(player.blockPosition()) <= 7) {
			jumps++;
		}
		return jumps;
	}

	public void onPowerRemoved(ServerPlayer player, SoulPower power) {
		State state = state(player);
		if (power == SoulPower.CHAMELEON_SCALE && state.chameleonHidden) {
			state.chameleonHidden = false;
			player.removeEffect(Effects.get("invisibility"));
		}
		if (power == SoulPower.ANCIENT_ROOT) {
			setTransientModifier(player, ROOT_KNOCKBACK, 0, false);
		}
	}

	public void onLeave(ServerPlayer player) {
		states.remove(player.getUUID());
	}
}
