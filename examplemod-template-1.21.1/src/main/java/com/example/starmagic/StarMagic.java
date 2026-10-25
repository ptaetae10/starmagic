package com.example.starmagic;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(StarMagic.MODID)
public class StarMagic {
    public static final String MODID = "starmagic";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<StarStaffItem> STAR_STAFF = ITEMS.registerItem(
            "star_staff",
            StarStaffItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));

    public StarMagic(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(StarMagic::addToCreativeTab);
        NeoForge.EVENT_BUS.addListener(DelayedTasks::onServerTick);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(STAR_STAFF);
        }
    }
}
