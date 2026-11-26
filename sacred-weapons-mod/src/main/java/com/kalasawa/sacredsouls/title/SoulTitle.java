package com.kalasawa.sacredsouls.title;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The 9 titles. One holder per server; a player holds at most one. */
public enum SoulTitle {
	WAR_GOD("war_god"),
	REAPER("reaper"),
	HEIR("heir"),
	DEVOURER("devourer"),
	FALLEN_KING("fallen_king"),
	BERSERKER("berserker"),
	NAMELESS_SHADOW("nameless_shadow"),
	GUARDIAN("guardian"),
	BORDERLESS("borderless");

	public final String id;

	SoulTitle(String id) {
		this.id = id;
	}

	public MutableComponent displayName() {
		return Component.translatable("title.sacredsouls." + id);
	}

	/** How to earn it. */
	public MutableComponent condition() {
		return Component.translatable("title.sacredsouls." + id + ".condition");
	}

	/** The always-on buff. */
	public MutableComponent buff() {
		return Component.translatable("title.sacredsouls." + id + ".buff");
	}

	/** The chance or trigger ability. */
	public MutableComponent proc() {
		return Component.translatable("title.sacredsouls." + id + ".proc");
	}

	public static SoulTitle byId(String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		for (SoulTitle title : values()) {
			if (title.id.equals(id)) {
				return title;
			}
		}
		return null;
	}
}
