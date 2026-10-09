// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica;

import dev.tensin.prefablitematica.blueprint.BlueprintManager;
import dev.tensin.prefablitematica.block.BlueprintWorkbenchBlock;
import dev.tensin.prefablitematica.block.entity.BlueprintWorkbenchBlockEntity;
import dev.tensin.prefablitematica.config.BlueprintConfig;
import dev.tensin.prefablitematica.item.*;
import dev.tensin.prefablitematica.network.BlueprintNetworking;
import dev.tensin.prefablitematica.placement.BlueprintPlacementManager;
import dev.tensin.prefablitematica.placement.BlueprintPreviewManager;
import dev.tensin.prefablitematica.screen.BlueprintWorkbenchScreenHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.*;
import java.util.Set;

public final class PrefabLitematicaMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("PrefabLitematica");
    public static BlueprintConfig CONFIG;
    public static final TicketType PLACEMENT_TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.parse("prefablitematica:placement"), new TicketType(0, TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
    public static final Identifier WORKBENCH_ID = Identifier.parse("prefablitematica:workbench");
    public static final BlueprintWorkbenchBlock WORKBENCH = Registry.register(BuiltInRegistries.BLOCK, WORKBENCH_ID,
            new BlueprintWorkbenchBlock(BlockBehaviour.Properties.of().strength(2.5f).setId(ResourceKey.create(Registries.BLOCK, WORKBENCH_ID))));
    public static final Item WORKBENCH_ITEM = Registry.register(BuiltInRegistries.ITEM, WORKBENCH_ID,
            new BlockItem(WORKBENCH, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, WORKBENCH_ID)).useBlockDescriptionPrefix()));
    public static final BlueprintItem BLUEPRINT = Registry.register(BuiltInRegistries.ITEM, Identifier.parse("prefablitematica:blueprint"),
            new BlueprintItem(new Item.Properties().stacksTo(1).setId(ResourceKey.create(Registries.ITEM, Identifier.parse("prefablitematica:blueprint")))));
    public static final BlankBlueprintItem BLANK_BLUEPRINT = Registry.register(BuiltInRegistries.ITEM, Identifier.parse("prefablitematica:blank_blueprint"),
            new BlankBlueprintItem(new Item.Properties().stacksTo(1).setId(ResourceKey.create(Registries.ITEM, Identifier.parse("prefablitematica:blank_blueprint")))));
    public static final CreativeBlueprintChargeBatteryItem BATTERY = Registry.register(BuiltInRegistries.ITEM, Identifier.parse("prefablitematica:creative_charge_battery"),
            new CreativeBlueprintChargeBatteryItem(new Item.Properties().stacksTo(64).rarity(Rarity.EPIC).setId(ResourceKey.create(Registries.ITEM, Identifier.parse("prefablitematica:creative_charge_battery")))));
    public static final BlockEntityType<BlueprintWorkbenchBlockEntity> WORKBENCH_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, WORKBENCH_ID,
            new BlockEntityType<>(BlueprintWorkbenchBlockEntity::new, Set.of(WORKBENCH)));
    public static final MenuType<BlueprintWorkbenchScreenHandler> WORKBENCH_MENU = Registry.register(BuiltInRegistries.MENU, WORKBENCH_ID,
            new MenuType<>(BlueprintWorkbenchScreenHandler::new, FeatureFlags.VANILLA_SET));
    private static BlueprintManager manager;
    private static BlueprintPlacementManager placements;
    private static BlueprintPreviewManager previews;
    public static BlueprintManager manager(MinecraftServer server) { if (manager == null) manager = new BlueprintManager(server); return manager; }
    public static BlueprintPlacementManager placements(MinecraftServer server) { if (placements == null) placements = new BlueprintPlacementManager(server); return placements; }
    public static BlueprintPlacementManager activePlacements() { return placements; }
    public static BlueprintPreviewManager previews(MinecraftServer server) { if (previews == null) previews = new BlueprintPreviewManager(server); return previews; }
    @Override public void onInitialize() {
        CONFIG = BlueprintConfig.load();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Identifier.parse("prefablitematica:blueprints"), FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.prefablitematica")).icon(() -> new ItemStack(BLANK_BLUEPRINT))
                .displayItems((parameters, entries) -> { entries.accept(BLANK_BLUEPRINT); entries.accept(WORKBENCH_ITEM); entries.accept(BATTERY); }).build());
        BlueprintNetworking.register();
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) ->
                placements != null && placements.reserved(world, hit.getBlockPos()) ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                placements != null && placements.reserved(world, pos) ? net.minecraft.world.InteractionResult.FAIL : net.minecraft.world.InteractionResult.PASS);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> { manager = new BlueprintManager(server); placements = new BlueprintPlacementManager(server); });
        ServerTickEvents.END_SERVER_TICK.register(server -> { placements(server).tick(); BlueprintNetworking.tick(server); previews(server).tick(); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { BlueprintNetworking.stop(); if (placements != null) placements.stop(); if (previews != null) previews.stop(); });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { manager = null; placements = null; previews = null; });
    }
}
