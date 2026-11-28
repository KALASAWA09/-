package com.kalasawa.sacredsouls.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import com.kalasawa.sacredsouls.soul.SoulManager;

/**
 * The staging of the Spirit King ultimate on the server: everything that should feel like the end of the world
 * for whoever is near. Huge textured magic circles (display entities with the mod's own textures) turn in the sky
 * and on the ground, a storm rolls in, the boss bar darkens the sky and brings fog, a funeral bell tolls three
 * times, the ground cracks open in glowing lines, rubble lifts into the air, black dragons coil round the whole
 * field, dark red lightning walks it, and then everything goes silent before the blow. Everything is sized to the
 * blast radius, so what you see is what will be destroyed. Damage and the crater stay in SkillHandler.
 *
 * Display entities are driven with vanilla commands, so their smooth interpolation works on every client.
 */
public final class KingShow {
	/** Every entity the show spawns carries this tag, so leftovers can always be removed. */
	public static final String TAG = "sacredsouls_king";
	/** Ticks of silence before the blast. Matches the client's pause and the charge sound. */
	public static final int PAUSE = 32;
	private static int counter;

	private final ServerLevel level;
	private final ServerPlayer caster;
	private final Vec3 start;
	private final int radius;
	private final int castTicks;
	private final String tag;
	private final ServerBossEvent bar;
	private final double circle;
	/** How high the sky circle hangs, and how much bigger than the original 45-block circle everything is. */
	private final double skyHeight;
	private final double k;
	/** Size of the dragons: their head model's scale is 2.6 times this. */
	private final double dragonSize;
	/** The mushroom cloud's size against the original 70 blocks. */
	private final double cloud;
	private final List<double[]> cracks = new ArrayList<>();
	private final boolean storm;
	private boolean wasRaining;
	private boolean wasThundering;
	private int debrisCount;

	public KingShow(ServerLevel level, ServerPlayer caster, Vec3 start, int radius, int castTicks) {
		this.level = level;
		this.caster = caster;
		this.start = start;
		this.radius = radius;
		this.castTicks = castTicks;
		this.tag = "ssk" + (++counter) + "_" + (level.getGameTime() % 100000);
		this.circle = circleFor(radius);
		this.skyHeight = skyFor(radius);
		this.k = circle / 45.0;
		this.dragonSize = Mth.clamp(radius / 40.0, 1.2, 4.0);
		this.cloud = Mth.clamp(radius / 100.0, 1.0, 2.0);
		this.storm = SkillConfig.ultimateStorm && level.dimension() == Level.OVERWORLD;
		this.bar = new ServerBossEvent(SoulManager.msg("king_bar", caster.getName()).withStyle(ChatFormatting.DARK_RED),
				BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
		this.bar.setDarkenScreen(true);
		this.bar.setCreateWorldFog(true);
		for (int i = 0; i < 12; i++) {
			cracks.add(new double[] {i * Math.PI * 2 / 12 + level.random.nextDouble() * 0.3, 0});
		}
	}

	/** Half-width of the sky circle for a blast radius; the client uses the same numbers. */
	public static double circleFor(int radius) {
		return Mth.clamp(radius * 0.55, 22, 100);
	}

	/** Height of the sky circle above the caster for a blast radius. */
	public static double skyFor(int radius) {
		return Mth.clamp(radius * 0.45, 45, 80);
	}

	// ---------------------------------------------------------------- timeline

	/** Called every tick of the cast, with the tick number. */
	public void cast(int age) {
		double progress = (double) age / castTicks;
		int pauseStart = castTicks - PAUSE;
		expire();
		if (age == 0) {
			begin();
		}
		if (age % 20 == 0) {
			watchers();
		}
		if (age == 4) {
			summonDragons();
		}
		if (age > 4 && age % DRAGON_STEP == 0 && age < pauseStart + PAUSE / 2) {
			moveDragons(age, pauseStart);
		}
		tentacles(age, pauseStart);
		bar.setProgress((float) Mth.clamp(1.0 - progress, 0, 1));
		// The circles turn, slowly and smoothly.
		if (age > 0 && age < pauseStart && age % 10 == 0) {
			double grow = Math.min(1.0, age / 40.0);
			double turn = age * 0.02;
			transform("sky", flat(turn), circle * 2 * grow, 10);
			transform("runes", flat(-turn * 1.6), circle * 1.45 * grow, 10);
			// The ground circle's outer ring is the edge of the blast.
			transform("ground", flat(turn * 0.5), radius * 2.05 * grow, 10);
		}
		// Three tolls of the funeral bell.
		if (age == 0 || age == castTicks / 3 || age == castTicks * 2 / 3) {
			everyone(ModSounds.KING_TOLL, 1.0f, 1.0f, radius * 3);
		}
		if (age == 2) {
			everyone(ModSounds.KING_DRONE, 1.0f, 1.0f, radius * 3);
		}
		if (age >= 20 && age < pauseStart) {
			crack(progress);
		}
		if (age == 30) {
			raiseDebris(pauseStart - 30);
		}
		// Dark red lightning walks the whole field, more and more often, out to the very edge of the blast.
		int gap = Math.max(2, (int) (14 - 12 * progress));
		if (age > 10 && age < pauseStart && age % gap == 0) {
			int strikes = 1 + (progress > 0.6 ? 1 : 0) + (progress > 0.85 ? 1 : 0);
			for (int n = 0; n < strikes; n++) {
				double a = level.random.nextDouble() * Math.PI * 2;
				double r = radius * (0.08 + level.random.nextDouble() * 0.92);
				bolt(start.add(Math.cos(a) * r, 0, Math.sin(a) * r));
			}
		}
		// The eight elements take their seats on the circle, light pours down from them, and they feed the core.
		if (age == ORBS_AT) {
			summonElements();
		}
		if (age > ORBS_AT && age < pauseStart && age % 10 == 0) {
			moveElements(age, progress);
		}
		if (age == pauseStart) {
			silence();
			converge();
		}
		if (age == pauseStart + PAUSE / 2) {
			dropCore();
		}
	}

	/** Called every tick after the blast, with the step since the blast. */
	public void after(int step) {
		expire();
		if (step == 0) {
			blast();
		}
		// The storm keeps striking the ruins for a while.
		if (step > 10 && step < 140 && step % 6 == 0) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double r = radius * (0.2 + level.random.nextDouble() * 0.8);
			bolt(start.add(Math.cos(a) * r, 0, Math.sin(a) * r));
		}
		for (int w = 0; w < 3; w++) {
			if (step == w * 22) {
				wave(w);
			}
			if (step == w * 22 + 16) {
				kill("wave" + w);
			}
		}
		mushroom(step);
		if (step == 2) {
			// The beam widens and fades.
			blockTransform("beam", 0.05, 90 * k, 18);
			blockTransform("beam_core", 0.05, 90 * k, 12);
		}
		if (step == 20) {
			kill("beam");
			kill("beam_core");
		}
		lightWallAfter(step);
		if (step == 40) {
			bar.setName(SoulManager.msg("king_bar_after").withStyle(ChatFormatting.DARK_GRAY));
			bar.setProgress(0);
		}
		if (step == 60) {
			transform("ground", flat(0.3), 0.01, 140);
		}
		if (step == 120) {
			bar.removeAllPlayers();
			bar.setDarkenScreen(false);
		}
		if (step == 200) {
			kill("ground");
			endStorm();
		}
	}

