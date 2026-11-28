package com.kalasawa.sacredsouls.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import com.kalasawa.sacredsouls.SacredSouls;
import com.kalasawa.sacredsouls.network.KingCastPayload;
import com.kalasawa.sacredsouls.spirit.Spirit;

/**
 * Draws the Spirit King ultimate on the client, tick by tick, so the shapes are smooth and whole:
 * the sky darkens, sparks fly from the eight element orbs (3D models from the server), everything falls silent for a moment, then a white flash, three waves, a mushroom cloud
 * and ash. The server only sends the start; damage and blocks stay on the server.
 */
public final class KingEffects {
	private static final ParticleOptions GOLD = new DustParticleOptions(0xFFD86A, 4.0f);
	private static final ParticleOptions PALE = new DustParticleOptions(0xFFF4D6, 3.0f);
	private static final ParticleOptions VIOLET = new DustParticleOptions(0x7A3CFF, 4.0f);
	private static final ParticleOptions SHADOW = new DustParticleOptions(0x24103C, 4.0f);
	private static final ParticleOptions CORE = new DustParticleOptions(0xFFFFFF, 4.0f);
	private static final ParticleOptions ASH = new DustParticleOptions(0x4A4442, 4.0f);
	private static final ParticleOptions EMBER = new DustParticleOptions(0xFF7A1E, 4.0f);

	private static final double[] WAVES = {1.0 / 3.0, 2.0 / 3.0, 1.0};
	private static final int WAVE_GAP = 22;
	private static final int WAVE_SPREAD = 14;
	private static final int PAUSE = com.kalasawa.sacredsouls.skill.KingShow.PAUSE;
	private static final int AFTER = 320;

	private record Effect(Vec3 center, int radius, int castTicks, long start) {
	}

	private static final List<Effect> ACTIVE = new ArrayList<>();
	/** Screen tint: 0..1 darkness during the cast, and a white flash after it. */
	private static float darkness;
	private static float flash;

	private KingEffects() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(KingCastPayload.TYPE, (payload, context) -> {
			Minecraft client = Minecraft.getInstance();
			if (client.level != null) {
				ACTIVE.add(new Effect(new Vec3(payload.x(), payload.y(), payload.z()), payload.radius(), payload.castTicks(),
						client.level.getGameTime()));
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, SacredSouls.id("king_tint"), KingEffects::renderTint);
	}

	static void tick(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null || client.player == null) {
			ACTIVE.clear();
			darkness = 0;
			flash = 0;
			return;
		}
		float targetDark = 0;
		flash = Math.max(0, flash - 0.035f);
		for (Iterator<Effect> it = ACTIVE.iterator(); it.hasNext();) {
			Effect effect = it.next();
			int age = (int) (level.getGameTime() - effect.start());
			if (age > effect.castTicks() + AFTER) {
				it.remove();
				continue;
			}
			double distance = client.player.position().distanceTo(effect.center());
			double near = Mth.clamp(1.0 - (distance - effect.radius()) / (effect.radius() * 1.5), 0, 1);
			if (age < effect.castTicks()) {
				double t = (double) age / effect.castTicks();
				targetDark = Math.max(targetDark, (float) (near * Math.min(0.55, t * 0.7)));
				if (age >= effect.castTicks() - PAUSE) {
					targetDark = Math.max(targetDark, (float) (near * 0.7));
				}
				cast(level, client, effect, age);
			} else {
				int step = age - effect.castTicks();
				if (step == 0) {
					flash = Math.max(flash, (float) (0.95 * near));
				}
				for (int w = 0; w < 3; w++) {
					if (step == w * WAVE_GAP && w > 0) {
						flash = Math.max(flash, (float) (0.35 * near));
					}
				}
				after(level, client, effect, step);
			}
		}
		darkness += (targetDark - darkness) * 0.15f;
	}

	// ---------------------------------------------------------------- the cast

