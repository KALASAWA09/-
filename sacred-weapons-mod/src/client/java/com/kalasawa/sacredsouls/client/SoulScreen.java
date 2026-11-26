package com.kalasawa.sacredsouls.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.kalasawa.sacredsouls.item.ModItems;
import com.kalasawa.sacredsouls.network.SoulActionPayload;
import com.kalasawa.sacredsouls.power.SoulPower;
import com.kalasawa.sacredsouls.soul.SoulData;
import com.kalasawa.sacredsouls.title.SoulTitle;

/** The soul screen (K): 5 devour slots, click to upgrade, eject with cleansing water. */
public class SoulScreen extends Screen {
	private static final int WIDTH = 330;
	private static final int ROW_HEIGHT = 34;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFA0A0A0;
	private static final int AQUA = 0xFF55FFFF;
	private static final int GOLD = 0xFFFFAA00;
	private static final int PURPLE = 0xFFD58CFF;
	private static final int RED = 0xFFFF5555;

	private String shownState = "";

	public SoulScreen() {
		super(Component.literal("วิญญาณในตัว"));
	}

	private int left() {
		return (this.width - WIDTH) / 2;
	}

	private int top() {
		return Math.max(10, (this.height - (70 + SoulData.MAX_SLOTS * ROW_HEIGHT)) / 2);
	}

	private static String stateKey(SoulData data) {
		return data.slots + "|" + data.levels + "|" + data.essence + "|" + data.energy + "|" + data.title;
	}

	@Override
	protected void init() {
		SoulData data = SacredSoulsClient.localSoul();
		shownState = stateKey(data);
		int x = left();
		int y = top() + 60;
		for (int i = 0; i < SoulData.MAX_SLOTS; i++) {
			final int slot = i;
			SoulPower power = data.powerAt(i);
			if (power == null) {
				continue;
			}
			int level = data.levels.get(i);
			int rowY = y + i * ROW_HEIGHT;
			Component upgradeLabel = level >= SoulData.MAX_LEVEL
					? Component.literal("ขั้นสูงสุด")
					: Component.literal("อัป (" + SoulData.upgradeCost(level) + ")");
			Button upgrade = Button.builder(upgradeLabel, b -> ClientPlayNetworking.send(new SoulActionPayload(SoulActionPayload.UPGRADE, slot)))
					.bounds(x + WIDTH - 118, rowY + 6, 62, 20)
					.build();
			upgrade.active = level < SoulData.MAX_LEVEL && data.essence >= SoulData.upgradeCost(level) && !data.isSoulless();
			addRenderableWidget(upgrade);
			addRenderableWidget(Button.builder(Component.literal("คาย"), b -> ClientPlayNetworking.send(new SoulActionPayload(SoulActionPayload.EJECT, slot)))
					.bounds(x + WIDTH - 52, rowY + 6, 48, 20)
					.build());
		}
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
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		SoulData data = SacredSoulsClient.localSoul();
		int x = left();
		int y = top();

		graphics.drawCenteredString(this.font, this.title, this.width / 2, y, PURPLE);
		String energy = data.isSoulless() ? "ร่างไร้วิญญาณ (ใช้ธูปเรียกวิญญาณ)" : "พลังวิญญาณ " + data.energy + "/10";
		graphics.drawString(this.font, energy, x, y + 16, data.isSoulless() ? RED : AQUA);
		graphics.drawString(this.font, "แก่นวิญญาณ " + data.essence, x + 190, y + 16, GOLD);
		SoulTitle title = data.soulTitle();
		graphics.drawString(this.font, title == null ? "ฉายา: ยังไม่มี" : "ฉายา: " + title.thaiName + " (" + title.buff + ")", x, y + 30, title == null ? GRAY : GOLD);
		graphics.drawString(this.font, "กลืนแล้ว " + data.slots.size() + "/" + SoulData.MAX_SLOTS + " ช่อง", x, y + 44, GRAY);

		int rowsTop = y + 60;
		for (int i = 0; i < SoulData.MAX_SLOTS; i++) {
			int rowY = rowsTop + i * ROW_HEIGHT;
			graphics.fill(x, rowY, x + WIDTH, rowY + ROW_HEIGHT - 2, 0x66000000);
			SoulPower power = data.powerAt(i);
			if (power == null) {
				graphics.drawString(this.font, "ช่องว่าง: ถือไอเทมวิญญาณแล้วคลิกขวาค้างเพื่อให้กลืน", x + 6, rowY + 12, GRAY);
				continue;
			}
			graphics.renderItem(new ItemStack(ModItems.SOUL_ITEMS.get(power)), x + 6, rowY + 8);
			int level = data.levels.get(i);
			graphics.drawString(this.font, power.thaiName + "  ขั้น " + level + "/" + SoulData.MAX_LEVEL, x + 28, rowY + 5, 0xFF000000 | power.color | 0x404040);
			String desc = this.font.plainSubstrByWidth(power.description, WIDTH - 150);
			graphics.drawString(this.font, desc, x + 28, rowY + 18, WHITE);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
