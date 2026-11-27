package com.kalasawa.sacredsouls.skill;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import com.kalasawa.sacredsouls.SacredSouls;

/** The mod's own sounds, made for the Spirit King ultimate. Files live in assets/sacredsouls/sounds. */
public final class ModSounds {
	/** A rising drone of low strings, wind and a distant choir, twelve seconds long. */
	public static final SoundEvent KING_DRONE = register("king.drone");
	/** A deep funeral bell. */
	public static final SoundEvent KING_TOLL = register("king.toll");
	/** The blast: a falling sub-bass thump, a crack and a long rumble. */
	public static final SoundEvent KING_BOOM = register("king.boom");
	/** A rising swell that ends at the instant of the blast. */
	public static final SoundEvent KING_CHARGE = register("king.charge");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = SacredSouls.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
		// Loading the class registers the sounds.
	}
}