	private static void cast(ClientLevel level, Minecraft client, Effect e, int age) {
		RandomSource random = level.random;
		Vec3 c = e.center();
		double t = (double) age / e.castTicks();
		int pauseStart = e.castTicks() - PAUSE;
		boolean pause = age >= pauseStart;
		double circle = Mth.clamp(e.radius() * 0.3, 22, 45);
		Vec3 sky = c.add(0, 45, 0);
		// Embers drift up past the circle's rim.
		if (!pause && age % 2 == 0) {
			double a = random.nextDouble() * Math.PI * 2;
			add(level, EMBER, sky.add(Math.cos(a) * circle, -random.nextDouble() * 4, Math.sin(a) * circle), 0, 0.02, 0);
		}
		double spin = age * 0.012;

		if (!pause) {
			// The circles themselves are textured display entities from the server; the client adds light and motion.
			// The shadow on the ground marks the edge of the blast.
			ring(level, SHADOW, c.add(0, 0.4, 0), e.radius(), 10, spin, Math.min(1.0, t / 0.4));
			ring(level, VIOLET, c.add(0, 0.6, 0), e.radius(), 4, -spin, Math.min(1.0, t / 0.4));
			// The orbs, pillars, links and core are 3D models from the server; the client adds sparks around each orb.
			if (age > 40 && age % 2 == 0) {
				double seal = circle * 2 * 184.0 / 512.0;
				double turn = (age - 10) * 0.02;
				Spirit[] spirits = Spirit.values();
				for (int i = 0; i < spirits.length; i++) {
					double a = -Math.PI / 2 + i * Math.PI / 4 - turn;
					Vec3 at = sky.add(Math.cos(a) * seal, -2.5, Math.sin(a) * seal);
					add(level, element(spirits[i]), at.add(random.nextGaussian() * 1.5, random.nextGaussian() * 1.5, random.nextGaussian() * 1.5),
							random.nextGaussian() * 0.04, -0.05, random.nextGaussian() * 0.04);
				}
			}
		} else {
			// The silence before the blow: the core shrinks to a point, then a thin beam falls to the ground.
			int p = age - pauseStart;
			double squeeze = 1.0 - p / (double) PAUSE;
			for (int k = 0; k < 6; k++) {
				Vec3 from = sky.add(random.nextGaussian() * 8 * squeeze, random.nextGaussian() * 8 * squeeze, random.nextGaussian() * 8 * squeeze);
				Vec3 in = sky.subtract(from).scale(0.25);
				add(level, ParticleTypes.END_ROD, from, in.x, in.y, in.z);
			}
			add(level, CORE, sky, 0, 0, 0);
			if (p > PAUSE / 2) {
				double fall = (p - PAUSE / 2) / (PAUSE / 2.0);
				line(level, CORE, sky, sky.add(0, -45 * fall, 0), 1.5, random);
			}
		}
		// Souls and ash rise from the ground around whoever is watching, inside the blast.
		Vec3 me = client.player.position();
		if (me.distanceTo(c) < e.radius() + 10) {
			for (int k = 0; k < 4; k++) {
				Vec3 at = me.add(random.nextGaussian() * 10, random.nextDouble() * 2, random.nextGaussian() * 10);
				add(level, k % 2 == 0 ? ParticleTypes.SOUL : ParticleTypes.ASH, at, 0, 0.05 + random.nextDouble() * 0.08, 0);
			}
		}
	}

	// ---------------------------------------------------------------- the blast and after

	private static void after(ClientLevel level, Minecraft client, Effect e, int step) {
		RandomSource random = level.random;
		Vec3 c = e.center();
		if (step == 0) {
			for (int k = 0; k < 10; k++) {
				add(level, ParticleTypes.EXPLOSION_EMITTER, c.add(random.nextGaussian() * 6, 1 + random.nextDouble() * 6, random.nextGaussian() * 6), 0, 0, 0);
			}
			for (double y = 0; y < 50; y += 1.0) {
				add(level, CORE, c.add(random.nextGaussian(), y, random.nextGaussian()), 0, 0, 0);
				add(level, ParticleTypes.END_ROD, c.add(0, y, 0), random.nextGaussian() * 0.3, 0, random.nextGaussian() * 0.3);
			}
		}
		// Three waves, each a wall of every element's colour rolling out over the ground.
		Spirit[] spirits = Spirit.values();
		for (int w = 0; w < 3; w++) {
			int local = step - w * WAVE_GAP;
			if (local < 0 || local > WAVE_SPREAD) {
				continue;
			}
			double from = w == 0 ? 0 : e.radius() * WAVES[w - 1];
			double to = e.radius() * WAVES[w];
			double front = from + (to - from) * Math.sqrt(local / (double) WAVE_SPREAD);
			int points = (int) Mth.clamp(front * 1.3, 24, 200);
			double phase = random.nextDouble();
			for (int i = 0; i < points; i++) {
				double a = (i + phase) * Math.PI * 2 / points;
				Vec3 at = c.add(Math.cos(a) * front, 0, Math.sin(a) * front);
				add(level, new DustParticleOptions(spirits[i % spirits.length].color, 4.0f), at.add(0, 0.8 + random.nextDouble() * 3.5, 0), 0, 0, 0);
				if (i % 3 == 0) {
					add(level, ParticleTypes.CLOUD, at.add(0, 0.5, 0), Math.cos(a) * 0.4, 0.05, Math.sin(a) * 0.4);
				}
				if (i % 20 == 0 && local % 3 == 0) {
					add(level, ParticleTypes.EXPLOSION, at.add(0, 1, 0), 0, 0, 0);
				}
			}
		}
		// The mushroom cloud: a burning stem that rises to 70 blocks and spreads a wide cap.
		if (step < 260) {
			double rise = Math.min(1.0, step / 70.0);
			double top = 8 + 62 * rise;
			for (int k = 0; k < 2; k++) {
				double y = random.nextDouble() * top;
				add(level, y < top * 0.55 ? EMBER : ASH, c.add(random.nextGaussian() * 3.5, y, random.nextGaussian() * 3.5), 0, 0.05, 0);
			}
			if (step % 2 == 0) {
				add(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c.add(random.nextGaussian() * 3, top * 0.3, random.nextGaussian() * 3), 0, 0.12, 0);
			}
			double cap = 10 + 24 * rise;
			for (int k = 0; k < 4; k++) {
				double a = random.nextDouble() * Math.PI * 2;
				double r = cap * Math.sqrt(random.nextDouble());
				double h = top + (1.0 - r / cap) * 8 + random.nextGaussian();
				add(level, k % 3 == 0 ? EMBER : ASH, c.add(Math.cos(a) * r, h, Math.sin(a) * r), 0, 0, 0);
			}
			if (step % 3 == 0) {
				add(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c.add(random.nextGaussian() * cap * 0.4, top, random.nextGaussian() * cap * 0.4), 0, 0.05, 0);
			}
		}
		// Fallout: ash drifting down around whoever is inside.
		Vec3 me = client.player.position();
		if (step > 20 && me.distanceTo(c) < e.radius() + 10) {
			for (int k = 0; k < 6; k++) {
				Vec3 at = me.add(random.nextGaussian() * 14, 6 + random.nextDouble() * 10, random.nextGaussian() * 14);
				add(level, k % 2 == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH, at, 0, -0.05, 0);
			}
		}
	}

