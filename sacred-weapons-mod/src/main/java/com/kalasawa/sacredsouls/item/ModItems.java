package com.kalasawa.sacredsouls.item;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;

import com.kalasawa.sacredsouls.SacredSouls;
import com.kalasawa.sacredsouls.power.SoulPower;

public final class ModItems {
	public static final Map<SoulPower, SoulItem> SOUL_ITEMS = new EnumMap<>(SoulPower.class);

	public static Item SOUL_FLAME;
	public static Item SUMMONING_INCENSE;
	public static Item CLEANSING_WATER;
	public static Item SOUL_CODEX;

	private ModItems() {
	}

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SacredSouls.id(name));
		T item = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void init() {
		for (SoulPower power : SoulPower.values()) {
			SOUL_ITEMS.put(power, register(power.id, p -> new SoulItem(power, p), new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
		}
		SOUL_FLAME = register("soul_flame", SoulFlameItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
		SUMMONING_INCENSE = register("summoning_incense", IncenseItem::new, new Item.Properties().stacksTo(16));
		CLEANSING_WATER = register("cleansing_water", Item::new, new Item.Properties().stacksTo(16));
		SOUL_CODEX = register("soul_codex", CodexItem::new, new Item.Properties().stacksTo(1));

		ResourceKey<CreativeModeTab> tabKey = ResourceKey.create(Registries.CREATIVE_MODE_TAB, SacredSouls.id("souls"));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey, FabricItemGroup.builder()
				.title(Component.literal("Sacred Souls"))
				.icon(() -> new ItemStack(SOUL_ITEMS.get(SoulPower.TIME_CRYSTAL)))
				.displayItems((context, entries) -> {
					SOUL_ITEMS.values().forEach(entries::accept);
					entries.accept(SOUL_FLAME);
					entries.accept(SUMMONING_INCENSE);
					entries.accept(CLEANSING_WATER);
					entries.accept(SOUL_CODEX);
				})
				.build());
	}
}
