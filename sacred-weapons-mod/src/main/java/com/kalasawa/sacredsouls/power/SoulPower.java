package com.kalasawa.sacredsouls.power;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The 50 soul items. Each one gives one passive power when devoured.
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
	THIEF_MASK("thief_mask", 0x2B2B2B),
	VAMPIRE_TOOTH("vampire_tooth", 0xB0213A),
	THORN_VINE("thorn_vine", 0x3E8E3A),
	STORM_ROD("storm_rod", 0x6C7BFF),
	TIDE_PEARL("tide_pearl", 0x3FB6C9),
	GOLDEN_SCARAB("golden_scarab", 0xE8B32E),
	BLOOD_MOON("blood_moon", 0xC0392B),
	SUN_SHARD("sun_shard", 0xFFD84A),
	IRON_SKIN("iron_skin", 0xB8B8C0),
	GIANT_BONE("giant_bone", 0xEDE6D3),
	SWIFT_HOOF("swift_hoof", 0x8B5A2B),
	ENDER_SHARD("ender_shard", 0x2FB58F),
	MAGNET_STONE("magnet_stone", 0xD04848),
	BEAR_CLAW("bear_claw", 0xF2F2F2),
	MANTIS_BLADE("mantis_blade", 0x8FD14F),
	TURTLE_PLATE("turtle_plate", 0x4E9A56),
	HUNTER_MARK("hunter_mark", 0xE0642D),
	EXECUTIONER_AXE("executioner_axe", 0x5B5B66),
	SHIELD_CRYSTAL("shield_crystal", 0x7FC8FF),
	OWL_FEATHER("owl_feather", 0xA88B6A),
	MOLE_CLAW("mole_claw", 0x6A5040),
	COURAGE_BANNER("courage_banner", 0xD43F3F),
	ESSENCE_SIPHON("essence_siphon", 0x9B59D0),
	LIFEBLOOM("lifebloom", 0xF07FB0),
	OBSIDIAN_SHARD("obsidian_shard", 0x3A2552),
	QUAKE_STONE("quake_stone", 0x9C7A4E),
	THUNDER_DRUM("thunder_drum", 0xC08A3E),
	PURITY_CHARM("purity_charm", 0xF5F5FF),
	CALM_LOTUS("calm_lotus", 0xE9A3D9),
	CROW_FEATHER("crow_feather", 0x2A2A3A),
	FROST_STEP("frost_step", 0xBFEFFF);

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
