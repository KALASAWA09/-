package com.kalasawa.sacredsouls.skill;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import com.kalasawa.sacredsouls.spirit.Spirit;

/** Active skills: one per spirit, unlocked at spirit level 3, plus the Spirit King ultimate. */
public enum Skill {
	FIRE("fire", Spirit.FIRE, 90, 16),
	LIGHT("light", Spirit.LIGHT, 80, 20),
	WATER("water", Spirit.WATER, 80, 14),
	DEATH("death", Spirit.DEATH, 100, 16),
	DARKNESS("darkness", Spirit.DARKNESS, 110, 10),
	LIGHTNING("lightning", Spirit.LIGHTNING, 70, 10),
	ICE("ice", Spirit.ICE, 95, 16),
	BLOOD("blood", Spirit.BLOOD, 120, 10),
	KING("king", null, 25 * 60, 0);

	public static final int SHARED_COOLDOWN_TICKS = 80;

	public final String id;
	public final Spirit spirit;
	public final int cooldownTicks;
	/** Warning time before the skill lands. The ultimate reads its own from the config. */
	public final int castTicks;

	Skill(String id, Spirit spirit, int cooldownSeconds, int castTicks) {
		this.id = id;
		this.spirit = spirit;
		this.cooldownTicks = cooldownSeconds * 20;
		this.castTicks = castTicks;
	}

	public MutableComponent displayName() {
		return Component.translatable("skill.sacredsouls." + id);
	}

	public static Skill of(Spirit spirit) {
		for (Skill skill : values()) {
			if (skill.spirit == spirit) {
				return skill;
			}
		}
		return null;
	}

	public static Skill byId(String id) {
		for (Skill skill : values()) {
			if (skill.id.equals(id)) {
				return skill;
			}
		}
		return null;
	}
}
