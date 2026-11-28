package com.kalasawa.sacredsouls.title;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
import com.kalasawa.sacredsouls.spirit.Spirit;

/**
 * Titles are earned by deeds. Each title has one holder on the server and a player holds at most one.
 * A holder who dies loses the title: their killer may take it, otherwise it is free for anyone to earn again.
 * A player who already holds a title is never made to swap: they are asked on the soul screen (K) and have
 * five minutes to accept or decline. Whatever they give up or turn down becomes free again.
 * A soulless body loses its title.
 */
public final class TitleManager {
	/** How many different soul items ผู้กลืนพันวิญญาณ needs (half of the 50). */
	public static final int DEVOURER_KINDS = 25;
	/** How long a player has to answer a title offer: five minutes. */
	public static final int OFFER_TICKS = 5 * 60 * 20;
	/** Titles each player has turned down, so they are not asked again and again. Cleared when their title changes. */
	private static final Map<UUID, Set<String>> DECLINED = new HashMap<>();

	private TitleManager() {
	}

	public static Map<String, String> holders(MinecraftServer server) {
		return server.overworld().getAttachedOrCreate(ModAttachments.TITLE_HOLDERS);
	}

	public static void setHolders(MinecraftServer server, Map<String, String> holders) {
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
		DECLINED.remove(player.getUUID());
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

	/** Frees the player's title (a soulless body, an admin command). */
	public static void releaseTitle(MinecraftServer server, ServerPlayer player, boolean announce) {
		clearOffer(player);
		SoulData data = SoulManager.get(player).copy();
		SoulTitle title = data.soulTitle();
		if (title == null) {
			return;
		}
		Map<String, String> holders = new HashMap<>(holders(server));
		holders.remove(title.id);
		setHolders(server, holders);
		DECLINED.remove(player.getUUID());
		data.title = "";
		SoulManager.set(player, data);
		if (announce) {
			announce(server, SoulManager.msg("title_free", title.displayName(), title.condition()));
		}
		recheckOthers(server, player);
	}

	/**
	 * A player died. Whatever title they held is lost. A player who killed them takes it (or is asked, if they
	 * already hold one); otherwise it is free for anyone to earn again. An open offer dies with them.
	 */
	public static void onDeath(ServerPlayer victim, ServerPlayer killer) {
		MinecraftServer server = SoulManager.server(victim);
		declineQuietly(victim);
		SoulTitle title = SoulManager.get(victim).soulTitle();
		if (title == null) {
			return;
		}
		SoulData victimData = SoulManager.get(victim).copy();
		victimData.title = "";
		SoulManager.set(victim, victimData);
		DECLINED.remove(victim.getUUID());
		Map<String, String> holders = new HashMap<>(holders(server));
		holders.remove(title.id);
		setHolders(server, holders);
		if (killer != null && !SoulManager.get(killer).isSoulless() && SoulManager.get(killer).soulTitle() == null) {
			grant(killer, title, victim);
			return;
		}
		announce(server, SoulManager.msg("title_lost", victim.getName(), title.displayName()));
		if (killer != null && !SoulManager.get(killer).isSoulless()) {
			offer(killer, title);
			return;
		}
		announce(server, SoulManager.msg("title_free", title.displayName(), title.condition()));
		recheckOthers(server, victim);
	}

	// ---------------------------------------------------------------- offers

	/** The title this player is being asked to take, or null. */
	public static SoulTitle offerOf(ServerPlayer player) {
		String offer = player.getAttached(ModAttachments.TITLE_OFFER);
		return offer == null ? null : SoulTitle.byId(offer.substring(0, Math.max(0, offer.indexOf('|'))));
	}

	/** The game time when the offer runs out. */
	public static long offerEnds(String offer) {
		try {
			return Long.parseLong(offer.substring(offer.indexOf('|') + 1));
		} catch (RuntimeException e) {
			return 0L;
		}
	}

	private static void clearOffer(ServerPlayer player) {
		player.removeAttached(ModAttachments.TITLE_OFFER);
	}

	/** Whether someone online is being asked to take this title right now. */
	private static boolean reserved(MinecraftServer server, SoulTitle title) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (offerOf(player) == title) {
				return true;
			}
		}
		return false;
	}

	/** Asks a player who already holds a title whether they want this one instead. */
	private static void offer(ServerPlayer player, SoulTitle title) {
		MinecraftServer server = SoulManager.server(player);
		SoulTitle current = SoulManager.get(player).soulTitle();
		if (current == null) {
			grant(player, title, null);
			return;
		}
		if (current == title || offerOf(player) != null) {
			// Already deciding on another one: this title simply stays free.
			announce(server, SoulManager.msg("title_free", title.displayName(), title.condition()));
			recheckOthers(server, player);
			return;
		}
		long ends = ((ServerLevel) player.level()).getGameTime() + OFFER_TICKS;
		player.setAttached(ModAttachments.TITLE_OFFER, title.id + "|" + ends);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.8f);
		player.displayClientMessage(SoulManager.msg("title_offer", title.displayName(), current.displayName())
				.withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), false);
	}

	/** The player takes the offered title; the one they held becomes free. */
	public static void accept(ServerPlayer player) {
		SoulTitle title = offerOf(player);
		clearOffer(player);
		if (title == null) {
			return;
		}
		MinecraftServer server = SoulManager.server(player);
		if (holders(server).containsKey(title.id) || SoulManager.get(player).isSoulless()) {
			return;
		}
		SoulTitle old = SoulManager.get(player).soulTitle();
		grant(player, title, null);
		if (old != null && old != title) {
			announce(server, SoulManager.msg("title_free", old.displayName(), old.condition()));
			recheckOthers(server, player);
		}
	}

	/** The player turns the offer down (or lets it run out); the title becomes free for others. */
	public static void decline(ServerPlayer player, boolean timedOut) {
		SoulTitle title = offerOf(player);
		clearOffer(player);
		if (title == null) {
			return;
		}
		MinecraftServer server = SoulManager.server(player);
		DECLINED.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(title.id);
		player.displayClientMessage(SoulManager.msg(timedOut ? "title_offer_expired" : "title_offer_declined", title.displayName())
				.withStyle(ChatFormatting.GRAY), false);
		if (!holders(server).containsKey(title.id)) {
			announce(server, SoulManager.msg("title_free", title.displayName(), title.condition()));
			recheckOthers(server, player);
		}
	}

	/** Drops an open offer without a word (the player died or left); the title is free again. */
	private static void declineQuietly(ServerPlayer player) {
		SoulTitle title = offerOf(player);
		clearOffer(player);
		if (title != null && !holders(SoulManager.server(player)).containsKey(title.id)) {
			MinecraftServer server = SoulManager.server(player);
			announce(server, SoulManager.msg("title_free", title.displayName(), title.condition()));
			recheckOthers(server, player);
		}
	}

	public static void onLeave(ServerPlayer player) {
		declineQuietly(player);
	}

	/** Runs out offers whose five minutes are up. Call every server tick. */
	public static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			String offer = player.getAttached(ModAttachments.TITLE_OFFER);
			if (offer != null && ((ServerLevel) player.level()).getGameTime() >= offerEnds(offer)) {
				decline(player, true);
			}
		}
	}

	/** A title just came free: anyone else online who has earned it may now get it, or be asked. */
	private static void recheckOthers(MinecraftServer server, ServerPlayer except) {
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other != except) {
				checkConditions(other);
			}
		}
	}

	private static boolean met(ServerPlayer player, SoulData data, SoulTitle title) {
		return switch (title) {
			case WAR_GOD -> data.killStreak >= 10;
			case REAPER -> data.soullessCaused >= 3;
			case HEIR -> false; // Needs the awakening ritual at the shrine (phase 2).
			case DEVOURER -> data.devoured.stream().filter(id -> !id.startsWith(SoulData.SPIRIT_PREFIX)).count() >= DEVOURER_KINDS;
			case SPIRIT_KING -> java.util.Arrays.stream(Spirit.values()).allMatch(s -> data.devoured.contains(SoulData.SPIRIT_PREFIX + s.id));
			case BERSERKER -> data.clutchWins >= 5;
			case NAMELESS_SHADOW -> data.invisKills >= 5;
			case GUARDIAN -> player.getStats().getValue(Stats.CUSTOM.get(Stats.RAID_WIN)) >= 5;
			case BORDERLESS -> Integer.bitCount(data.dimensions) >= 3 && data.distance >= 10_000L;
		};
	}

	/**
	 * Gives the player the first free title whose condition they meet. A player who holds none gets it at once;
	 * a player who holds one is asked first, on the soul screen.
	 */
	public static void checkConditions(ServerPlayer player) {
		SoulData data = SoulManager.get(player);
		if (data.isSoulless() || offerOf(player) != null) {
			return;
		}
		MinecraftServer server = SoulManager.server(player);
		Map<String, String> holders = holders(server);
		SoulTitle current = data.soulTitle();
		Set<String> declined = DECLINED.getOrDefault(player.getUUID(), Set.of());
		for (SoulTitle title : SoulTitle.values()) {
			if (title == current || holders.containsKey(title.id) || reserved(server, title) || !met(player, data, title)) {
				continue;
			}
			if (current == null) {
				grant(player, title, null);
				return;
			}
			if (!declined.contains(title.id)) {
				offer(player, title);
				return;
			}
		}
	}

	/** On join: drops a title this player still remembers but no longer holds (taken by an admin while offline). */
	public static void reconcile(ServerPlayer player) {
		SoulData data = SoulManager.get(player);
		SoulTitle title = data.soulTitle();
		if (title != null && !player.getUUID().toString().equals(holders(SoulManager.server(player)).get(title.id))) {
			SoulData copy = data.copy();
			copy.title = "";
			SoulManager.set(player, copy);
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
