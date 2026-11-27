package com.kalasawa.sacredsouls.skill;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Every block a skill breaks or places goes through here, so the server setting decides what happens:
 * nothing, a change that grows back, or a permanent change. Broken blocks never drop items.
 * Work is spread over ticks so a large skill cannot freeze the server.
 */
public final class BlockChanges {
	private static final int FLAGS = Block.UPDATE_CLIENTS;

	/** A block to put back later, if it still holds what the skill left there. */
	private record Restore(ServerLevel level, BlockPos pos, BlockState original, BlockState left, long at) {
	}

	/** A batch of positions to break, worked through a little each tick. */
	public interface Job {
		/** Returns the next position to try, or null when the job is done. */
		BlockPos next();

		ServerLevel level();

		ServerPlayer owner();

		boolean naturalOnly();

		int regrowTicks();
	}

	private static final PriorityQueue<Restore> RESTORES = new PriorityQueue<>(Comparator.comparingLong(Restore::at));
	private static final Deque<Job> JOBS = new ArrayDeque<>();

	private BlockChanges() {
	}

	public static boolean enabled() {
		return SkillConfig.blockMode != SkillConfig.BlockMode.OFF;
	}

	/** Blocks no skill may touch: unbreakable, holding items or data, or protected. */
	public static boolean canBreak(ServerLevel level, ServerPlayer owner, BlockPos pos, BlockState state, boolean naturalOnly) {
		if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) {
			return false;
		}
		if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.RESPAWN_ANCHOR)
				|| state.is(Blocks.REINFORCED_DEEPSLATE) || state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.NETHER_PORTAL)) {
			return false;
		}
		if (owner != null && !level.mayInteract(owner, pos)) {
			return false;
		}
		if (!naturalOnly) {
			return true;
		}
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
				|| state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.LEAVES) || state.is(BlockTags.ICE)
				|| state.is(BlockTags.SNOW) || state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.FLOWERS)
				|| state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.FIRE);
	}

	/** Soft blocks a wave or frost can sweep away: plants, leaves and snow. */
	public static boolean isSoft(BlockState state) {
		return state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.LEAVES)
				|| state.is(BlockTags.SNOW) || state.is(Blocks.SNOW);
	}

	/** Breaks one block with no drops, if the setting and the rules allow it. */
	public static boolean breakBlock(ServerLevel level, ServerPlayer owner, BlockPos pos, boolean naturalOnly, int regrowTicks) {
		return replace(level, owner, pos, Blocks.AIR.defaultBlockState(), naturalOnly, regrowTicks);
	}

	/** Swaps a block for another (grass to dirt, water to ice), following the setting. */
	public static boolean replace(ServerLevel level, ServerPlayer owner, BlockPos pos, BlockState next, boolean naturalOnly, int regrowTicks) {
		if (!enabled()) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		boolean water = state.is(Blocks.WATER) && !next.isAir();
		if (!water && !canBreak(level, owner, pos, state, naturalOnly)) {
			return false;
		}
		if (water && owner != null && !level.mayInteract(owner, pos)) {
			return false;
		}
		level.setBlock(pos.immutable(), next, FLAGS);
		if (SkillConfig.blockMode == SkillConfig.BlockMode.TEMPORARY) {
			RESTORES.add(new Restore(level, pos.immutable(), state, next, level.getGameTime() + regrowTicks));
		}
		return true;
	}

	/**
	 * Places a block into an empty space for a moment (ice spikes, fire), whatever the setting,
	 * as long as skills may change blocks at all. It is removed again after the given time.
	 */
	public static boolean placeBriefly(ServerLevel level, ServerPlayer owner, BlockPos pos, BlockState placed, int ticks) {
		if (!enabled()) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.isAir() || isSoft(state)) || state.hasBlockEntity() || (owner != null && !level.mayInteract(owner, pos))) {
			return false;
		}
		level.setBlock(pos.immutable(), placed, FLAGS);
		RESTORES.add(new Restore(level, pos.immutable(), state, placed, level.getGameTime() + ticks));
		return true;
	}

	public static void queue(Job job) {
		if (enabled()) {
			JOBS.add(job);
		}
	}

	public static void tick(MinecraftServer server) {
		int budget = SkillConfig.blocksPerTick;
		int checks = budget * 10;
		while (budget > 0 && checks > 0 && !JOBS.isEmpty()) {
			Job job = JOBS.peek();
			BlockPos pos = job.next();
			if (pos == null) {
				JOBS.poll();
				continue;
			}
			checks--;
			if (breakBlock(job.level(), job.owner(), pos, job.naturalOnly(), job.regrowTicks())) {
				budget--;
			}
		}
		if (RESTORES.isEmpty()) {
			return;
		}
		int restored = 0;
		List<Restore> later = new ArrayList<>();
		while (restored < SkillConfig.blocksPerTick && !RESTORES.isEmpty()) {
			Restore restore = RESTORES.peek();
			long now = restore.level().getGameTime();
			if (now < restore.at()) {
				break;
			}
			RESTORES.poll();
			if (!restore.level().getBlockState(restore.pos()).equals(restore.left())) {
				continue; // Something else changed it since; leave it alone.
			}
			if (!restore.original().isAir() && !restore.level().getEntitiesOfClass(LivingEntity.class, new AABB(restore.pos())).isEmpty()) {
				later.add(new Restore(restore.level(), restore.pos(), restore.original(), restore.left(), now + 40));
				continue; // Never grow a block back inside someone.
			}
			restore.level().setBlock(restore.pos(), restore.original(), FLAGS);
			restored++;
		}
		RESTORES.addAll(later);
	}

	/** On shutdown, put every temporary block back so nothing is lost. */
	public static void restoreAll() {
		JOBS.clear();
		for (Restore restore : RESTORES) {
			if (restore.level().getBlockState(restore.pos()).equals(restore.left())) {
				restore.level().setBlock(restore.pos(), restore.original(), FLAGS);
			}
		}
		RESTORES.clear();
	}
}
