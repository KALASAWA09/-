package com.kalasawa.sacredsouls.soul;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.kalasawa.sacredsouls.power.SoulPower;
import com.kalasawa.sacredsouls.spirit.Spirit;
import com.kalasawa.sacredsouls.title.SoulTitle;

/**
 * Everything the soul inside a player remembers. Stored on the player as a persistent,
 * synced attachment. Treat instances as values: change a {@link #copy()} and set it back.
 */
public final class SoulData {
	public static final int MAX_SLOTS = 5;
	/** Slot index used for the spirit slot in the soul screen and network actions. */
	public static final int SPIRIT_SLOT = MAX_SLOTS;
	public static final int MAX_LEVEL = 3;
	public static final int MAX_ENERGY = 10;
	public static final int START_ENERGY = 5;

	public static final Codec<SoulData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.listOf().optionalFieldOf("slots", List.of()).forGetter(d -> d.slots),
			Codec.INT.listOf().optionalFieldOf("levels", List.of()).forGetter(d -> d.levels),
			Codec.INT.optionalFieldOf("essence", 0).forGetter(d -> d.essence),
			Codec.INT.optionalFieldOf("energy", START_ENERGY).forGetter(d -> d.energy),
			Codec.STRING.optionalFieldOf("title", "").forGetter(d -> d.title),
			Codec.INT.optionalFieldOf("kill_streak", 0).forGetter(d -> d.killStreak),
			Codec.INT.optionalFieldOf("soulless_caused", 0).forGetter(d -> d.soullessCaused),
			Codec.INT.optionalFieldOf("clutch_wins", 0).forGetter(d -> d.clutchWins),
			Codec.INT.optionalFieldOf("invis_kills", 0).forGetter(d -> d.invisKills),
			Codec.LONG.optionalFieldOf("distance", 0L).forGetter(d -> d.distance),
			Codec.INT.optionalFieldOf("dimensions", 0).forGetter(d -> d.dimensions),
			Codec.STRING.listOf().optionalFieldOf("titles_held", List.of()).forGetter(d -> d.titlesHeld),
			Codec.STRING.listOf().optionalFieldOf("devoured", List.of()).forGetter(d -> d.devoured),
			Codec.INT.optionalFieldOf("raid_wins_seen", -1).forGetter(d -> d.raidWinsSeen),
			Codec.INT.optionalFieldOf("bred_seen", -1).forGetter(d -> d.bredSeen),
			Codec.STRING.optionalFieldOf("spirit", "").forGetter(d -> d.spirit)
	).apply(instance, SoulData::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, SoulData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

	public final List<String> slots;
	public final List<Integer> levels;
	public int essence;
	public int energy;
	public String title;
	public int killStreak;
	public int soullessCaused;
	public int clutchWins;
	public int invisKills;
	public long distance;
	public int dimensions;
	public final List<String> titlesHeld;
	public final List<String> devoured;
	public int raidWinsSeen;
	public int bredSeen;
	/** The bound spirit as "id:level", or "" when the spirit slot is empty. */
	public String spirit;

	public SoulData(List<String> slots, List<Integer> levels, int essence, int energy, String title,
			int killStreak, int soullessCaused, int clutchWins, int invisKills, long distance, int dimensions,
			List<String> titlesHeld, List<String> devoured, int raidWinsSeen, int bredSeen, String spirit) {
		this.slots = new ArrayList<>(slots);
		this.levels = new ArrayList<>(levels);
		while (this.levels.size() < this.slots.size()) {
			this.levels.add(1);
		}
		this.essence = essence;
		this.energy = energy;
		this.title = title;
		this.killStreak = killStreak;
		this.soullessCaused = soullessCaused;
		this.clutchWins = clutchWins;
		this.invisKills = invisKills;
		this.distance = distance;
		this.dimensions = dimensions;
		this.titlesHeld = new ArrayList<>(titlesHeld);
		this.devoured = new ArrayList<>(devoured);
		this.raidWinsSeen = raidWinsSeen;
		this.bredSeen = bredSeen;
		this.spirit = spirit;
	}

	public static SoulData empty() {
		return new SoulData(List.of(), List.of(), 0, START_ENERGY, "", 0, 0, 0, 0, 0L, 0, List.of(), List.of(), -1, -1, "");
	}

	public SoulData copy() {
		return new SoulData(slots, levels, essence, energy, title, killStreak, soullessCaused, clutchWins, invisKills,
				distance, dimensions, titlesHeld, devoured, raidWinsSeen, bredSeen, spirit);
	}

	public boolean isSoulless() {
		return energy <= 0;
	}

	/** Level 1-3 of the power, or 0 when it is not devoured. */
	public int level(SoulPower power) {
		int index = slots.indexOf(power.id);
		return index < 0 ? 0 : levels.get(index);
	}

	public SoulPower powerAt(int slot) {
		return slot >= 0 && slot < slots.size() ? SoulPower.byId(slots.get(slot)) : null;
	}

	/** The bound spirit, or null. */
	public Spirit spirit() {
		int colon = spirit.indexOf(':');
		return Spirit.byId(colon < 0 ? spirit : spirit.substring(0, colon));
	}

	/** Level 1-3 of the bound spirit, or 0. */
	public int spiritLevel() {
		int colon = spirit.indexOf(':');
		if (spirit() == null || colon < 0) {
			return 0;
		}
		try {
			return Math.max(1, Math.min(MAX_LEVEL, Integer.parseInt(spirit.substring(colon + 1))));
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	public void setSpirit(Spirit value, int level) {
		spirit = value == null ? "" : value.id + ":" + level;
	}

	public SoulTitle soulTitle() {
		return SoulTitle.byId(title);
	}

	public boolean allMaxed() {
		if (slots.size() < MAX_SLOTS) {
			return false;
		}
		for (int level : levels) {
			if (level < MAX_LEVEL) {
				return false;
			}
		}
		return true;
	}

	public static int upgradeCost(int currentLevel) {
		return switch (currentLevel) {
			case 1 -> 8;
			case 2 -> 20;
			default -> Integer.MAX_VALUE;
		};
	}
}
