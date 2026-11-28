package com.kalasawa.sacredsouls.skill;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

import com.kalasawa.sacredsouls.SacredSouls;

/** Server settings for active skills, in config/sacredsouls.properties. */
public final class SkillConfig {
	public enum BlockMode { OFF, TEMPORARY, PERMANENT }

	/** How skills break blocks: off, temporary (they grow back) or permanent. */
	public static BlockMode blockMode = BlockMode.TEMPORARY;
	/** Seconds before blocks broken in temporary mode grow back. */
	public static int regrowSeconds = 10;
	/** Multiplies all skill damage, 0.5 to 5. */
	public static double damageMultiplier = 2.5;
	/** Lets the Spirit King ultimate break blocks at all (still follows blockMode). */
	public static boolean ultimateBreaksBlocks = true;
	/** Radius of the ultimate's last wave, 20 to 200 blocks. */
	public static int ultimateRadius = 150;
	/** Cast time of the ultimate in seconds, 5 to 10. */
	public static int ultimateCastSeconds = 10;
	/** Lets the ultimate call a thunderstorm over the overworld while it is cast. */
	public static boolean ultimateStorm = true;
	/** Seconds before blocks broken by the ultimate grow back in temporary mode. */
	public static int ultimateRegrowSeconds = 60;
	/** Most blocks changed per tick by the ultimate, to keep the server from freezing. */
	public static int blocksPerTick = 2000;

	private SkillConfig() {
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("sacredsouls.properties");
		Properties props = new Properties();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				props.load(reader);
			} catch (IOException e) {
				SacredSouls.LOGGER.warn("Could not read {}", file, e);
			}
		}
		try {
			blockMode = BlockMode.valueOf(props.getProperty("skill_block_mode", "temporary").trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			blockMode = BlockMode.TEMPORARY;
		}
		regrowSeconds = clamp(intOf(props, "skill_regrow_seconds", 10), 2, 600);
		damageMultiplier = Math.max(0.5, Math.min(5.0, doubleOf(props, "skill_damage_multiplier", 2.5)));
		if (intOf(props, "config_version", 1) < 3 && damageMultiplier < 2.5) {
			damageMultiplier = 2.5; // Raised from 2.0 in version 3.
		}
		ultimateBreaksBlocks = Boolean.parseBoolean(props.getProperty("ultimate_breaks_blocks", "true").trim());
		ultimateRadius = clamp(intOf(props, "ultimate_radius", 150), 20, 200);
		if (intOf(props, "config_version", 1) < 2) {
			ultimateRadius = 150; // The radius went from 50 to 150 in version 2.
		}
		ultimateCastSeconds = clamp(intOf(props, "ultimate_cast_seconds", 10), 5, 10);
		if (intOf(props, "config_version", 1) < 4) {
			ultimateCastSeconds = 10; // A longer build-up since version 4.
		}
		ultimateStorm = Boolean.parseBoolean(props.getProperty("ultimate_storm", "true").trim());
		ultimateRegrowSeconds = clamp(intOf(props, "ultimate_regrow_seconds", 60), 10, 3600);
		blocksPerTick = clamp(intOf(props, "blocks_per_tick", 2000), 100, 20000);
		save(file);
	}

	/** Changes the block mode in game and writes it to the config file. */
	public static void setBlockMode(BlockMode mode) {
		blockMode = mode;
		save(FabricLoader.getInstance().getConfigDir().resolve("sacredsouls.properties"));
	}

	private static void save(Path file) {
		Properties props = new Properties();
		props.setProperty("config_version", "4");
		props.setProperty("skill_block_mode", blockMode.name().toLowerCase());
		props.setProperty("skill_regrow_seconds", Integer.toString(regrowSeconds));
		props.setProperty("skill_damage_multiplier", Double.toString(damageMultiplier));
		props.setProperty("ultimate_breaks_blocks", Boolean.toString(ultimateBreaksBlocks));
		props.setProperty("ultimate_radius", Integer.toString(ultimateRadius));
		props.setProperty("ultimate_storm", Boolean.toString(ultimateStorm));
		props.setProperty("ultimate_cast_seconds", Integer.toString(ultimateCastSeconds));
		props.setProperty("ultimate_regrow_seconds", Integer.toString(ultimateRegrowSeconds));
		props.setProperty("blocks_per_tick", Integer.toString(blocksPerTick));
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				props.store(writer, "Sacred Souls active skills. skill_block_mode: off, temporary or permanent");
			}
		} catch (IOException e) {
			SacredSouls.LOGGER.warn("Could not write {}", file, e);
		}
	}

	private static double doubleOf(Properties props, String key, double fallback) {
		try {
			return Double.parseDouble(props.getProperty(key, Double.toString(fallback)).trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static int intOf(Properties props, String key, int fallback) {
		try {
			return Integer.parseInt(props.getProperty(key, Integer.toString(fallback)).trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