	/** Called when the cast is cancelled or the caster is gone. */
	public void stop() {
		bar.removeAllPlayers();
		killAll();
		endStorm();
		for (ServerPlayer other : level.players()) {
			other.connection.send(new ClientboundStopSoundPacket(BuiltInRegistries.SOUND_EVENT.getKey(ModSounds.KING_DRONE), SoundSource.HOSTILE));
		}
	}

	public void finish() {
		bar.removeAllPlayers();
		killAll();
		endStorm();
	}

	// ---------------------------------------------------------------- the beats

	private void begin() {
		Component title = SoulManager.msg("king_title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
		Component subtitle = SoulManager.msg("king_subtitle", caster.getName()).withStyle(ChatFormatting.GRAY);
		for (ServerPlayer other : near(radius * 3 + 64)) {
			other.connection.send(new ClientboundSetTitlesAnimationPacket(20, 60, 30));
			other.connection.send(new ClientboundSetTitleTextPacket(title));
			other.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		}
		if (storm) {
			wasRaining = level.isRaining();
			wasThundering = level.isThundering();
			level.setWeatherParameters(0, castTicks + 400, true, true);
		}
		Vec3 sky = start.add(0, skyHeight, 0);
		summonItem("sky", "king_circle", sky, flat(0), 0.1);
		summonItem("runes", "king_runes", sky.add(0, -1.5, 0), flat(0), 0.1);
		summonItem("ground", "king_ground", start.add(0, 0.08, 0), flat(0), 0.1);
	}

	private void watchers() {
		for (ServerPlayer other : level.players()) {
			boolean inside = other.position().distanceTo(start) <= radius * 2 + 32;
			if (inside && !bar.getPlayers().contains(other)) {
				bar.addPlayer(other);
			} else if (!inside && bar.getPlayers().contains(other)) {
				bar.removePlayer(other);
			}
		}
	}

	/** Glowing cracks spread from the centre along jagged lines. */
	private void crack(double progress) {
		double reach = radius * 0.95 * Math.min(1.0, progress / 0.85);
		for (double[] crack : cracks) {
			while (crack[1] < reach) {
				crack[1] += 1.0;
				crack[0] += (level.random.nextDouble() - 0.5) * 0.08;
				double r = crack[1];
				BlockPos column = BlockPos.containing(start.x + Math.cos(crack[0]) * r, start.y, start.z + Math.sin(crack[0]) * r);
				if (!level.isLoaded(column)) {
					continue;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
				if (Math.abs(top.getY() - start.y) > 24) {
					continue;
				}
				BlockState next = r < reach * 0.5 || level.random.nextFloat() < 0.4f ? Blocks.MAGMA_BLOCK.defaultBlockState()
						: Blocks.BLACKSTONE.defaultBlockState();
				BlockChanges.replace(level, caster, top, next, true, SkillConfig.ultimateRegrowSeconds * 20 + castTicks);
				if (level.random.nextFloat() < 0.15f) {
					level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA, top.getX() + 0.5, top.getY() + 1.0, top.getZ() + 0.5, 1, 0.2, 0, 0.2, 0);
				}
			}
		}
	}

	/** Lumps of the ground lift slowly into the air and turn, as if gravity were failing. */
	private void raiseDebris(int ticks) {
		for (int i = 0; i < 44; i++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double r = 4 + level.random.nextDouble() * radius * 0.7;
			BlockPos column = BlockPos.containing(start.x + Math.cos(a) * r, start.y, start.z + Math.sin(a) * r);
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
			BlockState state = level.getBlockState(top);
			String block = state.isAir() || state.hasBlockEntity() || !state.isCollisionShapeFullBlock(level, top)
					? "minecraft:cobblestone" : BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
			String name = "debris" + (debrisCount++);
			float size = (float) ((0.6f + level.random.nextFloat() * 1.2f) * Math.max(1.0, k));
			cmd(String.format(Locale.ROOT,
					"summon minecraft:block_display %.2f %.2f %.2f {Tags:[\"%s\",\"%s\",\"%s\"],block_state:{Name:\"%s\"},view_range:6f,"
							+ "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[%.2ff,0f,%.2ff],scale:[%.2ff,%.2ff,%.2ff]}}",
					top.getX() + 0.5, top.getY() + 1.0, top.getZ() + 0.5, TAG, tag, tag + "_" + name, block, -size / 2, -size / 2, size, size, size));
			double rise = (6 + level.random.nextDouble() * 18) * k;
			float[] q = quat(level.random.nextDouble() * 2 - 1, level.random.nextDouble() * 2 - 1, level.random.nextDouble() * 2 - 1,
					level.random.nextDouble() * Math.PI * 1.5);
			// Start the climb a moment later, once the clients have the entity.
			pendingDebris.add(new Object[] {name, rise, q, size, ticks});
		}
	}

	private final List<Object[]> pendingDebris = new ArrayList<>();

	/** Runs queued work that must wait a tick for clients to know an entity. */
	public void late() {
		for (Object[] d : pendingDebris) {
			String name = (String) d[0];
			double rise = (double) d[1];
			float[] q = (float[]) d[2];
			float size = (float) d[3];
			int ticks = (int) d[4];
			cmd(String.format(Locale.ROOT,
					"data merge entity @e[tag=%s_%s,limit=1] {start_interpolation:0,interpolation_duration:%d,"
							+ "transformation:{left_rotation:[%.4ff,%.4ff,%.4ff,%.4ff],right_rotation:[0f,0f,0f,1f],translation:[%.2ff,%.2ff,%.2ff],scale:[%.2ff,%.2ff,%.2ff]}}",
					tag, name, ticks, q[0], q[1], q[2], q[3], -size / 2, rise, -size / 2, size, size, size));
		}
		pendingDebris.clear();
	}

	/**
	 * A bolt of dark red lightning: one of the mod's jagged bolt models, turned to face whoever looks, standing on
	 * the ground for a few ticks with a crack of thunder and a burst of red sparks. No vanilla bolt, which is white.
	 */
	private void bolt(Vec3 at) {
		BlockPos column = BlockPos.containing(at);
		if (!level.isLoaded(column)) {
			return;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
		Vec3 ground = new Vec3(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
		double height = skyHeight + 10 + level.random.nextDouble() * 15;
		double sy = height / 2.81;
		double width = 4 + level.random.nextDouble() * 3;
		String name = "bolt" + (boltCount++);
		cmd(String.format(Locale.ROOT,
				"summon minecraft:item_display %.3f %.3f %.3f {Tags:[\"%s\",\"%s\",\"%s_%s\"],"
						+ "item:{id:\"minecraft:paper\",count:1,components:{\"minecraft:item_model\":\"sacredsouls:king_bolt%d\"}},"
						+ "item_display:\"none\",billboard:\"vertical\",brightness:{sky:15,block:15},view_range:12f,transformation:%s}",
				ground.x, ground.y, ground.z, TAG, tag, tag, name, level.random.nextInt(3),
				transformation(yaw(0), 0, 1.31 * sy, 0, width, sy, width)));
		expiring.add(new Object[] {name, level.getGameTime() + 4 + level.random.nextInt(3)});
		level.sendParticles(RED_SPARK, ground.x, ground.y + 1, ground.z, 40, 1.5, 1.0, 1.5, 0.2);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA, ground.x, ground.y + 0.5, ground.z, 8, 0.8, 0.2, 0.8, 0);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, ground.x, ground.y + 1, ground.z, 10, 0.8, 0.8, 0.8, 0.05);
		level.playSound(null, ground.x, ground.y, ground.z, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
				8.0f, 0.6f + level.random.nextFloat() * 0.3f);
		level.playSound(null, ground.x, ground.y, ground.z, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER,
				2.0f, 0.5f);
	}

	private static final net.minecraft.core.particles.ParticleOptions RED_SPARK =
			new net.minecraft.core.particles.DustParticleOptions(0xD01018, 3.0f);
	private int boltCount;
	/** Short-lived entities and the game time they go. */
	private final List<Object[]> expiring = new ArrayList<>();

	private void expire() {
		long now = level.getGameTime();
		for (java.util.Iterator<Object[]> it = expiring.iterator(); it.hasNext();) {
			Object[] e = it.next();
			if ((long) e[1] <= now) {
				kill((String) e[0]);
				it.remove();
			}
		}
	}

	// ---------------------------------------------------------------- the dragons

	private static final int DRAGONS = 3;
	private static final int SEGMENTS = 16;
	private static final int DRAGON_STEP = 3;

	/** Each dragon's own ring: how far out (of the radius), how high (of the sky circle) and which way round. */
	private static final double[][] DRAGON_RINGS = {{0.42, 0.42, 1}, {0.68, 0.62, -1}, {0.92, 0.28, 1}};

	private static String segmentModel(int i) {
		if (i == SEGMENTS - 1) {
			return "king_dragon_tail";
		}
		return i == 1 || i == 9 ? "king_dragon_legs" : "king_dragon_body";
	}

	private double segmentSize(int i) {
		return dragonSize * (2.3 - 1.3 * i / (SEGMENTS - 1.0));
	}

	/** Black dragons rise out of the ground circle, one on each of three rings. */
	private void summonDragons() {
		for (int d = 0; d < DRAGONS; d++) {
			summonModel("dragon" + d + "h", "king_dragon_head", start, yaw(0), 0, -3, 0, 0.01, 0.01, 0.01, 15);
			for (int i = 0; i < SEGMENTS; i++) {
				summonModel("dragon" + d + "s" + i, segmentModel(i), start, yaw(0), 0, -3, 0, 0.01, 0.01, 0.01, 15);
			}
		}
		everyone(net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 1.0f, 0.4f, radius * 3);
	}

	/**
	 * Where dragon d's body is at a distance s along its path (s grows with time at the head). They coil round the
	 * field and rise and fall; at the silence their rings close in and they are drawn up into the core.
	 */
	private Vec3 dragonAt(int d, double s, int age, int pauseStart) {
		double[] ring = DRAGON_RINGS[d];
		double r = Math.max(8, radius * ring[0]);
		double h = skyHeight * ring[1];
		double into = age < pauseStart ? 0 : Math.min(1.0, (age - pauseStart) / (PAUSE / 2.0));
		r *= 1 - into * 0.97;
		h += (skyHeight - h) * into;
		double a = d * Math.PI * 2 / DRAGONS + ring[2] * s / Math.max(8, radius * ring[0]);
		double wave = 5 * dragonSize * Math.sin(s / (12 * dragonSize) + d);
		return start.add(Math.cos(a) * r, h + wave * (1 - into), Math.sin(a) * r);
	}

	private void moveDragons(int age, int pauseStart) {
		double grow = Math.min(1.0, (age - 4) / 40.0);
		double head = age * 1.8 * Math.max(1.0, dragonSize / 2);
		for (int d = 0; d < DRAGONS; d++) {
			double dist = 0;
			for (int i = -1; i < SEGMENTS; i++) {
				double size = i < 0 ? dragonSize * 2.6 : segmentSize(i);
				double s = head - dist;
				Vec3 at = dragonAt(d, s, age, pauseStart);
				Vec3 dir = dragonAt(d, s + 0.5, age, pauseStart).subtract(dragonAt(d, s - 0.5, age, pauseStart));
				double flatLen = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
				float[] q = FxStage.mul(FxStage.yaw(-Math.atan2(dir.z, dir.x)), FxStage.axis(0, 0, 1, Math.atan2(dir.y, flatLen)));
				Vec3 t = at.subtract(start);
				double scale = size * grow;
				move(i < 0 ? "dragon" + d + "h" : "dragon" + d + "s" + i, q, t.x, t.y, t.z, scale, scale, scale, DRAGON_STEP);
				dist += i < 0 ? dragonSize * 2.0 : size * 0.75;
			}
		}
		if (age % 60 == 0) {
			everyone(net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 0.7f, 0.5f + level.random.nextFloat() * 0.2f, radius * 3);
		}
	}

	private void killDragons() {
		for (int d = 0; d < DRAGONS; d++) {
			kill("dragon" + d + "h");
			for (int i = 0; i < SEGMENTS; i++) {
				kill("dragon" + d + "s" + i);
			}
		}
	}

	// ---------------------------------------------------------------- the tentacles

	private static final int TENTACLES = 14;
	private static final int TENTACLES_AT = 50;
	private static final int SWAY_STEP = 20;
	/** Each tentacle: x, y, z of its ground spot, its size, a phase, and the tick it breaks out. */
	private final List<double[]> tentacles = new ArrayList<>();

	/** Black tentacles break out of the ground round the edge of the circle, one after another. */
	private void tentacles(int age, int pauseStart) {
		if (age == TENTACLES_AT) {
			for (int i = 0; i < TENTACLES; i++) {
				// Spaced evenly round the inside of the ground circle's outer ring.
				double a = i * Math.PI * 2 / TENTACLES + (level.random.nextDouble() - 0.5) * 0.12;
				double r = radius * (0.84 + level.random.nextDouble() * 0.06);
				BlockPos column = BlockPos.containing(start.x + Math.cos(a) * r, start.y, start.z + Math.sin(a) * r);
				if (!level.isLoaded(column)) {
					continue;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				double size = (8 + level.random.nextDouble() * 4) * Math.max(1.0, k * 0.8);
				tentacles.add(new double[] {top.getX() + 0.5, top.getY(), top.getZ() + 0.5, size, level.random.nextDouble() * Math.PI * 2,
						TENTACLES_AT + 4 + i * 4});
			}
		}
		for (int i = 0; i < tentacles.size(); i++) {
			double[] t = tentacles.get(i);
			int out = (int) t[5];
			String name = "tentacle" + i;
			if (age == out - 3) {
				summonModel(name, "king_tentacle" + (i % 2), new Vec3(t[0], t[1], t[2]), yaw(t[4]), 0, 0, 0, 0.01, 0.01, 0.01, 15);
			}
			if (age == out) {
				Vec3 at = new Vec3(t[0], t[1] + 1, t[2]);
				level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(0x8B0000, 3.0f), at.x, at.y, at.z, 60, 2, 1, 2, 0.1);
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 30, 2, 0.5, 2, 0.08);
				level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 4.0f, 0.5f);
				pose(name, t, 1.0, 0, 14);
			} else if (age > out + 14 && age < pauseStart && (age - out) % SWAY_STEP == 0) {
				pose(name, t, 1.0, (age - out) / (double) SWAY_STEP, SWAY_STEP);
			}
			if (age == pauseStart) {
				// They sink back into the ground at the silence.
				pose(name, t, 0.01, 0, PAUSE / 2);
			}
			if (age == pauseStart + PAUSE / 2) {
				kill(name);
			}
		}
	}

	/** A tentacle grown to a share of its size and leaning a little, its base kept on the ground. */
	private void pose(String name, double[] t, double grow, double swing, int ticks) {
		double size = t[3] * grow;
		double lean = swing == 0 ? 0 : 0.12 * Math.sin(swing * 1.7 + t[4]);
		float[] q = FxStage.mul(FxStage.axis(Math.cos(t[4] + swing), 0, Math.sin(t[4] + swing), lean), yaw(t[4] + swing * 0.6));
		// The model's base sits 1.5 sizes below its origin; turn that offset with the model so the base stays put.
		double[] up = rotate(q, 0, 1.5 * size, 0);
		move(name, q, up[0], up[1], up[2], size, size, size, ticks);
	}

	private static double[] rotate(float[] q, double x, double y, double z) {
		double qx = q[0], qy = q[1], qz = q[2], qw = q[3];
		double tx = 2 * (qy * z - qz * y), ty = 2 * (qz * x - qx * z), tz = 2 * (qx * y - qy * x);
		return new double[] {x + qw * tx + (qy * tz - qz * ty), y + qw * ty + (qz * tx - qx * tz), z + qw * tz + (qx * ty - qy * tx)};
	}

	// ---------------------------------------------------------------- the wall of light

	private static final int WALL_PANELS = 48;
	private final List<String> wallPanels = new ArrayList<>();

	private double wallHeight() {
		return 220 * Math.max(1.0, k * 0.6);
	}

	/** Width of one panel: enough that neighbours overlap a little, so the wall reads as one round sheet. */
	private double panelWidth() {
		return 2 * Math.PI * radius / WALL_PANELS * 1.06;
	}

	/** Where the core hits the ground, one solid round wall of light bursts up the whole edge of the ground circle. */
	private void lightWall() {
		for (int i = 0; i < WALL_PANELS; i++) {
			double a = i * Math.PI * 2 / WALL_PANELS;
			BlockPos column = BlockPos.containing(start.x + Math.cos(a) * radius, start.y, start.z + Math.sin(a) * radius);
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			Vec3 at = new Vec3(start.x + Math.cos(a) * radius, top.getY(), start.z + Math.sin(a) * radius);
			String name = "wall" + i;
			summonModel(name, "king_wall", at, panelTurn(a), 0, 0, 0, panelWidth(), 0.01, 1, 15);
			wallPanels.add(name);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 12, 2, 2, 2, 0.3);
		}
	}

	/** Each panel lies along the circle, its face towards the centre. */
	private static float[] panelTurn(double a) {
		return yaw(-Math.atan2(Math.cos(a), -Math.sin(a)));
	}

	private void lightWallAfter(int step) {
		double height = wallHeight();
		for (int i = 0; i < wallPanels.size(); i++) {
			String name = wallPanels.get(i);
			double a = Integer.parseInt(name.substring(4)) * Math.PI * 2 / WALL_PANELS;
			if (step == 2) {
				// Up into the sky in under a second; the model's base sits 1.5 sizes below its origin.
				double sy = height / 1.5;
				move(name, panelTurn(a), 0, 1.5 * sy, 0, panelWidth(), sy, 1, 14);
			}
			if (step == 50) {
				// Then the whole wall lifts off the ground and rises into the sky, thinning as it goes.
				double thin = height * 0.3 / 1.5;
				move(name, panelTurn(a), 0, height * 1.4 + 1.5 * thin, 0, panelWidth(), thin, 1, 40);
			}
			if (step == 92) {
				kill(name);
			}
		}
	}

	/** The world holds its breath: the drone cuts out, the circles collapse into the core, then the charge. */
	private void silence() {
		for (ServerPlayer other : near(radius * 3 + 64)) {
			other.connection.send(new ClientboundStopSoundPacket(BuiltInRegistries.SOUND_EVENT.getKey(ModSounds.KING_DRONE), SoundSource.HOSTILE));
			other.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 10));
			other.connection.send(new ClientboundSetTitleTextPacket(Component.literal("")));
			other.connection.send(new ClientboundSetSubtitleTextPacket(SoulManager.msg("king_silence").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC)));
		}
		everyone(ModSounds.KING_CHARGE, 1.0f, 1.0f, radius * 3);
		transform("sky", flat(Math.PI), 0.01, PAUSE);
		transform("runes", flat(-Math.PI), 0.01, PAUSE - 6);
		bar.setName(SoulManager.msg("king_bar_end").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		// The rubble drops back down, all at once.
		cmd(String.format(Locale.ROOT, "kill @e[tag=%s,type=minecraft:block_display]", tag));
	}

	private void blast() {
		kill("sky");
		kill("runes");
		kill("core");
		for (int i = 0; i < ELEMENT_NAMES.length; i++) {
			kill("orb" + i);
			kill("pillar" + i);
			kill("link" + i);
		}
		everyone(ModSounds.KING_BOOM, 1.0f, 1.0f, radius * 3);
		// A beam of white fire where the core came down.
		summonBlock("beam", "minecraft:red_concrete", start, 6 * k, 90 * k);
		summonBlock("beam_core", "minecraft:white_concrete", start, 2 * k, 90 * k);
		lightWall();
		// Rings of red lightning answer the blow, out to the edge of the blast.
		for (int ringIndex = 1; ringIndex <= 3; ringIndex++) {
			for (int i = 0; i < 6; i++) {
				double a = i * Math.PI / 3 + ringIndex * 0.5;
				double r = radius * ringIndex / 3.0 * 0.9;
				bolt(start.add(Math.cos(a) * r, 0, Math.sin(a) * r));
			}
		}
	}

	private void wave(int index) {
		double[] reach = {radius / 3.0, radius * 2.0 / 3.0, radius};
		String name = "wave" + index;
		summonItem(name, "king_wave", start.add(0, 1.0 + index * 0.6, 0), flat(index), index == 0 ? 2 : reach[index - 1] * 2);
		pendingWaves.add(new double[] {index, reach[index] * 2});
		if (index > 0) {
			everyone(ModSounds.KING_BOOM, 0.8f, 1.15f + index * 0.1f, radius * 3);
		}
	}

	private final List<double[]> pendingWaves = new ArrayList<>();

	/** Second half of late(): waves start expanding one tick after they are spawned. */
	public void lateWaves() {
		for (double[] w : pendingWaves) {
			transform("wave" + (int) w[0], flat(w[0] + 0.5), w[1], 14);
		}
		pendingWaves.clear();
	}

	private void endStorm() {
		if (!storm) {
			return;
		}
		if (wasRaining) {
			level.setWeatherParameters(0, 6000, true, wasThundering);
		} else {
			level.setWeatherParameters(12000, 0, false, false);
		}
	}

	// ---------------------------------------------------------------- the eight elements (3D models)

	/** Spirit order, matching the seals painted on king_circle (clockwise from north). */
	private static final String[] ELEMENT_NAMES = {"fire", "light", "water", "death", "darkness", "lightning", "ice", "blood"};
	private static final int ORBS_AT = 40;
	private static final int PILLARS_AT = 60;
	private static final int LINKS_AT = 80;
	/** How far under the sky circle the orbs hang. */
	private static final double HANG = -2.5;

	private double sealRadius() {
		// The seals sit at 184 of 512 pixels from the texture's centre.
		return circle * 2 * 184.0 / 512.0;
	}

	/** World angle of seal i once the circle has turned by the given amount. */
	private static double sealAngle(int i, double turn) {
		return -Math.PI / 2 + i * Math.PI / 4 - turn;
	}

	private void summonElements() {
		Vec3 sky = start.add(0, skyHeight, 0);
		double r = sealRadius();
		double turn = ORBS_AT * 0.02 - 0.2;
		for (int i = 0; i < ELEMENT_NAMES.length; i++) {
			double a = sealAngle(i, turn);
			summonModel("orb" + i, "king_orb_" + ELEMENT_NAMES[i], sky, yaw(i), Math.cos(a) * r, (HANG + 6) * k, Math.sin(a) * r, 0.01, 0.01, 0.01, 15);
		}
		summonModel("core", "king_core", sky, yaw(0), 0, (HANG - 0.5) * k, 0, 0.01, 0.01, 0.01, 15);
		everyone(net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, 1.0f, 0.5f, radius * 3);
	}

	/** Every ten ticks: the orbs follow their seals round, the pillars reach the ground, the links feed the core. */
	private void moveElements(int age, double progress) {
		Vec3 sky = start.add(0, skyHeight, 0);
		double r = sealRadius();
		// Same turn the circle is heading to over these ten ticks.
		double turn = age * 0.02;
		double ground = skyHeight + HANG * k;
		for (int i = 0; i < ELEMENT_NAMES.length; i++) {
			double a = sealAngle(i, turn);
			double x = Math.cos(a) * r, z = Math.sin(a) * r;
			double bob = Math.sin(age * 0.1 + i) * 0.6 * k;
			double size = (7.0 + 2.0 * progress) * k;
			move("orb" + i, yaw(age * 0.12 + i), x, HANG * k + bob, z, size, size, size, 10);
			double pillar = (1.0 + progress * 0.6) * k;
			if (age == PILLARS_AT) {
				summonModel("pillar" + i, "king_pillar", sky, yaw(0), x, HANG * k, z, k, 0.01, k, 15);
			} else if (age > PILLARS_AT) {
				move("pillar" + i, yaw(age * 0.05), x, HANG * k, z, pillar, ground / 1.5, pillar, 10);
			}
			double link = (5 + progress * 4) * k;
			if (age == LINKS_AT) {
				summonModel("link" + i, "king_link_" + ELEMENT_NAMES[i], sky, yaw(-a), 0, HANG * k, 0, 0.01, 5 * k, 5 * k, 15);
			} else if (age > LINKS_AT) {
				move("link" + i, yaw(-a), 0, HANG * k, 0, r, link, link, 10);
			}
		}
		double core = (3 + 7 * progress) * k;
		move("core", yaw(age * -0.08), 0, (HANG - 0.5) * k, 0, core, core, core, 10);
		if (age == PILLARS_AT) {
			everyone(net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.5f, radius * 3);
		}
	}

	/** At the silence: the elements are drunk into the core, which swells. */
	private void converge() {
		int half = PAUSE / 2;
		for (int i = 0; i < ELEMENT_NAMES.length; i++) {
			move("orb" + i, yaw(i * 2.0), 0, (HANG - 0.5) * k, 0, 0.5 * k, 0.5 * k, 0.5 * k, half);
			move("link" + i, yaw(-sealAngle(i, (castTicks - PAUSE) * 0.02)), 0, HANG * k, 0, 0.01, 2 * k, 2 * k, half);
			move("pillar" + i, yaw(0), 0, HANG * k, 0, 0.3 * k, 0.01, 0.3 * k, 10);
		}
		move("core", yaw(Math.PI * 0.9), 0, (HANG - 0.5) * k, 0, 15 * k, 15 * k, 15 * k, half);
	}

	/** Then the core shrinks to a burning point and falls to the ground. */
	private void dropCore() {
		for (int i = 0; i < ELEMENT_NAMES.length; i++) {
			kill("orb" + i);
			kill("link" + i);
			kill("pillar" + i);
		}
		// The dragons have been drawn into the core.
		killDragons();
		move("core", yaw(Math.PI * 1.8), 0, -skyHeight + 1, 0, 2.5 * k, 2.5 * k, 2.5 * k, PAUSE / 2);
	}

	/** The mushroom cloud: a burning stem that climbs to sixty-odd blocks under a rolling cap, then breaks up. */
	private void mushroom(int step) {
		switch (step) {
			case 3 -> {
				summonModel("stem", "king_cloud_stem", start, yaw(0), 0, 0.75 * cloud, 0, 0.5 * cloud, 0.5 * cloud, 0.5 * cloud, 11);
				summonModel("cap", "king_cloud_cap", start, yaw(0), 0, 2 * cloud, 0, 1 * cloud, 1 * cloud, 1 * cloud, 11);
			}
			case 4 -> {
				move("stem", yaw(0.2), 0, (1.5 * 10) * cloud, 0, 5 * cloud, 10 * cloud, 5 * cloud, 25);
				move("cap", yaw(0.3), 0, 31 * cloud, 0, 8 * cloud, 8 * cloud, 8 * cloud, 25);
			}
			case 30 -> {
				move("stem", yaw(0.5), 0, (1.5 * 22) * cloud, 0, 7 * cloud, 22 * cloud, 7 * cloud, 70);
				move("cap", yaw(0.9), 0, 69 * cloud, 0, 13 * cloud, 13 * cloud, 13 * cloud, 70);
			}
			case 100 -> {
				move("stem", yaw(0.8), 0, (1.5 * 23) * cloud, 0, 6.5 * cloud, 23 * cloud, 6.5 * cloud, 80);
				move("cap", yaw(1.6), 0, 71.5 * cloud, 0, 15 * cloud, 12 * cloud, 15 * cloud, 80);
			}
			case 180 -> {
				move("stem", yaw(1.0), 0, (1.5 * 23) * cloud, 0, 0.01 * cloud, 23 * cloud, 0.01 * cloud, 60);
				move("cap", yaw(2.2), 0, 76 * cloud, 0, 22 * cloud, 1.5 * cloud, 22 * cloud, 66);
			}
			case 250 -> {
				kill("stem");
				kill("cap");
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- display entity helpers

	private static float[] yaw(double angle) {
		return new float[] {0, (float) Math.sin(angle / 2), 0, (float) Math.cos(angle / 2)};
	}

	private static String transformation(float[] q, double tx, double ty, double tz, double sx, double sy, double sz) {
		return String.format(Locale.ROOT,
				"{left_rotation:[%.5ff,%.5ff,%.5ff,%.5ff],right_rotation:[0f,0f,0f,1f],translation:[%.3ff,%.3ff,%.3ff],scale:[%.3ff,%.3ff,%.3ff]}",
				q[0], q[1], q[2], q[3], tx, ty, tz, sx, sy, sz);
	}

	/** Summons one of the mod's 3D models (a Blockbench item model) as an item display. */
	private void summonModel(String name, String model, Vec3 at, float[] q, double tx, double ty, double tz, double sx, double sy, double sz, int block) {
		cmd(String.format(Locale.ROOT,
				"summon minecraft:item_display %.3f %.3f %.3f {Tags:[\"%s\",\"%s\",\"%s_%s\"],"
						+ "item:{id:\"minecraft:paper\",count:1,components:{\"minecraft:item_model\":\"sacredsouls:%s\"}},"
						+ "item_display:\"none\",brightness:{sky:15,block:%d},view_range:12f,transformation:%s}",
				at.x, at.y, at.z, TAG, tag, tag, name, model, block, transformation(q, tx, ty, tz, sx, sy, sz)));
	}

	private void move(String name, float[] q, double tx, double ty, double tz, double sx, double sy, double sz, int ticks) {
		cmd(String.format(Locale.ROOT, "data merge entity @e[tag=%s_%s,limit=1] {start_interpolation:0,interpolation_duration:%d,transformation:%s}",
				tag, name, ticks, transformation(q, tx, ty, tz, sx, sy, sz)));
	}

	/** A rotation that lays the flat model on the ground, turned by an angle around the vertical. */
	private static float[] flat(double turn) {
		double sx = Math.sin(-Math.PI / 4), cx = Math.cos(-Math.PI / 4);
		double sy = Math.sin(turn / 2), cy = Math.cos(turn / 2);
		return new float[] {(float) (cy * sx), (float) (sy * cx), (float) (-sy * sx), (float) (cy * cx)};
	}

	private static float[] quat(double ax, double ay, double az, double angle) {
		double len = Math.sqrt(ax * ax + ay * ay + az * az);
		if (len < 1.0E-4) {
			return new float[] {0, 0, 0, 1};
		}
		double s = Math.sin(angle / 2) / len;
		return new float[] {(float) (ax * s), (float) (ay * s), (float) (az * s), (float) Math.cos(angle / 2)};
	}

	private static String transformation(float[] q, double scale) {
		return String.format(Locale.ROOT,
				"{left_rotation:[%.5ff,%.5ff,%.5ff,%.5ff],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[%.3ff,%.3ff,%.3ff]}",
				q[0], q[1], q[2], q[3], scale, scale, scale);
	}

	private void summonItem(String name, String model, Vec3 at, float[] rotation, double scale) {
		cmd(String.format(Locale.ROOT,
				"summon minecraft:item_display %.3f %.3f %.3f {Tags:[\"%s\",\"%s\",\"%s_%s\"],"
						+ "item:{id:\"minecraft:paper\",count:1,components:{\"minecraft:item_model\":\"sacredsouls:%s\"}},"
						+ "item_display:\"none\",brightness:{sky:15,block:15},view_range:12f,transformation:%s}",
				at.x, at.y, at.z, TAG, tag, tag, name, model, transformation(rotation, scale)));
	}

	private void summonBlock(String name, String block, Vec3 at, double width, double height) {
		cmd(String.format(Locale.ROOT,
				"summon minecraft:block_display %.3f %.3f %.3f {Tags:[\"%s\",\"%s\",\"%s_%s\"],block_state:{Name:\"%s\"},"
						+ "brightness:{sky:15,block:15},view_range:12f,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
						+ "translation:[%.2ff,0f,%.2ff],scale:[%.2ff,%.2ff,%.2ff]}}",
				at.x, at.y, at.z, TAG, tag, tag, name, block, -width / 2, -width / 2, width, height, width));
	}

	private void blockTransform(String name, double width, double height, int ticks) {
		// The beam thins to nothing while it stays tall.
		cmd(String.format(Locale.ROOT,
				"data merge entity @e[tag=%s_%s,limit=1] {start_interpolation:0,interpolation_duration:%d,"
						+ "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[%.2ff,0f,%.2ff],scale:[%.2ff,%.2ff,%.2ff]}}",
				tag, name, ticks, -width / 2, -width / 2, width, height, width));
	}

	private void transform(String name, float[] rotation, double scale, int ticks) {
		cmd(String.format(Locale.ROOT, "data merge entity @e[tag=%s_%s,limit=1] {start_interpolation:0,interpolation_duration:%d,transformation:%s}",
				tag, name, ticks, transformation(rotation, scale)));
	}

	private void kill(String name) {
		cmd(String.format(Locale.ROOT, "kill @e[tag=%s_%s]", tag, name));
	}

	private void killAll() {
		cmd(String.format(Locale.ROOT, "kill @e[tag=%s]", tag));
	}

	private void cmd(String command) {
		MinecraftServer server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(), command);
	}

	/** Removes show entities left behind by a crash or an interrupted cast, in every loaded dimension. */
	public static void cleanupAll(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(),
					"kill @e[tag=" + TAG + "]");
		}
	}

	// ---------------------------------------------------------------- sound helpers

	private List<ServerPlayer> near(double range) {
		List<ServerPlayer> list = new ArrayList<>();
		for (ServerPlayer other : level.players()) {
			if (other.position().distanceTo(start) <= range) {
				list.add(other);
			}
		}
		return list;
	}

	/** Plays a sound to each nearby player alone, at their own position, so it never stacks up between them. */
	private void everyone(SoundEvent sound, float volume, float pitch, double range) {
		net.minecraft.core.Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
		for (ServerPlayer other : near(range)) {
			other.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(holder, SoundSource.HOSTILE,
					other.getX(), other.getY(), other.getZ(), volume, pitch, level.random.nextLong()));
		}
	}
}
