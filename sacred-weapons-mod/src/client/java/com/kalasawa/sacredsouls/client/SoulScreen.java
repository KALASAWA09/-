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
import com.kalasawa.sacredsouls.soul.ModAttachments;
import com.kalasawa.sacredsouls.soul.SoulData;
import com.kalasawa.sacredsouls.spirit.Spirit;
import com.kalasawa.sacredsouls.title.SoulTitle;
import com.kalasawa.sacredsouls.title.TitleManager;

/**
 * The soul screen (K). Top: energy, essence, title. Middle: 5 slot boxes and the spirit slot.
 * Bottom: details of the selected slot with Upgrade and Eject. While a title is on offer, the bottom asks
 * whether to take it instead, with Accept and Decline and the time left.
 */
public class SoulScreen extends Screen {
	private static final int WIDTH = 250;
	private static final int BOX = 30;
	private static final int GAP = 10;
	private static final int PANEL_HEIGHT = 96;
	private static final int SPIRIT_GAP = 20;
	private static final int SPIRIT = SoulData.SPIRIT_SLOT;
	private static final int BOXES = SoulData.MAX_SLOTS + 1;

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
	private static final int SPIRIT_EDGE = 0xFF4FD8E8;

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
		int rowWidth = BOXES * BOX + (SoulData.MAX_SLOTS - 1) * GAP + SPIRIT_GAP;
		int start = (this.width - rowWidth) / 2;
		return i == SPIRIT ? start + SoulData.MAX_SLOTS * (BOX + GAP) - GAP + SPIRIT_GAP : start + i * (BOX + GAP);
	}

	/** Icon item of a box, or null when it is empty. */
	private static ItemStack icon(SoulData data, int i) {
		if (i == SPIRIT) {
			Spirit spirit = data.spirit();
			return spirit == null ? null : new ItemStack(ModItems.SPIRIT_ITEMS.get(spirit));
		}
		SoulPower power = data.powerAt(i);
		return power == null ? null : new ItemStack(ModItems.SOUL_ITEMS.get(power));
	}

	private static int boxLevel(SoulData data, int i) {
		return i == SPIRIT ? data.spiritLevel() : i < data.levels.size() ? data.levels.get(i) : 0;
	}

	private static Component boxName(SoulData data, int i) {
		if (i == SPIRIT) {
			Spirit spirit = data.spirit();
			return spirit == null ? Component.translatable("gui.sacredsouls.empty_spirit") : spirit.displayName();
		}
		SoulPower power = data.powerAt(i);
		return power == null ? Component.translatable("gui.sacredsouls.empty_slot") : power.displayName();
	}

	private int panelY() {
		return slotsY() + BOX + 14;
	}

	private static String stateKey(SoulData data) {
		return data.slots + "|" + data.levels + "|" + data.essence + "|" + data.energy + "|" + data.title + "|" + data.spirit
				+ "|" + offer();
	}

	/** The raw title offer ("id|ends") synced from the server, or "" when there is none. */
	private static String offer() {
		var player = net.minecraft.client.Minecraft.getInstance().player;
		String offer = player == null ? null : player.getAttached(ModAttachments.TITLE_OFFER);
		return offer == null ? "" : offer;
	}

	private static SoulTitle offeredTitle() {
		String offer = offer();
		int bar = offer.indexOf('|');
		return bar < 0 ? null : SoulTitle.byId(offer.substring(0, bar));
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
		selected = Math.max(0, Math.min(selected, SPIRIT));

		// The slot boxes are plain buttons with no label; the icon is drawn on top in render().
		for (int i = 0; i < BOXES; i++) {
			final int slot = i;
			Button box = Button.builder(Component.empty(), b -> {
				selected = slot;
				rebuildWidgets();
			}).bounds(slotX(i), slotsY(), BOX, BOX).build();
			box.setTooltip(Tooltip.create(boxName(data, i)));
			addRenderableWidget(box);
		}

		if (offeredTitle() != null) {
			int buttonY = panelY() + PANEL_HEIGHT + 6;
			int half = (WIDTH - 6) / 2;
			addRenderableWidget(Button.builder(Component.translatable("gui.sacredsouls.accept_title"),
					b -> ClientPlayNetworking.send(new SoulActionPayload(SoulActionPayload.ACCEPT_TITLE, 0)))
					.bounds(left(), buttonY, half, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("gui.sacredsouls.decline_title"),
					b -> ClientPlayNetworking.send(new SoulActionPayload(SoulActionPayload.DECLINE_TITLE, 0)))
					.bounds(left() + WIDTH - half, buttonY, half, 20).build());
			return;
		}
		if (icon(data, selected) == null) {
			return;
		}
		int level = boxLevel(data, selected);
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
		// Cyan frame marks the spirit slot when it is not selected.
		if (selected != SPIRIT) {
			int px = slotX(SPIRIT);
			graphics.fill(px - 1, sy - 1, px + BOX + 1, sy + BOX + 1, SPIRIT_EDGE);
		}
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
		for (int i = 0; i < BOXES; i++) {
			int sx = slotX(i);
			int sy = slotsY();
			ItemStack icon = icon(data, i);
			if (icon == null) {
				graphics.drawCenteredString(this.font, "+", sx + BOX / 2, sy + (BOX - 8) / 2, i == SPIRIT ? SPIRIT_EDGE : DIM);
			} else {
				graphics.renderItem(icon, sx + (BOX - 16) / 2, sy + (BOX - 16) / 2);
				String lv = String.valueOf(boxLevel(data, i));
				graphics.drawString(this.font, lv, sx + BOX - 3 - this.font.width(lv), sy + BOX - 10, GOLD);
			}
		}
		Component spiritLabel = Component.translatable("gui.sacredsouls.spirit_slot");
		graphics.drawCenteredString(this.font, spiritLabel, slotX(SPIRIT) + BOX / 2, slotsY() - 11, SPIRIT_EDGE);

		renderDetails(graphics, data, x + 8, panelY() + 7, WIDTH - 16, title);
	}

	private void renderDetails(GuiGraphics graphics, SoulData data, int x, int y, int width, SoulTitle title) {
		SoulTitle offered = offeredTitle();
		if (offered != null) {
			renderOffer(graphics, offered, title, x, y, width);
			return;
		}
		if (selected == SPIRIT) {
			renderSpirit(graphics, data, x, y, width);
			return;
		}
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

	private void renderSpirit(GuiGraphics graphics, SoulData data, int x, int y, int width) {
		Spirit spirit = data.spirit();
		if (spirit == null) {
			graphics.drawString(this.font, Component.translatable("gui.sacredsouls.empty_spirit"), x, y, WHITE);
			wrapped(graphics, Component.translatable("gui.sacredsouls.empty_spirit_hint"), x, y + 14, width, LIGHT);
			return;
		}
		int level = data.spiritLevel();
		graphics.drawString(this.font, spirit.displayName(), x, y, bright(spirit.color));
		Component levelText = Component.translatable("gui.sacredsouls.level", level, SoulData.MAX_LEVEL);
		graphics.drawString(this.font, levelText, x + width - this.font.width(levelText), y, GOLD);
		y = wrapped(graphics, spirit.summary(), x, y + 14, width, WHITE);
		y += 4;
		y = wrapped(graphics, Component.translatable("gui.sacredsouls.now", spirit.effect(level)), x, y, width, GREEN);
		if (level < SoulData.MAX_LEVEL) {
			wrapped(graphics, Component.translatable("gui.sacredsouls.next", spirit.effect(level + 1)), x, y + 2, width, DIM);
		}
	}

	/** The title offer: what is offered, what it would replace, and the minutes left to decide. */
	private void renderOffer(GuiGraphics graphics, SoulTitle offered, SoulTitle current, int x, int y, int width) {
		graphics.drawString(this.font, Component.translatable("gui.sacredsouls.offer_header"), x, y, GOLD);
		long left = 0;
		var level = net.minecraft.client.Minecraft.getInstance().level;
		if (level != null) {
			left = Math.max(0, TitleManager.offerEnds(offer()) - level.getGameTime()) / 20;
		}
		String clock = String.format(java.util.Locale.ROOT, "%d:%02d", left / 60, left % 60);
		graphics.drawString(this.font, clock, x + width - this.font.width(clock), y, left < 60 ? RED : GOLD);
		y += 13;
		graphics.drawString(this.font, offered.displayName(), x, y, GOLD);
		y = wrapped(graphics, offered.buff(), x, y + 11, width, LIGHT);
		y = wrapped(graphics, offered.proc(), x, y, width, LIGHT);
		if (current != null) {
			wrapped(graphics, Component.translatable("gui.sacredsouls.offer_swap", current.displayName()), x, y + 4, width, DIM);
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
