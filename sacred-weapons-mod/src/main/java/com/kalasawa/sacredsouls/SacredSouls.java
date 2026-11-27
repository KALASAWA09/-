package com.kalasawa.sacredsouls;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.kalasawa.sacredsouls.command.SoulCommands;
import com.kalasawa.sacredsouls.item.ModItems;
import com.kalasawa.sacredsouls.network.AirJumpPayload;
import com.kalasawa.sacredsouls.network.SkillCastPayload;
import com.kalasawa.sacredsouls.network.SkillCooldownPayload;
import com.kalasawa.sacredsouls.network.SoulActionPayload;
import com.kalasawa.sacredsouls.power.PowerHandler;
import com.kalasawa.sacredsouls.skill.BlockChanges;
import com.kalasawa.sacredsouls.skill.SkillConfig;
import com.kalasawa.sacredsouls.skill.SkillHandler;
import com.kalasawa.sacredsouls.soul.LootSources;
import com.kalasawa.sacredsouls.soul.ModAttachments;
import com.kalasawa.sacredsouls.soul.SoulManager;
import com.kalasawa.sacredsouls.title.TitleManager;

public class SacredSouls implements ModInitializer {
	public static final String MOD_ID = "sacredsouls";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final PowerHandler POWERS = new PowerHandler();

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModAttachments.init();
		ModItems.init();
		LootSources.init();
		SoulCommands.init();
		SkillConfig.load();

		PayloadTypeRegistry.playC2S().register(SoulActionPayload.TYPE, SoulActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AirJumpPayload.TYPE, AirJumpPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(SkillCastPayload.TYPE, SkillCastPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(SkillCooldownPayload.TYPE, SkillCooldownPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SoulActionPayload.TYPE, (payload, context) -> {
			if (payload.action() == SoulActionPayload.UPGRADE) {
				SoulManager.upgrade(context.player(), payload.slot());
			} else if (payload.action() == SoulActionPayload.EJECT) {
				SoulManager.eject(context.player(), payload.slot());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(AirJumpPayload.TYPE, (payload, context) -> POWERS.onAirJump(context.player()));

		ServerPlayNetworking.registerGlobalReceiver(SkillCastPayload.TYPE, (payload, context) -> SkillHandler.tryCast(context.player(), payload.skill()));

		ServerTickEvents.END_SERVER_TICK.register(POWERS::tick);
		ServerTickEvents.END_SERVER_TICK.register(SkillHandler::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> BlockChanges.restoreAll());
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(POWERS::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) ->
				POWERS.afterDamage(entity, source, damageTaken));
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> POWERS.allowDeath(entity, source));
		ServerLivingEntityEvents.AFTER_DEATH.register(POWERS::afterDeath);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
				SoulManager.updateSoullessHealth(newPlayer, SoulManager.get(newPlayer)));
		ServerPlayerEvents.JOIN.register(player -> {
			SoulManager.updateSoullessHealth(player, SoulManager.get(player));
			TitleManager.reconcile(player);
			TitleManager.checkConditions(player);
			SkillHandler.sync(player);
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			POWERS.onLeave(player);
			SkillHandler.onLeave(player);
		});

		LOGGER.info("Sacred Souls loaded");
	}
}
