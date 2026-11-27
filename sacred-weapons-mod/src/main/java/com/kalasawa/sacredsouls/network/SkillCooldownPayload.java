package com.kalasawa.sacredsouls.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.kalasawa.sacredsouls.SacredSouls;

/** Server to client: ticks left on each skill, as "id:ticks;id:ticks", plus "shared". */
public record SkillCooldownPayload(String cooldowns) implements CustomPacketPayload {
	public static final Type<SkillCooldownPayload> TYPE = new Type<>(SacredSouls.id("skill_cooldowns"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SkillCooldownPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, SkillCooldownPayload::cooldowns,
			SkillCooldownPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
