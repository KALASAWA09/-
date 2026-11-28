package com.kalasawa.sacredsouls.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import com.kalasawa.sacredsouls.SacredSouls;
import com.kalasawa.sacredsouls.item.ModItems;
import com.kalasawa.sacredsouls.network.SkillCastPayload;
import com.kalasawa.sacredsouls.network.SkillCooldownPayload;
import com.kalasawa.sacredsouls.skill.Skill;
import com.kalasawa.sacredsouls.skill.SkillHandler;

/** The skill key: tap to cast the chosen skill, hold to open the wheel. Also the small cooldown icon on the HUD. */
public final class SkillClient {
	/** Ticks the key must be held before the wheel opens. */
	private static final int HOLD_TICKS = 5;

	static KeyMapping key;
	static Skill selected;
	private static final Map<String, Long> COOLDOWN_END = new HashMap<>();
	private static int held;
	private static boolean wasDown;

	private SkillClient() {
	}

	static void init(KeyMapping.Category category) {
		key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.sacredsouls.skill", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, category));
		ClientPlayNetworking.registerGlobalReceiver(SkillCooldownPayload.TYPE, (payload, context) -> readCooldowns(payload.cooldowns()));
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, SacredSouls.id("skill_icon"), SkillClient::renderHud);
	}

	static List<Skill> available() {
		return SkillHandler.available(SacredSoulsClient.localSoul());
	}

	/** The skill a tap will cast: the one picked on the wheel, or the first one available. */
	static Skill current() {
		List<Skill> skills = available();
		if (selected != null && skills.contains(selected)) {
			return selected;
		}
		for (Skill skill : skills) {
			if (skill != Skill.KING) {
				return skill;
			}
		}
		return skills.isEmpty() ? null : skills.get(0);
	}

	private static long now() {
		Minecraft client = Minecraft.getInstance();
		return client.level == null ? 0L : client.level.getGameTime();
	}

	private static void readCooldowns(String text) {
		long now = now();
		COOLDOWN_END.clear();
		for (String part : text.split(";")) {
			int colon = part.indexOf(':');
			if (colon <= 0) {
				continue;
			}
			try {
				COOLDOWN_END.put(part.substring(0, colon), now + Long.parseLong(part.substring(colon + 1)));
			} catch (NumberFormatException ignored) {
				// Skip a broken entry.
			}
		}
	}

	/** Ticks left before this skill can be used, counting the short shared wait. */
	static long remaining(Skill skill) {
		long now = now();
		long own = COOLDOWN_END.getOrDefault(skill.id, 0L) - now;
		long shared = COOLDOWN_END.getOrDefault("shared", 0L) - now;
		return Math.max(0L, Math.max(own, shared));
	}

	static ItemStack icon(Skill skill) {
		return skill.spirit == null ? new ItemStack(Items.NETHER_STAR) : new ItemStack(ModItems.SPIRIT_ITEMS.get(skill.spirit));
	}

	static int color(Skill skill) {
		return skill.spirit == null ? 0xFFFFD86B : 0xFF000000 | skill.spirit.color;
	}

	static String seconds(long ticks) {
		long seconds = (ticks + 19) / 20;
		return seconds >= 60 ? (seconds / 60) + ":" + String.format("%02d", seconds % 60) : Long.toString(seconds);
	}

	/** True while the key is physically held, even with a screen open. */
	static boolean keyPhysicallyDown() {
		InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(key);
		long window = GLFW.glfwGetCurrentContext();
		if (window == 0L || bound.getValue() < 0) {
			return false;
		}
		if (bound.getType() == InputConstants.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(window, bound.getValue()) == GLFW.GLFW_PRESS;
		}
		return GLFW.glfwGetKey(window, bound.getValue()) == GLFW.GLFW_PRESS;
	}

	static void wheelClosed() {
		held = 0;
		wasDown = false;
	}

	static void tick(Minecraft client) {
		boolean down = key.isDown();
		while (key.consumeClick()) {
			// Presses are read from isDown(); drop the queued clicks.
		}
		if (client.screen != null) {
			wasDown = false;
			held = 0;
			return;
		}
		if (down) {
			held++;
			if (held == HOLD_TICKS) {
				if (available().isEmpty()) {
					client.player.displayClientMessage(Component.translatable("msg.sacredsouls.skill_none").withStyle(ChatFormatting.GRAY), true);
				} else {
					client.setScreen(new SkillWheelScreen());
				}
			}
		} else {
			if (wasDown && held > 0 && held < HOLD_TICKS) {
				cast(client);
			}
			held = 0;
		}
		wasDown = down;
	}

	private static void cast(Minecraft client) {
		Skill skill = current();
		if (skill == null) {
			client.player.displayClientMessage(Component.translatable("msg.sacredsouls.skill_none").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		ClientPlayNetworking.send(new SkillCastPayload(skill.id));
	}

	private static void renderHud(GuiGraphics graphics, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		Skill skill = current();
		if (skill == null) {
			return;
		}
		int x = graphics.guiWidth() / 2 + 91 + 28;
		int y = graphics.guiHeight() - 22;
		graphics.fill(x - 1, y - 1, x + 21, y + 21, color(skill));
		graphics.fill(x, y, x + 20, y + 20, 0xFF16161C);
		graphics.renderItem(icon(skill), x + 2, y + 2);
		long left = remaining(skill);
		if (left > 0) {
			int cooldown = skill.cooldownTicks;
			int cover = (int) Math.ceil(20.0 * Math.min(1.0, left / (double) Math.max(1, cooldown)));
			graphics.fill(x, y + 20 - cover, x + 20, y + 20, 0xA0000000);
			String text = seconds(left);
			graphics.drawString(client.font, text, x + 21 - client.font.width(text), y + 12, 0xFFFFFFFF);
		}
	}
}
