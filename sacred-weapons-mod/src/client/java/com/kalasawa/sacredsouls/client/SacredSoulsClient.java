package com.kalasawa.sacredsouls.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.kalasawa.sacredsouls.SacredSouls;
import com.kalasawa.sacredsouls.item.SoulItem;
import com.kalasawa.sacredsouls.network.AirJumpPayload;
import com.kalasawa.sacredsouls.power.PowerHandler;
import com.kalasawa.sacredsouls.power.SoulPower;
import com.kalasawa.sacredsouls.soul.ModAttachments;
import com.kalasawa.sacredsouls.soul.SoulData;

public class SacredSoulsClient implements ClientModInitializer {
	private static KeyMapping openSoul;
	private boolean jumpWasDown;
	private int airJumpsUsed;

	public static SoulData localSoul() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return SoulData.empty();
		}
		SoulData data = player.getAttached(ModAttachments.SOUL);
		return data == null ? SoulData.empty() : data;
	}

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(SacredSouls.id("souls"));
		openSoul = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.sacredsouls.open_soul", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				return;
			}
			while (openSoul.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new SoulScreen());
				}
			}
			tickAirJump(client, client.player);
		});

		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (stack.getItem() instanceof SoulItem soulItem) {
				lines.add(Component.literal(soulItem.power.description).withStyle(ChatFormatting.GRAY));
				lines.add(Component.literal("คลิกขวาค้างเพื่อให้วิญญาณกลืน").withStyle(ChatFormatting.DARK_PURPLE));
			}
		});
	}

	/** ขนนกลม: press jump again while in the air. Movement is client-side, the server checks the count. */
	private void tickAirJump(Minecraft client, LocalPlayer player) {
		boolean jumpDown = client.options.keyJump.isDown();
		boolean pressed = jumpDown && !jumpWasDown;
		jumpWasDown = jumpDown;
		if (player.onGround()) {
			airJumpsUsed = 0;
			return;
		}
		if (!pressed || client.screen != null || player.getAbilities().flying || player.isInWater() || player.onClimbable() || player.isPassenger()) {
			return;
		}
		SoulData data = localSoul();
		int feather = data.isSoulless() ? 0 : data.level(SoulPower.WIND_FEATHER);
		if (feather <= 0) {
			return;
		}
		int max = PowerHandler.maxAirJumps(player.level(), player, feather, data.level(SoulPower.BAT_WING) > 0);
		if (airJumpsUsed >= max) {
			return;
		}
		airJumpsUsed++;
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(motion.x, 0.42 + 0.08 * feather, motion.z);
		player.resetFallDistance();
		ClientPlayNetworking.send(AirJumpPayload.INSTANCE);
	}
}
