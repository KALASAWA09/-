package com.kalasawa.sacredsouls.spirit;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The 8 elemental spirits. A player can bind one, in the 6th (spirit) slot. */
public enum Spirit {
	FIRE("fire", 0xFF7A2A),
	LIGHT("light", 0xFFF2A0),
	WATER("water", 0x3A9AFF),
	DEATH("death", 0xE8E0C8),
	EARTH("earth", 0x7AC04A),
	DARKNESS("darkness", 0x8A4AD0),
	LIGHTNING("lightning", 0xFFE14A),
	STONE("stone", 0xA8A8B4);

	public final String id;
	public final int color;

	Spirit(String id, int color) {
		this.id = id;
		this.color = color;
	}

	public String itemId() {
		return "spirit_" + id;
	}

	public MutableComponent displayName() {
		return Component.translatable("item.sacredsouls." + itemId());
	}

	public MutableComponent summary() {
		return Component.translatable("spirit.sacredsouls." + id + ".summary");
	}

	public MutableComponent effect(int level) {
		return Component.translatable("spirit.sacredsouls." + id + "." + level);
	}

	public static Spirit byId(String id) {
		for (Spirit spirit : values()) {
			if (spirit.id.equals(id)) {
				return spirit;
			}
		}
		return null;
	}
}
