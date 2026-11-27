package com.kalasawa.sacredsouls.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.kalasawa.sacredsouls.skill.Skill;
import com.kalasawa.sacredsouls.spirit.Spirit;

/**
 * Hold the skill key: a ring of the eight spirit skills, pixel style. Point at one and let go to pick it.
 * The Spirit King's own skill sits in the middle.
 */
public class SkillWheelScreen extends Screen {
	private static final int INNER = 30;
	private static final int OUTER = 74;
	private static final int CELL = 2;
	private static final int SEGMENT = 0xC8121216;
	private static final int SEGMENT_HOVER = 0xE0968773;
	private static final int SEGMENT_LOCKED = 0xA00A0A0C;

	private final List<Skill> available;
	private Skill hovered;

	public SkillWheelScreen() {
		super(Component.translatable("gui.sacredsouls.skill_wheel"));
		this.available = SkillClient.available();
		this.hovered = SkillClient.current();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// No blur or dimming, so the world stays visible behind the wheel.
	}

	@Override
	public void tick() {
		if (!SkillClient.keyPhysicallyDown()) {
			if (hovered != null && available.contains(hovered)) {
				SkillClient.selected = hovered;
			}
			SkillClient.wheelClosed();
			onClose();
		}
	}

	private static Skill skillAt(int index) {
		return Skill.of(Spirit.values()[index]);
	}

	/** Segment under an angle measured clockwise from straight up. */
	private static int segment(double degrees) {
		return (int) Math.floor(((degrees + 22.5) % 360 + 360) % 360 / 45.0);
	}

	private static double angleOf(double dx, double dy) {
		return (Math.toDegrees(Math.atan2(dx, -dy)) + 360) % 360;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int cx = this.width / 2;
		int cy = this.height / 2;
		double mx = mouseX - cx;
		double my = mouseY - cy;
		double distance = Math.sqrt(mx * mx + my * my);
		if (distance < INNER - 4) {
			hovered = available.contains(Skill.KING) ? Skill.KING : hovered;
		} else if (distance > 6) {
			hovered = skillAt(segment(angleOf(mx, my)));
		}
		int hoverIndex = hovered != null && hovered.spirit != null ? hovered.spirit.ordinal() : -1;

		// The ring, drawn in small square cells for a pixel look.
		for (int px = -OUTER - 6; px <= OUTER + 6; px += CELL) {
			for (int py = -OUTER - 6; py <= OUTER + 6; py += CELL) {
				double d = Math.sqrt(px * px + py * py);
				double angle = angleOf(px, py);
				int index = segment(angle);
				boolean hover = index == hoverIndex;
				double outer = OUTER + (hover ? 5 : 0);
				double inner = INNER + (hover ? 5 : 0);
				if (d < inner || d > outer) {
					continue;
				}
				double offset = ((angle + 22.5) % 45 + 45) % 45;
				if (offset < 2.0 || offset > 43.0) {
					continue; // The thin gap between segments.
				}
				boolean unlocked = available.contains(skillAt(index));
				int color = hover ? SEGMENT_HOVER : unlocked ? SEGMENT : SEGMENT_LOCKED;
				graphics.fill(cx + px, cy + py, cx + px + CELL, cy + py + CELL, color);
			}
		}
		// The middle: the Spirit King's ultimate, if this player holds the title.
		if (available.contains(Skill.KING)) {
			for (int px = -INNER + 6; px <= INNER - 6; px += CELL) {
				for (int py = -INNER + 6; py <= INNER - 6; py += CELL) {
					if (px * px + py * py <= (INNER - 6) * (INNER - 6)) {
						graphics.fill(cx + px, cy + py, cx + px + CELL, cy + py + CELL, hovered == Skill.KING ? SEGMENT_HOVER : SEGMENT);
					}
				}
			}
			drawIcon(graphics, Skill.KING, cx, cy - 6, true);
		}
		for (int i = 0; i < 8; i++) {
			Skill skill = skillAt(i);
			double angle = Math.toRadians(i * 45.0);
			double r = (INNER + OUTER) / 2.0 + (i == hoverIndex ? 5 : 0);
			int x = cx + (int) Math.round(Math.sin(angle) * r);
			int y = cy - (int) Math.round(Math.cos(angle) * r);
			drawIcon(graphics, skill, x, y, available.contains(skill));
		}
		// The name and state of the pointed skill, below the wheel.
		if (hovered != null) {
			int textY = cy + OUTER + 12;
			graphics.drawCenteredString(this.font, hovered.displayName(), cx, textY, SkillClient.color(hovered));
			Component state;
			if (!available.contains(hovered)) {
				state = hovered.spirit == null ? Component.translatable("gui.sacredsouls.skill_king_only")
						: Component.translatable("gui.sacredsouls.skill_needs", hovered.spirit.displayName());
			} else {
				long left = SkillClient.remaining(hovered);
				state = left > 0 ? Component.translatable("gui.sacredsouls.skill_wait", SkillClient.seconds(left))
						: Component.translatable("gui.sacredsouls.skill_ready");
			}
			graphics.drawCenteredString(this.font, state, cx, textY + 11, 0xFFD8D8E0);
		}
	}

	private void drawIcon(GuiGraphics graphics, Skill skill, int x, int y, boolean unlocked) {
		int left = x - 11;
		int top = y - 11;
		graphics.fill(left - 1, top - 1, left + 23, top + 23, unlocked ? SkillClient.color(skill) : 0xFF404040);
		graphics.fill(left, top, left + 22, top + 22, 0xFF0C0C0E);
		graphics.renderItem(SkillClient.icon(skill), left + 3, top + 3);
		if (!unlocked) {
			graphics.fill(left, top, left + 22, top + 22, 0xD0000000);
			// A small padlock.
			graphics.fill(left + 13, top + 13, left + 20, top + 19, 0xFFC8AA3C);
			graphics.fill(left + 14, top + 10, left + 15, top + 13, 0xFFC8AA3C);
			graphics.fill(left + 18, top + 10, left + 19, top + 13, 0xFFC8AA3C);
			graphics.fill(left + 14, top + 9, left + 19, top + 10, 0xFFC8AA3C);
			return;
		}
		long remaining = SkillClient.remaining(skill);
		if (remaining > 0) {
			int cover = (int) Math.ceil(22.0 * Math.min(1.0, remaining / (double) Math.max(1, skill.cooldownTicks)));
			graphics.fill(left, top + 22 - cover, left + 22, top + 22, 0xA0000000);
			String text = SkillClient.seconds(remaining);
			graphics.drawString(this.font, text, left + 22 - this.font.width(text), top + 14, 0xFFFFFFFF);
		}
	}
}
