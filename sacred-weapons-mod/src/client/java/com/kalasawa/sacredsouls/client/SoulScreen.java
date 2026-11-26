package com.kalasawa.sacredsouls.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.kalasawa.sacredsouls.item.ModItems;
import com.kalasawa.sacredsouls.network.SoulActionPayload;
import com.kalasawa.sacredsouls.power.SoulPower;
import com.kalasawa.sacredsouls.soul.SoulData;
import com.kalasawa.sacredsouls.title.SoulTitle;

/**
 * The soul screen (K). Top: energy, essence, title. Middle: 5 slot boxes.
 * Bottom: details of the selected slot with Upgrade and Eject.
 */
public class SoulScreen extends Screen {
	private static final int WIDTH = 250;
	private static final int BOX = 30;
	private static final int GAP = 10;
	private static final int PANEL_HEIGHT = 96;

	private static final int WHITE = 0xFFFFFFFF;
	private static final int LIGHT = 0xFFD8D8E0;
	private static final int DIM = 0xFF9090A0;
	private static final int AQUA = 0xFF6FF3FF;
	private static final int GOLD = 0xFFFFCC33;
	private static final int GREEN = 0xFF7CFF7C;
	private static final int PURPLE = 0xFFE0A8FF;
	private static final int RED = 0xFFFF6B6B;
	private static final int PANEL = 0xC0101018;
	private static final int PANEL_EDGE = 0xFF5A3F8C;

	private int selected = 0;
	private String shownState = "";

	public SoulScreen() {
		super(Component.translatable("gui.sacredsouls.title"));
	}

	private int left() {
		return (this.width - WIDTH) / 2;
	}

	private int top() {
		return Math.max(8, (this.height - 210) / 2);
	}

	private int slotsY() {
		return top() + 44;
	}

	private int slotX(int i) {
		int rowWidth = SoulData.MAX_SLOTS * BOX + (SoulData.MAX_SLOTS - 1) * GAP;
		return (this.width - rowWidth) / 2 + i * (BOX + GAP);
	}

	private int panelY() {
		return slotsY() + BOX + 14;
	}

	private static String stateKey(SoulData data) {
		return data.slots + "|" + data.levels + "|" + data.essence + "|" + data.energy + "|" + data.title;
	}