	// ---------------------------------------------------------------- drawing helpers

	private static void add(ClientLevel level, ParticleOptions particle, Vec3 at, double dx, double dy, double dz) {
		level.addParticle(particle, true, true, at.x, at.y, at.z, dx, dy, dz);
	}

	/** A ring drawn a few points per tick at moving spots; long-lived dust fills it in. */
	private static void ring(ClientLevel level, ParticleOptions particle, Vec3 center, double radius, int points, double phase, double drawn) {
		RandomSource random = level.random;
		for (int i = 0; i < points; i++) {
			double a = phase + (i + random.nextDouble()) * Math.PI * 2 / points * drawn;
			add(level, particle, center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius), 0, 0, 0);
		}
	}

	private static void line(ClientLevel level, ParticleOptions particle, Vec3 from, Vec3 to, double step, RandomSource random) {
		Vec3 delta = to.subtract(from);
		int steps = Math.max(1, (int) (delta.length() / step));
		double offset = random.nextDouble();
		for (int i = 0; i < steps; i++) {
			add(level, particle, from.add(delta.scale((i + offset) / steps)), 0, 0, 0);
		}
	}

	private static ParticleOptions element(Spirit spirit) {
		return switch (spirit) {
			case FIRE -> ParticleTypes.FLAME;
			case LIGHT -> ParticleTypes.END_ROD;
			case WATER -> ParticleTypes.SPLASH;
			case DEATH -> ParticleTypes.SOUL_FIRE_FLAME;
			case DARKNESS -> ParticleTypes.SQUID_INK;
			case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
			case ICE -> ParticleTypes.SNOWFLAKE;
			case BLOOD -> ParticleTypes.DAMAGE_INDICATOR;
		};
	}

	// ---------------------------------------------------------------- screen tint

	private static void renderTint(GuiGraphics graphics, DeltaTracker tickCounter) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		if (darkness > 0.01f) {
			// A dark wash over everything, and a blood-red vignette closing in from the edges.
			int wash = (int) (darkness * 140) & 0xFF;
			graphics.fill(0, 0, width, height, (wash << 24) | 0x080208);
			int edge = (int) (Math.min(1f, darkness * 1.4f) * 220) & 0xFF;
			int red = (edge << 24) | 0x3A0008;
			int clear = 0x003A0008;
			int band = Math.max(1, (int) (height * 0.28f));
			graphics.fillGradient(0, 0, width, band, red, clear);
			graphics.fillGradient(0, height - band, width, height, clear, red);
			int side = Math.max(1, (int) (width * 0.18f));
			for (int i = 0; i < side; i += 2) {
				int a = (int) (edge * (1.0f - i / (float) side)) & 0xFF;
				graphics.fill(i, 0, i + 2, height, (a << 24) | 0x3A0008);
				graphics.fill(width - i - 2, 0, width - i, height, (a << 24) | 0x3A0008);
			}
		}
		if (flash > 0.01f) {
			int alpha = (int) (Math.min(1f, flash) * 255) & 0xFF;
			graphics.fill(0, 0, width, height, (alpha << 24) | 0xFFF8EC);
		}
	}

}
