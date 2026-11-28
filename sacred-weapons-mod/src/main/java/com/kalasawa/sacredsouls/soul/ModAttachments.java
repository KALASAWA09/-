package com.kalasawa.sacredsouls.soul;

import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.network.codec.ByteBufCodecs;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import com.kalasawa.sacredsouls.SacredSouls;

public final class ModAttachments {
	/** The soul inside each player. Survives death and logout, synced to that player only. */
	public static final AttachmentType<SoulData> SOUL = AttachmentRegistry.create(SacredSouls.id("soul"), builder -> builder
			.initializer(SoulData::empty)
			.persistent(SoulData.CODEC)
			.copyOnDeath()
			.syncWith(SoulData.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Stored on the overworld: title id -> holder UUID. One holder per title on the server. */
	public static final AttachmentType<Map<String, String>> TITLE_HOLDERS = AttachmentRegistry.create(SacredSouls.id("title_holders"), builder -> builder
			.initializer(Map::of)
			.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));

	/**
	 * A title offered to a player who already holds one, as "title_id|game time it runs out". Not saved: an offer
	 * lasts only while the player is online and alive. Synced so the soul screen can show it.
	 */
	public static final AttachmentType<String> TITLE_OFFER = AttachmentRegistry.create(SacredSouls.id("title_offer"), builder -> builder
			.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly()));

	/** Game time when the Spirit King ultimate is ready again. Kept across logout and restarts. */
	public static final AttachmentType<Long> ULTIMATE_READY = AttachmentRegistry.create(SacredSouls.id("ultimate_ready"), builder -> builder
			.initializer(() -> 0L)
			.persistent(Codec.LONG)
			.copyOnDeath());

	private ModAttachments() {
	}

	public static void init() {
		// Loads the class so the attachment types register during mod init.
	}
}
