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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
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
 * times, the ground cracks open in glowing lines, rubble lifts into the air, lightning walks the field, and then
 * everything goes silent before the blow. Damage and the crater stay in SkillHandler.
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
		this.circle = Mth.clamp(radius * 0.3, 22, 45);
		this.storm = SkillConfig.ultimateStorm && level.dimension() == Level.OVERWORLD;
		this.bar = new ServerBossEvent(SoulManager.msg("king_bar", caster.getName()).withStyle(ChatFormatting.DARK_RED),
				BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
		this.bar.setDarkenScreen(true);
		this.bar.setCreateWorldFog(true);
		for (int i = 0; i < 12; i++) {
			cracks.add(new double[] {i * Math.PI * 2 / 12 + level.random.nextDouble() * 0.3, 0});
		}
	}

	// ---------------------------------------------------------------- timeline

	/** Called every tick of the cast, with the tick number. */
	public void cast(int age) {
		double progress = (double) age / castTicks;
		int pauseStart = castTicks - PAUSE;
		if (age == 0) {
			begin();
		}
		if (age % 20 == 0) {
			watchers();
		}
		bar.setProgress((float) Mth.clamp(1.0 - progress, 0, 1));
		// The circles turn, slowly and smoothly.
		if (age > 0 && age < pauseStart && age % 10 == 0) {
			double grow = Math.min(1.0, age / 40.0);
			double turn = age * 0.02;
			transform("sky", flat(turn), circle * 2 * grow, 10);
			transform("runes", flat(-turn * 1.6), circle * 1.45 * grow, 10);
			transform("ground", flat(turn * 0.5), Math.min(60, radius * 0.4) * grow, 10);
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
		// Lightning walks the field, more and more often.
		int gap = Math.max(4, (int) (24 - 20 * progress));
		if (age > 10 && age < pauseStart && age % gap == 0) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double r = radius * (0.2 + level.random.nextDouble() * 0.6);
			bolt(start.add(Math.cos(a) * r, 0, Math.sin(a) * r));
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
		if (step == 0) {
			blast();
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
			blockTransform("beam", 0.05, 90, 18);
			blockTransform("beam_core", 0.05, 90, 12);
		}
		if (step == 20) {
			kill("beam");
			kill("beam_core");
		}
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
		Vec3 sky = start.add(0, 45, 0);
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
		double reach = Math.min(radius * 0.7, 80) * Math.min(1.0, progress / 0.85);
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
		for (int i = 0; i < 28; i++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double r = 4 + level.random.nextDouble() * 26;
			BlockPos column = BlockPos.containing(start.x + Math.cos(a) * r, start.y, start.z + Math.sin(a) * r);
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
			BlockState state = level.getBlockState(top);
			String block = state.isAir() || state.hasBlockEntity() || !state.isCollisionShapeFullBlock(level, top)
					? "minecraft:cobblestone" : BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
			String name = "debris" + (debrisCount++);
			float size = 0.6f + level.random.nextFloat() * 1.2f;
			cmd(String.format(Locale.ROOT,
					"summon minecraft:block_display %.2f %.2f %.2f {Tags:[\"%s\",\"%s\",\"%s\"],block_state:{Name:\"%s\"},view_range:6f,"
							+ "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[%.2ff,0f,%.2ff],scale:[%.2ff,%.2ff,%.2ff]}}",
					top.getX() + 0.5, top.getY() + 1.0, top.getZ() + 0.5, TAG, tag, tag + "_" + name, block, -size / 2, -size / 2, size, size, size));
			double rise = 6 + level.random.nextDouble() * 18;
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

	private void bolt(Vec3 at) {
		BlockPos column = BlockPos.containing(at);
		if (!level.isLoaded(column)) {
			return;
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.setPos(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
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
		summonBlock("beam", "minecraft:white_concrete", start, 6, 90);
		summonBlock("beam_core", "minecraft:light_blue_concrete", start, 2, 90);
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3;
			bolt(start.add(Math.cos(a) * 6, 0, Math.sin(a) * 6));
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
	private static final double SKY = 45;

	private double sealRadius() {
		// The seals sit at 184 of 512 pixels from the texture's centre.
		return circle * 2 * 184.0 / 512.0;
	}

	/** World angle of seal i once the circle has turned by the given amount. */
	private static double sealAngle(int i, double turn) {
		return -Math.PI / 2 + i * Math.PI / 4 - turn;
	}

	private void summonElements() {
		Vec3 sky = start.add(0, SKY, 0);
		double r = sealRadius();
		double turn = ORBS_AT * 0.02 - 0.2;
		for (int i = 0; i < ELEMENT_NAMES.length; i++) {
			double a = sealAngle(i, turn);
			summonModel("orb" + i, "king_orb_" + ELEMENT_NAMES[i], sky, yaw(i), Math.cos(a) * r, HANG + 6, Math.sin(a) * r, 0.01, 0.01, 0.01, 15);
		}
		summonModel("core", "king_core", sky, yaw(0), 0, HANG - 0.5, 0, 0.01, 0.01, 0.01, 15);
		everyone(net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, 1.0f, 0.5f, radius * 3);
	}

	/** Every ten ticks: the orbs follow their seals round, the pillars reach the ground, the links feed the core. */
	private void moveElements(int age, double progress) {
		Vec3 sky = start.add(0, SKY, 0);
		double r = sealRadius();
		// Same turn the circle is heading to over these ten ticks.
		double turn = age * 0.02;
		double ground = SKY + HANG;
		for (int i = 0; i < ELEMENT_NAMES.length; i++) {
			double a = sealAngle(i, turn);
			double x = Math.cos(a) * r, z = Math.sin(a) * r;
			double bob = Math.sin(age * 0.1 + i) * 0.6;
			double size = 7.0 + 2.0 * progress;
			move("orb" + i, yaw(age * 0.12 + i), x, HANG + bob, z, size, size, size, 10);
			if (age == PILLARS_AT) {
				summonModel("pillar" + i, "king_pillar", sky, yaw(0), x, HANG, z, 1.0, 0.01, 1.0, 15);
			} else if (age > PILLARS_AT) {
				move("pillar" + i, yaw(age * 0.05), x, HANG, z, 1.0 + progress * 0.6, ground / 1.5, 1.0 + progress * 0.6, 10);
			}
			if (age == LINKS_AT) {
				summonModel("link" + i, "king_link_" + ELEMENT_NAMES[i], sky, yaw(-a), 0, HANG, 0, 0.01, 5, 5, 15);
			} else if (age > LINKS_AT) {
				move("link" + i, yaw(-a), 0, HANG, 0, r, 5 + progress * 4, 5 + progress * 4, 10);
			}
		}
		double core = 3 + 7 * progress;
		move("core", yaw(age * -0.08), 0, HANG - 0.5, 0, core, core, core, 10);
		if (age == PILLARS_AT) {
			everyone(net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.5f, radius * 3);
		}
	}

	/** At the silence: the elements are drunk into the core, which swells. */
	private void converge() {
		int half = PAUSE / 2;
		for (int i = 0; i < ELEMENT_NAMES.length; i++) {
			move("orb" + i, yaw(i * 2.0), 0, HANG - 0.5, 0, 0.5, 0.5, 0.5, half);
			move("link" + i, yaw(-sealAngle(i, (castTicks - PAUSE) * 0.02)), 0, HANG, 0, 0.01, 2, 2, half);
			move("pillar" + i, yaw(0), 0, HANG, 0, 0.3, 0.01, 0.3, 10);
		}
		move("core", yaw(Math.PI * 0.9), 0, HANG - 0.5, 0, 15, 15, 15, half);
	}

	/** Then the core shrinks to a burning point and falls to the ground. */
	private void dropCore() {
		for (int i = 0; i < ELEMENT_NAMES.length; i++) {
			kill("orb" + i);
			kill("link" + i);
			kill("pillar" + i);
		}
		move("core", yaw(Math.PI * 1.8), 0, -SKY + 1, 0, 2.5, 2.5, 2.5, PAUSE / 2);
	}

	/** The mushroom cloud: a burning stem that climbs to sixty-odd blocks under a rolling cap, then breaks up. */
	private void mushroom(int step) {
		switch (step) {
			case 3 -> {
				summonModel("stem", "king_cloud_stem", start, yaw(0), 0, 0.75, 0, 0.5, 0.5, 0.5, 11);
				summonModel("cap", "king_cloud_cap", start, yaw(0), 0, 2, 0, 1, 1, 1, 11);
			}
			case 4 -> {
				move("stem", yaw(0.2), 0, 1.5 * 10, 0, 5, 10, 5, 25);
				move("cap", yaw(0.3), 0, 31, 0, 8, 8, 8, 25);
			}
			case 30 -> {
				move("stem", yaw(0.5), 0, 1.5 * 22, 0, 7, 22, 7, 70);
				move("cap", yaw(0.9), 0, 69, 0, 13, 13, 13, 70);
			}
			case 100 -> {
				move("stem", yaw(0.8), 0, 1.5 * 23, 0, 6.5, 23, 6.5, 80);
				move("cap", yaw(1.6), 0, 71.5, 0, 15, 12, 15, 80);
			}
			case 180 -> {
				move("stem", yaw(1.0), 0, 1.5 * 23, 0, 0.01, 23, 0.01, 60);
				move("cap", yaw(2.2), 0, 76, 0, 22, 1.5, 22, 66);
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
