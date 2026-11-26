package com.kalasawa.sacredsouls.soul;

import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.kalasawa.sacredsouls.SacredSouls;
import com.kalasawa.sacredsouls.item.ModItems;
import com.kalasawa.sacredsouls.power.SoulPower;
import com.kalasawa.sacredsouls.title.SoulTitle;
import com.kalasawa.sacredsouls.title.TitleManager;

/** All changes to a player's soul go through here so the attachment is always re-set (and re-synced). */
public final class SoulManager {
	private static final net.minecraft.resources.Identifier SOULLESS_HEALTH = SacredSouls.id("soulless_health");

	private SoulManager() {
	}

	public static SoulData get(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.SOUL);
	}

	public static void set(ServerPlayer player, SoulData data) {
		player.setAttached(ModAttachments.SOUL, data);
	}

	/** A translatable chat message from lang key msg.sacredsouls.<key>. */
	public static MutableComponent msg(String key, Object... args) {
		return Component.translatable("msg.sacredsouls." + key, args);
	}

	private static void say(ServerPlayer player, Component message) {
		player.displayClientMessage(message, false);
	}

	private static void effects(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.8f, 0.6f);
		level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
	}

	/** Returns true when the item was used up. */
	public static boolean devour(ServerPlayer player, SoulPower power) {
		SoulData data = get(player).copy();
		if (data.isSoulless()) {
			say(player, msg("soulless_cant_devour").withStyle(ChatFormatting.RED));
			return false;
		}
		int index = data.slots.indexOf(power.id);
		int gain = 0;
		if (index >= 0) {
			gain = essenceGain(data, 5);
			data.essence += gain;
		} else if (data.slots.size() < SoulData.MAX_SLOTS) {
			data.slots.add(power.id);
			data.levels.add(1);
		} else {
			say(player, msg("slots_full").withStyle(ChatFormatting.RED));
			return false;
		}
		if (!data.devoured.contains(power.id)) {
			data.devoured.add(power.id);
		}
		set(player, data);
		effects(player);
		if (gain > 0) {
			say(player, msg("devour_again", power.displayName(), gain).withStyle(ChatFormatting.AQUA));
		} else {
			say(player, msg("devour_new", power.displayName(), power.summary()).withStyle(ChatFormatting.AQUA));
		}
		TitleManager.checkConditions(player);
		return true;
	}

	public static int essenceGain(SoulData data, int base) {
		int gain = data.soulTitle() == SoulTitle.DEVOURER ? base + base / 2 : base;
		// ไส้กรองแก่น: +1/+2/+3 on every gain.
		return data.isSoulless() ? gain : gain + data.level(SoulPower.ESSENCE_SIPHON);
	}

	public static void upgrade(ServerPlayer player, int slot) {
		SoulData data = get(player).copy();
		SoulPower power = data.powerAt(slot);
		if (power == null || data.isSoulless()) {
			return;
		}
		int level = data.levels.get(slot);
		if (level >= SoulData.MAX_LEVEL) {
			say(player, msg("max_level", power.displayName()).withStyle(ChatFormatting.GRAY));
			return;
		}
		int cost = SoulData.upgradeCost(level);
		if (data.essence < cost) {
			say(player, msg("not_enough_essence", cost, data.essence).withStyle(ChatFormatting.RED));
			return;
		}
		data.essence -= cost;
		data.levels.set(slot, level + 1);
		set(player, data);
		ServerLevel serverLevel = (ServerLevel) player.level();
		serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
		serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.8, 0.5, 0.03);
		say(player, msg("upgraded", power.displayName(), level + 1, power.effect(level + 1)).withStyle(ChatFormatting.GOLD));
	}

	public static void eject(ServerPlayer player, int slot) {
		SoulData data = get(player).copy();
		SoulPower power = data.powerAt(slot);
		if (power == null) {
			return;
		}
		int waterSlot = -1;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(ModItems.CLEANSING_WATER)) {
				waterSlot = i;
				break;
			}
		}
		if (waterSlot < 0 && !player.isCreative()) {
			say(player, msg("need_water").withStyle(ChatFormatting.RED));
			return;
		}
		if (waterSlot >= 0) {
			player.getInventory().getItem(waterSlot).shrink(1);
		}
		data.slots.remove(slot);
		data.levels.remove(slot);
		set(player, data);
		SacredSouls.POWERS.onPowerRemoved(player, power);
		say(player, msg("ejected", power.displayName()).withStyle(ChatFormatting.YELLOW));
	}

	public static boolean useSoulFlame(ServerPlayer player) {
		SoulData data = get(player).copy();
		if (data.isSoulless()) {
			say(player, msg("flame_soulless").withStyle(ChatFormatting.RED));
			return false;
		}
		if (data.energy >= SoulData.MAX_ENERGY) {
			say(player, msg("energy_full").withStyle(ChatFormatting.GRAY));
			return false;
		}
		data.energy++;
		set(player, data);
		effects(player);
		say(player, msg("energy", data.energy).withStyle(ChatFormatting.AQUA));
		return true;
	}

	public static boolean useIncense(ServerPlayer player) {
		SoulData data = get(player).copy();
		if (!data.isSoulless()) {
			say(player, msg("incense_not_needed").withStyle(ChatFormatting.GRAY));
			return false;
		}
		data.energy = 3;
		set(player, data);
		updateSoullessHealth(player, data);
		effects(player);
		server(player).getPlayerList().broadcastSystemMessage(
				msg("soul_returned", player.getName()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
		return true;
	}

	/** Changes soul energy and handles the soulless body at 0. */
	public static void changeEnergy(ServerPlayer player, int delta) {
		SoulData data = get(player).copy();
		boolean wasSoulless = data.isSoulless();
		data.energy = Math.max(0, Math.min(SoulData.MAX_ENERGY, data.energy + delta));
		set(player, data);
		if (!wasSoulless && data.isSoulless()) {
			TitleManager.releaseTitle(server(player), player, true);
			server(player).getPlayerList().broadcastSystemMessage(
					msg("became_soulless", player.getName()).withStyle(ChatFormatting.DARK_RED), false);
		}
		updateSoullessHealth(player, get(player));
	}

	public static void updateSoullessHealth(ServerPlayer player, SoulData data) {
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return;
		}
		if (data.isSoulless()) {
			health.addOrUpdateTransientModifier(new AttributeModifier(SOULLESS_HEALTH, -6.0, AttributeModifier.Operation.ADD_VALUE));
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
		} else {
			health.removeModifier(SOULLESS_HEALTH);
		}
	}

	/** PvP kill bookkeeping: energy, essence, streaks and title conditions. */
	public static void onPlayerKill(ServerPlayer killer, ServerPlayer victim) {
		SoulData killerData = get(killer).copy();
		killerData.essence += essenceGain(killerData, 3);
		killerData.killStreak++;
		if (killer.getHealth() < 4.0f) {
			killerData.clutchWins++;
		}
		if (killer.hasEffect(Effects.get("invisibility"))) {
			killerData.invisKills++;
		}
		boolean victimHadSoul = !get(victim).isSoulless();
		set(killer, killerData);

		SoulData victimData = get(victim).copy();
		victimData.killStreak = 0;
		set(victim, victimData);

		TitleManager.onKill(killer, victim);
		changeEnergy(victim, -1);
		changeEnergy(killer, 1);

		if (victimHadSoul && get(victim).isSoulless()) {
			SoulData again = get(killer).copy();
			again.soullessCaused++;
			set(killer, again);
		}
		TitleManager.checkConditions(killer);
	}

	public static void showCodex(ServerPlayer viewer) {
		MinecraftServer server = server(viewer);
		say(viewer, msg("codex_header").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			SoulData data = get(player);
			MutableComponent line = Component.literal(player.getName().getString()).withStyle(ChatFormatting.WHITE);
			line.append(Component.literal(" | ").append(msg("energy", data.energy)).withStyle(ChatFormatting.AQUA));
			SoulTitle title = data.soulTitle();
			if (title != null) {
				line.append(Component.literal(" | ").append(title.displayName()).withStyle(ChatFormatting.GOLD));
			}
			MutableComponent powers = Component.empty();
			for (int i = 0; i < data.slots.size(); i++) {
				SoulPower power = data.powerAt(i);
				if (power != null) {
					powers.append(i == 0 ? " | " : ", ").append(power.displayName()).append(" " + data.levels.get(i));
				}
			}
			line.append(powers.withStyle(ChatFormatting.GRAY));
			say(viewer, line);
		}
		Map<String, String> holders = TitleManager.holders(server);
		MutableComponent titles = msg("codex_free_titles").withStyle(ChatFormatting.GOLD);
		boolean any = false;
		for (SoulTitle title : SoulTitle.values()) {
			if (!holders.containsKey(title.id)) {
				titles.append(Component.literal(any ? ", " : "").append(title.displayName()).withStyle(ChatFormatting.YELLOW));
				any = true;
			}
		}
		say(viewer, any ? titles : msg("codex_no_free_titles").withStyle(ChatFormatting.GOLD));
		say(viewer, msg("codex_sources").withStyle(ChatFormatting.DARK_AQUA));
	}

	public static MinecraftServer server(ServerPlayer player) {
		return ((ServerLevel) player.level()).getServer();
	}
}
