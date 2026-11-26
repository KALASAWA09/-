package com.kalasawa.sacredsouls.title;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;

import com.kalasawa.sacredsouls.power.SoulPower;
import com.kalasawa.sacredsouls.soul.ModAttachments;
import com.kalasawa.sacredsouls.soul.SoulData;
import com.kalasawa.sacredsouls.soul.SoulManager;

/**
 * Titles are earned by deeds. Each title has one holder on the server, a player holds at most one,
 * killing a holder takes their title, and a soulless body loses its title.
 */
public final class TitleManager {
	private TitleManager() {
	}

	public static Map<String, String> holders(MinecraftServer server) {
		return server.overworld().getAttachedOrCreate(ModAttachments.TITLE_HOLDERS);
	}

	private static void setHolders(MinecraftServer server, Map<String, String> holders) {
		server.overworld().setAttached(ModAttachments.TITLE_HOLDERS, Map.copyOf(holders));
	}

	private static void announce(MinecraftServer server, MutableComponent text) {
		server.getPlayerList().broadcastSystemMessage(text.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
	}

	public static void grant(ServerPlayer player, SoulTitle title, ServerPlayer takenFrom) {
		MinecraftServer server = SoulManager.server(player);
		Map<String, String> holders = new HashMap<>(holders(server));
		SoulData data = SoulManager.get(player).copy();
		SoulTitle old = data.soulTitle();
		if (old != null) {
			holders.remove(old.id);
		}
		holders.put(title.id, player.getUUID().toString());
		setHolders(server, holders);
		data.title = title.id;
		if (!data.titlesHeld.contains(title.id)) {
			data.titlesHeld.add(title.id);
		}
		SoulManager.set(player, data);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
		announce(server, takenFrom == null
				? SoulManager.msg("title_earned", player.getName(), title.displayName())
				: SoulManager.msg("title_taken", player.getName(), title.displayName(), takenFrom.getName()));
		player.displayClientMessage(SoulManager.msg("title_bonus", title.buff(), title.proc()).withStyle(ChatFormatting.YELLOW), false);
		checkConditions(player);
	}

	/** Frees the player's title. */
	public static void releaseTitle(MinecraftServer server, ServerPlayer player, boolean announce) {
		SoulData data = SoulManager.get(player).copy();
		SoulTitle title = data.soulTitle();
		if (title == null) {
			return;
		}
		Map<String, String> holders = new HashMap<>(holders(server));
		holders.remove(title.id);
		setHolders(server, holders);
		data.title = "";
		SoulManager.set(player, data);
		if (announce) {
			announce(server, SoulManager.msg("title_free", title.displayName(), title.condition()));
		}
	}

	/** The killer takes the victim's title. */
	public static void onKill(ServerPlayer killer, ServerPlayer victim) {
		SoulTitle title = SoulManager.get(victim).soulTitle();
		if (title == null) {
			return;
		}
		MinecraftServer server = SoulManager.server(killer);
		SoulData victimData = SoulManager.get(victim).copy();
		victimData.title = "";
		SoulManager.set(victim, victimData);
		Map<String, String> holders = new HashMap<>(holders(server));
		holders.remove(title.id);
		setHolders(server, holders);
		SoulTitle killerOld = SoulManager.get(killer).soulTitle();
		if (killerOld != null && killerOld != title) {
			announce(server, SoulManager.msg("title_dropped", killer.getName(), killerOld.displayName()));
		}
		grant(killer, title, victim);
	}

	private static boolean met(ServerPlayer player, SoulData data, SoulTitle title) {
		return switch (title) {
			case WAR_GOD -> data.killStreak >= 10;
			case REAPER -> data.soullessCaused >= 3;
			case HEIR -> false; // Needs the awakening ritual at the shrine (phase 2).
			case DEVOURER -> data.devoured.size() >= SoulPower.values().length;
			case FALLEN_KING -> {
				int others = 0;
				for (String held : data.titlesHeld) {
					if (!held.equals(SoulTitle.FALLEN_KING.id)) {
						others++;
					}
				}
				yield others >= 3;
			}
			case BERSERKER -> data.clutchWins >= 5;
			case NAMELESS_SHADOW -> data.invisKills >= 5;
			case GUARDIAN -> player.getStats().getValue(Stats.CUSTOM.get(Stats.RAID_WIN)) >= 5;
			case BORDERLESS -> Integer.bitCount(data.dimensions) >= 3 && data.distance >= 10_000L;
		};
	}

	/** Grants the first vacant title whose condition the player meets, if they hold none. */
	public static void checkConditions(ServerPlayer player) {
		SoulData data = SoulManager.get(player);
		if (data.isSoulless() || data.soulTitle() != null) {
			return;
		}
		Map<String, String> holders = holders(SoulManager.server(player));
		for (SoulTitle title : SoulTitle.values()) {
			if (!holders.containsKey(title.id) && met(player, data, title)) {
				grant(player, title, null);
				return;
			}
		}
	}

	/** Clears titles whose holder data no longer agrees (for example after a player data reset). */
	public static void validate(MinecraftServer server) {
		Map<String, String> holders = new HashMap<>(holders(server));
		boolean changed = false;
		for (Map.Entry<String, String> entry : Map.copyOf(holders).entrySet()) {
			ServerPlayer holder = server.getPlayerList().getPlayer(UUID.fromString(entry.getValue()));
			if (holder != null && !entry.getKey().equals(SoulManager.get(holder).title)) {
				holders.remove(entry.getKey());
				changed = true;
			}
		}
		if (changed) {
			setHolders(server, holders);
		}
	}
}
