package com.kalasawa.sacredsouls.soul;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

import com.kalasawa.sacredsouls.item.ModItems;
import com.kalasawa.sacredsouls.power.SoulPower;

/** Where soul items drop. Mob and chest drops use loot tables; mining, raids and breeding use events. */
public final class LootSources {
	private record Drop(SoulPower power, float chance, boolean killedByPlayer) {
	}

	private static final Map<ResourceKey<LootTable>, Drop> TABLE_DROPS = new LinkedHashMap<>();

	static {
		mob("cave_spider", SoulPower.SERPENT_EYE, 0.04f);
		mob("breeze", SoulPower.WIND_FEATHER, 0.10f);
		mob("enderman", SoulPower.MIRROR_SHADOW, 0.04f);
		mob("stray", SoulPower.ETERNAL_ICE, 0.05f);
		mob("blaze", SoulPower.EMBER_COAL, 0.05f);
		mob("goat", SoulPower.STONE_HORN, 0.15f);
		mob("bat", SoulPower.BAT_WING, 0.10f);
		mob("frog", SoulPower.CHAMELEON_SCALE, 0.08f);
		mob("pillager", SoulPower.SHACKLE, 0.05f);
		mob("guardian", SoulPower.MIRROR_GLASS, 0.05f);
		mob("spider", SoulPower.SPIDER_FANG, 0.03f);
		mob("shulker", SoulPower.GRAVITY_ORB, 0.08f);
		mob("witch", SoulPower.THIEF_MASK, 0.06f);
		chest("ancient_city", SoulPower.TIME_CRYSTAL, 0.15f);
		chest("shipwreck_treasure", SoulPower.HAUNTED_COMPASS, 0.10f);
		chest("shipwreck_supply", SoulPower.HAUNTED_COMPASS, 0.10f);
		chest("shipwreck_map", SoulPower.HAUNTED_COMPASS, 0.10f);
	}

	private static void mob(String entity, SoulPower power, float chance) {
		TABLE_DROPS.put(ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace("entities/" + entity)), new Drop(power, chance, true));
	}

	private static void chest(String chest, SoulPower power, float chance) {
		TABLE_DROPS.put(ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace("chests/" + chest)), new Drop(power, chance, false));
	}

	private LootSources() {
	}

	public static void init() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			Drop drop = TABLE_DROPS.get(key);
			if (drop == null || !source.isBuiltin()) {
				return;
			}
			LootPool.Builder pool = LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1))
					.add(LootItem.lootTableItem(ModItems.SOUL_ITEMS.get(drop.power())))
					.when(LootItemRandomChanceCondition.randomChance(drop.chance()));
			if (drop.killedByPlayer()) {
				pool.when(LootItemKilledByPlayerCondition.killedByPlayer());
			}
			tableBuilder.withPool(pool);
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer)) {
				return;
			}
			SoulPower power = miningDrop(state, pos, serverLevel.getRandom().nextFloat());
			if (power != null) {
				Block.popResource(serverLevel, pos, new ItemStack(ModItems.SOUL_ITEMS.get(power)));
			}
		});
	}

	private static SoulPower miningDrop(BlockState state, BlockPos pos, float roll) {
		if (state.is(BlockTags.DIAMOND_ORES)) {
			return roll < 0.03f ? SoulPower.MINER_CORE : null;
		}
		if (pos.getY() < 0 && isOre(state)) {
			return roll < 0.01f ? SoulPower.STONE_HEART : null;
		}
		if (state.is(Blocks.DARK_OAK_LOG) || state.is(Blocks.JUNGLE_LOG) || state.is(Blocks.SPRUCE_LOG) || state.is(Blocks.PALE_OAK_LOG)) {
			return roll < 0.01f ? SoulPower.ANCIENT_ROOT : null;
		}
		return null;
	}

	private static boolean isOre(BlockState state) {
		return state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.GOLD_ORES)
				|| state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.LAPIS_ORES)
				|| state.is(BlockTags.EMERALD_ORES);
	}

	/** Called every second per player: rewards raid wins and wolf breeding by watching vanilla stats. */
	public static void checkStats(ServerPlayer player) {
		int raids = player.getStats().getValue(Stats.CUSTOM.get(Stats.RAID_WIN));
		int bred = player.getStats().getValue(Stats.CUSTOM.get(Stats.ANIMALS_BRED));
		SoulData data = SoulManager.get(player);
		if (data.raidWinsSeen == raids && data.bredSeen == bred) {
			return;
		}
		SoulData copy = data.copy();
		ServerLevel level = (ServerLevel) player.level();
		if (copy.raidWinsSeen >= 0 && raids > copy.raidWinsSeen && level.getRandom().nextFloat() < 0.30f) {
			give(player, SoulPower.SPIRIT_BELL);
		}
		if (copy.bredSeen >= 0 && bred > copy.bredSeen && level.getRandom().nextFloat() < 0.10f
				&& !level.getEntitiesOfClass(Wolf.class, player.getBoundingBox().inflate(8.0), Wolf::isBaby).isEmpty()) {
			give(player, SoulPower.WILD_FLUTE);
		}
		copy.raidWinsSeen = raids;
		copy.bredSeen = bred;
		SoulManager.set(player, copy);
	}

	private static void give(ServerPlayer player, SoulPower power) {
		ItemStack stack = new ItemStack(ModItems.SOUL_ITEMS.get(power));
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}
}
