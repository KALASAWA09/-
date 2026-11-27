package com.kalasawa.sacredsouls.command;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import com.kalasawa.sacredsouls.power.SoulPower;
import com.kalasawa.sacredsouls.soul.SoulData;
import com.kalasawa.sacredsouls.soul.SoulManager;
import com.kalasawa.sacredsouls.title.SoulTitle;
import com.kalasawa.sacredsouls.title.TitleManager;

/**
 * Admin commands (op level 2):
 * /soul info [player], /soul title give|clear, /soul energy set, /soul essence add.
 */
public final class SoulCommands {
	private SoulCommands() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("soul")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("info")
						.executes(ctx -> info(ctx.getSource(), ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player())
								.executes(ctx -> info(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("title")
						.then(Commands.literal("give")
								.then(Commands.argument("player", EntityArgument.player())
										.then(Commands.argument("title", StringArgumentType.word())
												.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
														java.util.Arrays.stream(SoulTitle.values()).map(t -> t.id), builder))
												.executes(SoulCommands::giveTitle))))
						.then(Commands.literal("clear")
								.then(Commands.argument("player", EntityArgument.player())
										.executes(SoulCommands::clearTitle))))
				.then(Commands.literal("energy")
						.then(Commands.literal("set")
								.then(Commands.argument("player", EntityArgument.player())
										.then(Commands.argument("amount", IntegerArgumentType.integer(0, SoulData.MAX_ENERGY))
												.executes(SoulCommands::setEnergy)))))
				.then(Commands.literal("essence")
						.then(Commands.literal("add")
								.then(Commands.argument("player", EntityArgument.player())
										.then(Commands.argument("amount", IntegerArgumentType.integer())
												.executes(SoulCommands::addEssence)))))
				.then(Commands.literal("cooldown")
						.then(Commands.literal("reset")
								.executes(ctx -> resetCooldowns(ctx.getSource(), ctx.getSource().getPlayerOrException()))
								.then(Commands.argument("player", EntityArgument.player())
										.executes(ctx -> resetCooldowns(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))));
	}

	private static int resetCooldowns(CommandSourceStack source, ServerPlayer player) {
		com.kalasawa.sacredsouls.skill.SkillHandler.resetCooldowns(player);
		source.sendSuccess(() -> msg("cooldown_reset", player.getName()), true);
		return 1;
	}

	private static MutableComponent msg(String key, Object... args) {
		return SoulManager.msg("cmd." + key, args);
	}

	private static int info(CommandSourceStack source, ServerPlayer player) {
		SoulData data = SoulManager.get(player);
		SoulTitle title = data.soulTitle();
		source.sendSuccess(() -> msg("info_header", player.getName()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), false);
		source.sendSuccess(() -> msg("info_stats", data.energy, SoulData.MAX_ENERGY, data.essence,
				title == null ? msg("none") : title.displayName(), data.devoured.size()).withStyle(ChatFormatting.GRAY), false);
		if (data.slots.isEmpty()) {
			source.sendSuccess(() -> msg("info_no_slots").withStyle(ChatFormatting.GRAY), false);
		}
		for (int i = 0; i < data.slots.size(); i++) {
			SoulPower power = data.powerAt(i);
			int slot = i + 1;
			int level = data.levels.get(i);
			Component name = power == null ? Component.literal(data.slots.get(i)) : power.displayName();
			source.sendSuccess(() -> msg("info_slot", slot, name, level).withStyle(ChatFormatting.AQUA), false);
		}
		return 1;
	}

	private static int giveTitle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String id = StringArgumentType.getString(ctx, "title");
		SoulTitle title = SoulTitle.byId(id);
		if (title == null) {
			ctx.getSource().sendFailure(msg("unknown_title", id));
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		// Take the title away from whoever holds it now.
		String holder = TitleManager.holders(server).get(title.id);
		if (holder != null && !holder.equals(player.getUUID().toString())) {
			ServerPlayer old = server.getPlayerList().getPlayer(UUID.fromString(holder));
			if (old != null) {
				TitleManager.releaseTitle(server, old, false);
			} else {
				Map<String, String> holders = new HashMap<>(TitleManager.holders(server));
				holders.remove(title.id);
				TitleManager.setHolders(server, holders);
			}
		}
		TitleManager.grant(player, title, null);
		ctx.getSource().sendSuccess(() -> msg("title_given", title.displayName(), player.getName()), true);
		return 1;
	}

	private static int clearTitle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		SoulTitle title = SoulManager.get(player).soulTitle();
		if (title == null) {
			ctx.getSource().sendFailure(msg("no_title", player.getName()));
			return 0;
		}
		TitleManager.releaseTitle(ctx.getSource().getServer(), player, true);
		ctx.getSource().sendSuccess(() -> msg("title_cleared", title.displayName(), player.getName()), true);
		return 1;
	}

	private static int setEnergy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		int amount = IntegerArgumentType.getInteger(ctx, "amount");
		SoulManager.changeEnergy(player, amount - SoulManager.get(player).energy);
		ctx.getSource().sendSuccess(() -> msg("energy_set", player.getName(), amount), true);
		return 1;
	}

	private static int addEssence(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		int amount = IntegerArgumentType.getInteger(ctx, "amount");
		SoulData data = SoulManager.get(player).copy();
		data.essence = Math.max(0, data.essence + amount);
		SoulManager.set(player, data);
		int total = data.essence;
		ctx.getSource().sendSuccess(() -> msg("essence_added", amount, player.getName(), total), true);
		return 1;
	}
}
