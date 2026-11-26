package com.kalasawa.sacredsouls.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.kalasawa.sacredsouls.SacredSouls;

/** Sent when the client performs a mid-air jump (ขนนกลม). */
public record AirJumpPayload() implements CustomPacketPayload {
	public static final AirJumpPayload INSTANCE = new AirJumpPayload();
	public static final Type<AirJumpPayload> TYPE = new Type<>(SacredSouls.id("air_jump"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AirJumpPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