	/** Mixes a power colour halfway to white so it reads well on a dark background. */
	private static int bright(int rgb) {
		int r = (((rgb >> 16) & 0xFF) + 255) / 2;
		int g = (((rgb >> 8) & 0xFF) + 255) / 2;
		int b = ((rgb & 0xFF) + 255) / 2;
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	@Override
	protected void init() {
		SoulData data = SacredSoulsClient.localSoul();
		shownState = stateKey(data);
		selected = Math.max(0, Math.min(selected, SoulData.MAX_SLOTS - 1));

		// The slot boxes are plain buttons with no label; the icon is drawn on top in render().
		for (int i = 0; i < SoulData.MAX_SLOTS; i++) {
			final int slot = i;
			Button box = Button.builder(Component.empty(), b -> {
				selected = slot;
				rebuildWidgets();
			}).bounds(slotX(i), slotsY(), BOX, BOX).build();
			SoulPower power = data.powerAt(i);
			box.setTooltip(Tooltip.create(power == null ? Component.translatable("gui.sacredsouls.empty_slot") : power.displayName()));
			addRenderableWidget(box);
		}

		SoulPower power = data.powerAt(selected);
		if (power == null) {
			return;
		}
		int level = data.levels.get(selected);
		int buttonY = panelY() + PANEL_HEIGHT + 6;
		int x = left();
		Component upgradeLabel = level >= SoulData.MAX_LEVEL
				? Component.translatable("gui.sacredsouls.max_level")
				: Component.translatable("gui.sacredsouls.upgrade", SoulData.upgradeCost(level));
		Button upgrade = Button.builder(upgradeLabel, b -> ClientPlayNetworking.send(new SoulActionPayload(SoulActionPayload.UPGRADE, selected)))
				.bounds(x, buttonY, 150, 20)
				.build();
		upgrade.active = level < SoulData.MAX_LEVEL && data.essence >= SoulData.upgradeCost(level) && !data.isSoulless();
		if (level < SoulData.MAX_LEVEL && data.essence < SoulData.upgradeCost(level)) {
			upgrade.setTooltip(Tooltip.create(Component.translatable("gui.sacredsouls.need_essence", SoulData.upgradeCost(level) - data.essence)));
		}
		addRenderableWidget(upgrade);

		Button eject = Button.builder(Component.translatable("gui.sacredsouls.eject"), b -> ClientPlayNetworking.send(new SoulActionPayload(SoulActionPayload.EJECT, selected)))
				.bounds(x + 156, buttonY, WIDTH - 156, 20)
				.build();
		eject.setTooltip(Tooltip.create(Component.translatable("gui.sacredsouls.eject_hint")));
		addRenderableWidget(eject);
	}

	@Override
	public void tick() {
		super.tick();
		SoulData data = SacredSoulsClient.localSoul();
		if (!stateKey(data).equals(shownState)) {
			rebuildWidgets();
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics, mouseX, mouseY, partialTick);
		int x = left();
		int y = panelY();
		// Details panel with a thin purple frame.
		graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + PANEL_HEIGHT + 1, PANEL_EDGE);
		graphics.fill(x, y, x + WIDTH, y + PANEL_HEIGHT, PANEL);
		// Gold frame around the selected slot.
		int sx = slotX(selected);
		int sy = slotsY();
		graphics.fill(sx - 2, sy - 2, sx + BOX + 2, sy + BOX + 2, GOLD);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		SoulData data = SacredSoulsClient.localSoul();
		int x = left();
		int y = top();

		graphics.drawCenteredString(this.font, this.title, this.width / 2, y, PURPLE);

		// Energy as 10 pips, essence on the right.
		int barY = y + 16;
		if (data.isSoulless()) {
			graphics.drawString(this.font, Component.translatable("gui.sacredsouls.soulless"), x, barY, RED);
		} else {
			Component label = Component.translatable("gui.sacredsouls.energy");
			graphics.drawString(this.font, label, x, barY, AQUA);
			int pipX = x + this.font.width(label) + 6;
			for (int i = 0; i < SoulData.MAX_ENERGY; i++) {
				int px = pipX + i * 8;
				graphics.fill(px, barY, px + 6, barY + 7, i < data.energy ? AQUA : 0xFF303040);
			}
		}
		Component essence = Component.translatable("gui.sacredsouls.essence", data.essence);
		graphics.drawString(this.font, essence, x + WIDTH - this.font.width(essence), barY, GOLD);

		SoulTitle title = data.soulTitle();
		Component titleLine = title == null
				? Component.translatable("gui.sacredsouls.no_title")
				: Component.translatable("gui.sacredsouls.title_line", title.displayName());
		graphics.drawString(this.font, titleLine, x, y + 29, title == null ? DIM : GOLD);

		// Slot icons and level numbers.
		for (int i = 0; i < SoulData.MAX_SLOTS; i++) {
			int sx = slotX(i);
			int sy = slotsY();
			SoulPower power = data.powerAt(i);
			if (power == null) {
				graphics.drawCenteredString(this.font, "+", sx + BOX / 2, sy + (BOX - 8) / 2, DIM);
			} else {
				graphics.renderItem(new ItemStack(ModItems.SOUL_ITEMS.get(power)), sx + (BOX - 16) / 2, sy + (BOX - 16) / 2);
				String lv = String.valueOf(data.levels.get(i));
				graphics.drawString(this.font, lv, sx + BOX - 3 - this.font.width(lv), sy + BOX - 10, GOLD);
			}
		}

		renderDetails(graphics, data, x + 8, panelY() + 7, WIDTH - 16, title);
	}

	private void renderDetails(GuiGraphics graphics, SoulData data, int x, int y, int width, SoulTitle title) {
		SoulPower power = data.powerAt(selected);
		if (power == null) {
			graphics.drawString(this.font, Component.translatable("gui.sacredsouls.empty_slot"), x, y, WHITE);
			y = wrapped(graphics, Component.translatable("gui.sacredsouls.empty_hint"), x, y + 14, width, LIGHT);
			if (title != null) {
				y += 8;
				graphics.drawString(this.font, title.displayName(), x, y, GOLD);
				y = wrapped(graphics, title.buff(), x, y + 11, width, LIGHT);
				wrapped(graphics, title.proc(), x, y, width, LIGHT);
			}
			return;
		}
		int level = data.levels.get(selected);
		graphics.drawString(this.font, power.displayName(), x, y, bright(power.color));
		Component levelText = Component.translatable("gui.sacredsouls.level", level, SoulData.MAX_LEVEL);
		graphics.drawString(this.font, levelText, x + width - this.font.width(levelText), y, GOLD);

		y = wrapped(graphics, power.summary(), x, y + 14, width, WHITE);
		y += 4;
		y = wrapped(graphics, Component.translatable("gui.sacredsouls.now", power.effect(level)), x, y, width, GREEN);
		if (level < SoulData.MAX_LEVEL) {
			wrapped(graphics, Component.translatable("gui.sacredsouls.next", power.effect(level + 1)), x, y + 2, width, DIM);
		}
	}

	/** Draws text wrapped to the width and returns the y below it. */
	private int wrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
		List<FormattedCharSequence> lines = this.font.split(text, width);
		for (FormattedCharSequence line : lines) {
			graphics.drawString(this.font, line, x, y, color);
			y += 10;
		}
		return y;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
