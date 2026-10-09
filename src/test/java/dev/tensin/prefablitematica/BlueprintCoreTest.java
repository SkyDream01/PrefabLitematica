// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica;

import dev.tensin.prefablitematica.blueprint.*;
import dev.tensin.prefablitematica.config.BlueprintConfig;
import dev.tensin.prefablitematica.material.*;
import dev.tensin.prefablitematica.placement.BlueprintRotation;
import dev.tensin.prefablitematica.security.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import org.junit.jupiter.api.*;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class BlueprintCoreTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private BlueprintData data(List<BlueprintBlock> blocks, int x, int y, int z) { return new BlueprintData(UUID.randomUUID(), "House", x,y,z,blocks,new LinkedHashMap<>()); }
    @Test void batchesNeverOverfillAndReturnActualAcceptedCount() {
        var r = new MaterialRequirement("group:wooden_planks","minecraft:oak_planks","wooden_planks",100);
        assertEquals(30,r.offer(30)); assertEquals(30,r.offer(30)); assertEquals(40,r.offer(90)); assertEquals(0,r.offer(1)); assertEquals(0,r.remaining());
        assertThrows(IllegalArgumentException.class,()->r.offer(-1));
    }
    @Test void onlyAllGroupsCompleteMeansFullyCharged() {
        var d = data(List.of(),1,1,1); d.requirements.put("wood",new MaterialRequirement("wood","minecraft:oak_planks","wooden_planks",100));
        d.requirements.put("lava",new MaterialRequirement("lava","minecraft:lava_bucket","lava",2));
        d.requirements.get("wood").offer(100); assertFalse(d.fullyCharged()); d.fill(); assertTrue(d.fullyCharged()); d.reset(); assertEquals(0,d.charge());
    }
    @Test void materialListMovesCompletedRowsAfterAllOutstandingRows() {
        var d = data(List.of(), 1, 1, 1);
        for (int i = 0; i < 7; i++) d.requirements.put("row" + i, new MaterialRequirement("row" + i, "minecraft:stone", "exact", 2));
        d.requirements.get("row0").offer(2); d.requirements.get("row2").offer(2); d.requirements.get("row1").offer(1);
        assertEquals(List.of("row1", "row3", "row4", "row5", "row6", "row0", "row2"), d.requirementsForInput().stream().map(r -> r.key).toList());
        // Five outstanding requirements fill the whole first page, even if completed
        // requirements originally preceded them in the stored map.
        assertTrue(d.requirementsForInput().subList(0, 5).stream().allMatch(r -> r.remaining() > 0));
        d.requirements.get("row1").offer(1);
        assertEquals(List.of("row3", "row4", "row5", "row6", "row0", "row1", "row2"), d.requirementsForInput().stream().map(r -> r.key).toList());
        assertEquals(List.of("row0", "row1", "row2", "row3", "row4", "row5", "row6"), new ArrayList<>(d.requirements.keySet()));
        d.fill(); assertEquals(new ArrayList<>(d.requirements.values()), d.requirementsForInput());
        d.reset(); assertEquals(new ArrayList<>(d.requirements.values()), d.requirementsForInput());
    }
    @Test void slabAndSnowAndCandleCostsUseItemCounts() {
        var resolver = new BlockMaterialResolver(); var pos=BlockPos.ZERO;
        assertEquals(2,resolver.resolve(Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,SlabType.DOUBLE),Map.of(),pos).count());
        assertEquals(7,resolver.resolve(Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS,7),Map.of(),pos).count());
        assertEquals(4,resolver.resolve(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("minecraft:red_candle")).defaultBlockState().setValue(BlockStateProperties.CANDLES,4),Map.of(),pos).count());
        assertEquals(Items.REDSTONE,resolver.resolve(Blocks.REDSTONE_WIRE.defaultBlockState(),Map.of(),pos).item());
        assertEquals(Items.TORCH,resolver.resolve(Blocks.WALL_TORCH.defaultBlockState(),Map.of(),pos).item());
        assertEquals(Items.WHEAT_SEEDS,resolver.resolve(Blocks.WHEAT.defaultBlockState(),Map.of(),pos).item());
    }
    @Test void doorsRequireCompletePairAndChargeOneItem() {
        var lower=Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.LOWER);
        var upper=lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER);
        var states=Map.of(BlockPos.ZERO,lower,BlockPos.ZERO.above(),upper); var resolver=new BlockMaterialResolver();
        assertEquals(1,resolver.resolve(lower,states,BlockPos.ZERO).count()); assertEquals(0,resolver.resolve(upper,states,BlockPos.ZERO.above()).count());
        assertThrows(IllegalArgumentException.class,()->resolver.resolve(upper,Map.of(),BlockPos.ZERO));
    }
    @Test void bedsChargeOneAndRejectOrphanHead() {
        var foot=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("minecraft:red_bed")).defaultBlockState().setValue(BlockStateProperties.BED_PART,BedPart.FOOT);
        var head=foot.setValue(BlockStateProperties.BED_PART,BedPart.HEAD); var pos=BlockPos.ZERO.relative(foot.getValue(BlockStateProperties.HORIZONTAL_FACING));
        var states=Map.of(BlockPos.ZERO,foot,pos,head); var resolver=new BlockMaterialResolver();
        assertEquals(1,resolver.resolve(foot,states,BlockPos.ZERO).count()); assertEquals(0,resolver.resolve(head,states,pos).count());
        assertThrows(IllegalArgumentException.class,()->resolver.resolve(head,Map.of(),pos));
    }
    @Test void waterIsTwoOnceAndLavaOnlyCountsSources() {
        var resolver=new FluidMaterialResolver(); for(int i=0;i<10000;i++)resolver.accept(Blocks.WATER.defaultBlockState().getFluidState());
        resolver.accept(Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,true).getFluidState());
        for(int i=0;i<37;i++)resolver.accept(Blocks.LAVA.defaultBlockState().getFluidState());
        resolver.accept(Blocks.LAVA.defaultBlockState().setValue(BlockStateProperties.LEVEL,3).getFluidState());
        var requirements=new LinkedHashMap<String,MaterialRequirement>();resolver.finish(requirements);
        assertEquals(2,requirements.get("item:minecraft:water_bucket").required);assertEquals(37,requirements.get("item:minecraft:lava_bucket").required);
        var empty=new LinkedHashMap<String,MaterialRequirement>();new FluidMaterialResolver().finish(empty);assertTrue(empty.isEmpty());
    }
    @Test void containerItemsLootAndComponentsAreStripped() throws Exception {
        var nbt=TagParser.parseCompoundFully("{Items:[{id:'minecraft:diamond',count:64}],LootTable:'minecraft:chests/end_city_treasure',LootTableSeed:1L,components:{'minecraft:container':[{id:'minecraft:diamond'}]}}");
        assertTrue(BlueprintNbtSanitizer.sanitize(nbt,Blocks.CHEST.defaultBlockState(),16384).isEmpty());
    }
    @Test void comparatorRuntimeOutputIsPreservedAndBounded() throws Exception {
        var raw = TagParser.parseCompoundFully("{id:'minecraft:comparator',OutputSignal:1,Items:[{id:'minecraft:diamond',count:64}],components:{}}");
        var clean = BlueprintNbtSanitizer.prepareForExport(raw, Blocks.COMPARATOR.defaultBlockState());
        assertEquals(1, clean.getIntOr("OutputSignal", -1)); assertEquals(Set.of("OutputSignal"), clean.keySet());
        assertEquals(clean, BlueprintNbtSanitizer.sanitize(clean, Blocks.COMPARATOR.defaultBlockState(), 16384));
        for (String value : List.of("-1", "16", "'not a number'"))
            assertThrows(IllegalArgumentException.class, () -> BlueprintNbtSanitizer.sanitize(TagParser.parseCompoundFully("{OutputSignal:" + value + "}"), Blocks.COMPARATOR.defaultBlockState(), 16384));
    }
    @Test void scheduledTicksRoundtripPreservesTypesTimingPriorityAndOrder() throws Exception {
        var blocks = List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.OBSERVER.defaultBlockState(), null),
                new BlueprintBlock(BlockPos.ZERO.east(), Blocks.WATER.defaultBlockState(), null));
        var ticks = List.of(new BlueprintScheduledTick(BlockPos.ZERO, false, net.minecraft.resources.Identifier.parse("minecraft:observer"), 2, -3, 41),
                new BlueprintScheduledTick(BlockPos.ZERO.east(), true, net.minecraft.resources.Identifier.parse("minecraft:water"), 7, 2, 42));
        var source = new BlueprintData(UUID.randomUUID(), "Ticks", 2, 1, 1, blocks, ticks, new LinkedHashMap<>());
        var decoded = BlueprintSerializer.decode(BlueprintSerializer.encode(source), source.id, new BlueprintConfig());
        assertEquals(ticks, decoded.scheduledTicks);
        for (var invalid : List.of(new BlueprintScheduledTick(BlockPos.ZERO, false, net.minecraft.resources.Identifier.parse("minecraft:tnt"), 0, 0, 0),
                new BlueprintScheduledTick(BlockPos.ZERO.above(), false, ticks.getFirst().type(), 0, 0, 0),
                new BlueprintScheduledTick(BlockPos.ZERO, false, ticks.getFirst().type(), 0, 4, 0))) {
            var bad = new BlueprintData(source.id, source.name, 2, 1, 1, blocks, List.of(invalid), new LinkedHashMap<>());
            assertThrows(IOException.class, () -> BlueprintSerializer.decode(BlueprintSerializer.encode(bad), bad.id, new BlueprintConfig()));
        }
    }
    @Test void legacyBlueprintFormatWithoutTicksStillLoads() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(BlueprintSerializer.MAGIC); BlueprintSerializer.writeString(out, "Legacy");
            out.writeInt(1); out.writeInt(1); out.writeInt(1); out.writeInt(1);
            BlueprintSerializer.writeString(out, "minecraft:stone"); out.writeInt(0); out.writeInt(1);
            out.writeInt(0); out.writeInt(0); out.writeInt(0); out.writeInt(0);
            BlueprintSerializer.writeString(out, "minecraft:empty"); BlueprintSerializer.writeString(out, "");
        }
        var legacy = BlueprintSerializer.decode(bytes.toByteArray(), UUID.randomUUID(), new BlueprintConfig());
        assertEquals(Blocks.STONE.defaultBlockState(), legacy.blocks.getFirst().state()); assertTrue(legacy.scheduledTicks.isEmpty());
    }
    @Test void legacyComparatorRefreshPreservesChargeAndCannotExchangeTheBuilding() throws Exception {
        var state = Blocks.COMPARATOR.defaultBlockState().setValue(BlockStateProperties.POWERED, true);
        var source = data(List.of(new BlueprintBlock(BlockPos.ZERO, state, new CompoundTag())), 1, 1, 1);
        byte[] expanded = BoundedStreams.expand(BlueprintSerializer.encode(source), 33554432, 100663296);
        java.nio.ByteBuffer.wrap(expanded).putInt(BlueprintSerializer.MAGIC);
        var legacyBytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(legacyBytes)) { gzip.write(expanded, 0, expanded.length - 6); }
        var old = BlueprintSerializer.decode(legacyBytes.toByteArray(), source.id, new BlueprintConfig());
        assertTrue(old.requiresReimport);
        old.requirements.put("comparator", new MaterialRequirement("comparator", "minecraft:comparator", "exact", 1)); old.fill();
        var nbt = new CompoundTag(); nbt.putInt("OutputSignal", 1);
        var replacement = new BlueprintData(old.id, old.name, 1, 1, 1, List.of(new BlueprintBlock(BlockPos.ZERO, state, nbt)), new LinkedHashMap<>());
        replacement.requirements.put("comparator", new MaterialRequirement("comparator", "minecraft:comparator", "exact", 1));
        BlueprintManager.refreshLegacy(old, replacement);
        assertTrue(replacement.fullyCharged()); assertFalse(replacement.requiresReimport); assertEquals(old.id, replacement.id);
        var other = new BlueprintData(old.id, old.name, 1, 1, 1, List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null)), replacement.requirements);
        assertThrows(IllegalArgumentException.class, () -> BlueprintManager.refreshLegacy(old, other));
        replacement.requiresReimport = true;
        assertThrows(IllegalArgumentException.class, () -> BlueprintManager.refreshLegacy(old, replacement));
        replacement.requiresReimport = false;
        replacement.requirements.get("comparator").required = 2;
        assertThrows(IllegalArgumentException.class, () -> BlueprintManager.refreshLegacy(old, replacement));
    }
    @Test void portalWarningsRoundtripAndAllEarlierFormatsRemainReadable() throws Exception {
        var source = data(List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.AIR.defaultBlockState(), null)), 1, 1, 1);
        source.portalNeedsIgnition = true; source.portalNeedsSlicing = true;
        var encoded = BlueprintSerializer.encode(source);
        var decoded = BlueprintSerializer.decode(encoded, source.id, new BlueprintConfig());
        assertTrue(decoded.portalNeedsIgnition); assertTrue(decoded.portalNeedsSlicing);
        assertEquals(source.blocks, decoded.blocks);
        byte[] expanded = BoundedStreams.expand(encoded, 33554432, 100663296);
        for (int format = 1; format <= 3; format++) {
            var historical = expanded.clone(); java.nio.ByteBuffer.wrap(historical).putInt(0x42505230 + format);
            int removed = switch (format) { case 1 -> 6; case 2 -> 2; default -> 1; };
            var bytes = new ByteArrayOutputStream();
            try (var gzip = new GZIPOutputStream(bytes)) { gzip.write(historical, 0, historical.length - removed); }
            var old = BlueprintSerializer.decode(bytes.toByteArray(), source.id, new BlueprintConfig());
            assertEquals(source.blocks, old.blocks); assertEquals(format == 3, old.portalNeedsIgnition);
            assertFalse(old.portalNeedsSlicing); assertFalse(old.requiresReimport);
        }
    }
    @Test void cosmeticSignSurvivesButCommandsDoNot() throws Exception {
        var nbt=TagParser.parseCompoundFully("{front_text:{messages:[{text:'Hello',click_event:{action:'run_command',command:'/op test'}}]},Items:[]}");
        String clean=BlueprintNbtSanitizer.sanitize(nbt,Blocks.OAK_SIGN.defaultBlockState(),16384).toString();
        assertTrue(clean.contains("Hello"));assertFalse(clean.contains("run_command"));assertFalse(clean.contains("Items"));
    }
    @Test void nbtDepthAndSizeAreBounded() {
        CompoundTag root=new CompoundTag(),child=root;for(int i=0;i<18;i++){var next=new CompoundTag();child.put("nested",next);child=next;}
        CompoundTag nbt=root;assertThrows(IllegalArgumentException.class,()->BlueprintNbtSanitizer.sanitize(nbt,Blocks.CHEST.defaultBlockState(),16384));
    }
    @Test void blockEntityIdsMustMatchActualBlock() throws Exception {
        var nbt=TagParser.parseCompoundFully("{id:'minecraft:furnace'}");
        assertThrows(IllegalArgumentException.class,()->BlueprintNbtSanitizer.sanitize(nbt,Blocks.CHEST.defaultBlockState(),16384));
        assertTrue(BlueprintNbtSanitizer.sanitize(TagParser.parseCompoundFully("{id:'minecraft:chest'}"),Blocks.CHEST.defaultBlockState(),16384).isEmpty());
    }
    @Test void schematicExportIgnoresStaleTileMapsButServerStillRejectsUnexpectedNbt() throws Exception {
        var stale = TagParser.parseCompoundFully("{id:'minecraft:chest',Items:[{id:'minecraft:diamond',count:64}]}");
        assertNull(BlueprintNbtSanitizer.prepareForExport(stale, Blocks.STONE.defaultBlockState()));
        assertTrue(BlueprintNbtSanitizer.prepareForExport(stale, Blocks.CHEST.defaultBlockState()).isEmpty());
        var invalid = data(List.of(new BlueprintBlock(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), stale)), 1, 1, 1);
        assertThrows(IOException.class, () -> BlueprintSerializer.decode(BlueprintSerializer.encode(invalid), invalid.id, new BlueprintConfig()));
    }
    @Test void containerContentsAreDroppedBeforeApplyingNetworkNbtLimits() {
        var input = new CompoundTag(); var items = new ListTag();
        for (int i = 0; i < 300; i++) { var item = new CompoundTag(); item.putString("id", "minecraft:diamond"); items.add(item); }
        input.put("Items", items);
        assertTrue(BlueprintNbtSanitizer.prepareForExport(input, Blocks.CHEST.defaultBlockState()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> BlueprintNbtSanitizer.sanitize(input, Blocks.CHEST.defaultBlockState(), 16384));
    }
    @Test void rotationKeepsAllCornersInsideRotatedBounds() {
        for(var rot:BlueprintRotation.values())for(int x=0;x<3;x++)for(int z=0;z<5;z++){
            var p=rot.apply(new BlockPos(x,2,z),3,5);int width=rot.ordinal()%2==0?3:5,depth=rot.ordinal()%2==0?5:3;
            assertTrue(p.getX()>=0&&p.getX()<width&&p.getZ()>=0&&p.getZ()<depth);assertEquals(2,p.getY());
        }
    }
    @Test void wireRoundtripPreservesOriginalStateFluidAndRelativeCoordinates() throws Exception {
        var state=Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,true);
        var d=data(List.of(new BlueprintBlock(new BlockPos(2,1,3),state,null)),3,2,4);
        var read=BlueprintSerializer.decode(BlueprintSerializer.encode(d),d.id,new BlueprintConfig());assertEquals(d.blocks,read.blocks);assertTrue(read.requirements.isEmpty());
    }
    @Test void duplicateAndOutOfBoundsCoordinatesAreRejected() throws Exception {
        var b=new BlueprintBlock(BlockPos.ZERO,Blocks.STONE.defaultBlockState(),null);
        assertThrows(IOException.class,()->BlueprintSerializer.decode(BlueprintSerializer.encode(data(List.of(b,b),2,1,1)),UUID.randomUUID(),new BlueprintConfig()));
        assertThrows(IOException.class,()->BlueprintSerializer.decode(BlueprintSerializer.encode(data(List.of(new BlueprintBlock(new BlockPos(-1,0,0),b.state(),null)),1,1,1)),UUID.randomUUID(),new BlueprintConfig()));
    }
    @Test void decompressionBombsAreRejected() throws Exception {
        var bytes=new ByteArrayOutputStream();try(var gzip=new GZIPOutputStream(bytes)){gzip.write(new byte[100000]);}
        assertThrows(IOException.class,()->BoundedStreams.expand(bytes.toByteArray(),10000,1000));
    }
}
