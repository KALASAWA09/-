package com.kalasawa.sacredsouls.power;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The 20 soul items. Each one gives one passive power when devoured.
 * Numbers live in {@link PowerHandler}; the text lives in the lang files.
 */
public enum SoulPower {
	SERPENT_EYE("serpent_eye", 0x7FD14B),
	STONE_HEART("stone_heart", 0x8A8A8A),
	WIND_FEATHER("wind_feather", 0xBFE9FF),
	MIRROR_SHADOW("mirror_shadow", 0x4B2E83),
	SPIRIT_BELL("spirit_bell", 0xF2C14E),
	TIME_CRYSTAL("time_crystal", 0x5BE7E0),
	HAUNTED_COMPASS("haunted_compass", 0xB34A4A),
	ETERNAL_ICE("eternal_ice", 0x9FD8FF),
	EMBER_COAL("ember_coal", 0xFF6A2B),
	ANCIENT_ROOT("ancient_root", 0x6B4F2A),
	STONE_HORN("stone_horn", 0xD9D2C3),
	BAT_WING("bat_wing", 0x3D2B2B),
	CHAMELEON_SCALE("chameleon_scale", 0x47C27A),
	SHACKLE("shackle", 0x6E6E78),
	MIRROR_GLASS("mirror_glass", 0xE6F7FF),
	SPIDER_FANG("spider_fang", 0x5A1F1F),
	GRAVITY_ORB("gravity_orb", 0xA35BD6),
	MINER_CORE("miner_core", 0x4FE3C1),
	WILD_FLUTE("wild_flute", 0xC9A66B),
	THIEF_MASK("thief_mask", 0x2B2B2B);

	public final String id;
	public final int color;

	SoulPower(String id, int color) {
		this.id = id;
		this.color = color;
	}

	/** Item name, e.g. "Serpent Eye". */
	public MutableComponent displayName() {
		return Component.translatable("item.sacredsouls." + id);
	}

	/** One short sentence on what the power does, without numbers. */
	public MutableComponent summary() {
		return Component.translatable("power.sacredsouls." + id + ".summary");
	}

	/** The numbers at one level (1 to 3), e.g. "Range 16 blocks". */
	public MutableComponent effect(int level) {
		return Component.translatable("power.sacredsouls." + id + "." + Math.max(1, Math.min(3, level)));
	}

	public static SoulPower byId(String id) {
		for (SoulPower power : values()) {
			if (power.id.equals(id)) {
				return power;
			}
		}
		return null;
	}
}
