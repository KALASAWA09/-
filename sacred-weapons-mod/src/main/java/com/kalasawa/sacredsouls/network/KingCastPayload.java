package com.kalasawa.sacredsouls.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.kalasawa.sacredsouls.SacredSouls;

/**
 * Server to client: the Spirit King ultimate has started here. The client draws the whole spectacle
 * itself (circle, orbs, waves, cloud, flash), which is smoother than sending every particle.
 */
public record KingCastPayload(double x, double y, double z, int radius, int castTicks) implements CustomPacketPayload {
	public static final Type<KingCastPayload> TYPE = new Type<>(SacredSouls.id("king_cast"));
	public static final StreamCodec<RegistryFriendlyByteBuf, KingCastPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, KingCastPayload::x,
			ByteBufCodecs.DOUBLE, KingCastPayload::y,
			ByteBufCodecs.DOUBLE, KingCastPayload::z,
			ByteBufCodecs.VAR_INT, KingCastPayload::radius,
			ByteBufCodecs.VAR_INT, KingCastPayload::castTicks,
			KingCastPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
