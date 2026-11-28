package com.kalasawa.sacredsouls.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.kalasawa.sacredsouls.SacredSouls;

/** Sent by the soul screen: upgrade or eject the item in a slot, or answer a title offer. */
public record SoulActionPayload(int action, int slot) implements CustomPacketPayload {
	public static final int UPGRADE = 0;
	public static final int EJECT = 1;
	/** Take the title offered on the soul screen. */
	public static final int ACCEPT_TITLE = 2;
	/** Turn the offered title down. */
	public static final int DECLINE_TITLE = 3;

	public static final Type<SoulActionPayload> TYPE = new Type<>(SacredSouls.id("soul_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SoulActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SoulActionPayload::action,
			ByteBufCodecs.VAR_INT, SoulActionPayload::slot,
			SoulActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
