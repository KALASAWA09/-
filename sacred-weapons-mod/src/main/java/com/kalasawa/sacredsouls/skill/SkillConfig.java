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
	/** Lets the Spirit King ultimate break blocks at all (still follows blockMode). */
	public static boolean ultimateBreaksBlocks = true;
	/** Radius of the ultimate's third wave, 20 to 70 blocks. */
	public static int ultimateRadius = 50;
	/** Cast time of the ultimate in seconds, 5 to 10. */
	public static int ultimateCastSeconds = 8;
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
		ultimateBreaksBlocks = Boolean.parseBoolean(props.getProperty("ultimate_breaks_blocks", "true").trim());
		ultimateRadius = clamp(intOf(props, "ultimate_radius", 50), 20, 70);
		ultimateCastSeconds = clamp(intOf(props, "ultimate_cast_seconds", 8), 5, 10);
		ultimateRegrowSeconds = clamp(intOf(props, "ultimate_regrow_seconds", 60), 10, 3600);
		blocksPerTick = clamp(intOf(props, "blocks_per_tick", 2000), 100, 20000);
		save(file);
	}

	private static void save(Path file) {
		Properties props = new Properties();
		props.setProperty("skill_block_mode", blockMode.name().toLowerCase());
		props.setProperty("skill_regrow_seconds", Integer.toString(regrowSeconds));
		props.setProperty("ultimate_breaks_blocks", Boolean.toString(ultimateBreaksBlocks));
		props.setProperty("ultimate_radius", Integer.toString(ultimateRadius));
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
