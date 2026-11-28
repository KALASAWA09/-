package com.kalasawa.sacredsouls.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.kalasawa.sacredsouls.SacredSouls;

/** Sent when the player taps the skill key: cast the skill with this id. */
public record SkillCastPayload(String skill) implements CustomPacketPayload {
	public static final Type<SkillCastPayload> TYPE = new Type<>(SacredSouls.id("skill_cast"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SkillCastPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, SkillCastPayload::skill,
			SkillCastPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
