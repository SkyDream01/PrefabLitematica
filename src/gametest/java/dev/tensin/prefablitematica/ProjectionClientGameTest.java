// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica;

import com.mojang.blaze3d.platform.InputConstants;
import dev.tensin.prefablitematica.blueprint.*;
import dev.tensin.prefablitematica.client.BlueprintProjectionClient;
import dev.tensin.prefablitematica.client.screen.BlueprintProjectionScreen;
import dev.tensin.prefablitematica.integration.litematica.*;
import dev.tensin.prefablitematica.item.BlueprintItem;
import dev.tensin.prefablitematica.placement.BlueprintRotation;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.*;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.nio.file.*;
import java.util.*;

/** Only the panel may change coordinates/rotation, show a projection or execute placement. */
public final class ProjectionClientGameTest implements FabricClientGameTest {
    private record Fixture(UUID id, BlockPos origin) {}
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var reload = context.computeOnClient(client -> {
                client.options.languageCode = "zh_cn"; client.getLanguageManager().setSelected("zh_cn"); return client.reloadResourcePacks();
            });
            context.waitFor(client -> reload.isDone() && client.gui.overlay() == null);
            Fixture fixture = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var origin = player.blockPosition().above(3).south(4);
                var blocks = new ArrayList<BlueprintBlock>();
                for (int x = 0; x < 3; x++) for (int z = 0; z < 2; z++) {
                    blocks.add(new BlueprintBlock(new BlockPos(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState(), null));
                    var roof = x == 0 && z == 0 ? Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH) : Blocks.STONE_BRICKS.defaultBlockState();
                    blocks.add(new BlueprintBlock(new BlockPos(x, 2, z), roof, null));
                }
                for (int x : new int[]{0, 2}) for (int z = 0; z < 2; z++) blocks.add(new BlueprintBlock(new BlockPos(x, 1, z), Blocks.GLASS.defaultBlockState(), null));
                var data = new BlueprintData(UUID.randomUUID(), "Projection house", 3, 3, 2, blocks, new LinkedHashMap<>());
                var manager = PrefabLitematicaMod.manager(server); var analysis = manager.new Analysis(data); while (!analysis.tick(4096)) {}
                data.fill(); manager.create(data);
                for (var pos : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(5, 9, 5))) player.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                player.level().setBlockAndUpdate(origin.offset(1, 1, 0), Blocks.DIAMOND_BLOCK.defaultBlockState());
                player.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.loaded(data)); player.containerMenu.broadcastChanges(); player.setNoGravity(true);
                player.teleportTo(player.level(), origin.getX() + 6.5, origin.getY() + 4, origin.getZ() - 7.5, Set.of(), 36, 18, true);
                return new Fixture(data.id, origin);
            });
            context.waitFor(client -> fixture.id.equals(BlueprintItem.id(client.player.getMainHandItem())));
            context.getInput().pressKey(BlueprintProjectionClient.OPEN_UI); context.waitForScreen(BlueprintProjectionScreen.class);
            context.waitFor(client -> BlueprintProjectionClient.INSTANCE.data() != null);
            setCoordinates(context, fixture.origin);
            context.waitFor(client -> BlueprintProjectionClient.INSTANCE.origin().equals(fixture.origin));
            context.runOnClient(client -> {
                var preview = BlueprintProjectionClient.INSTANCE;
                if (preview.shown() || preview.projection() != null || preview.litematicaVisible() || preview.canConfirm()) throw new AssertionError("Opening the panel must not show or execute a projection");
                if (!preview.info().getString().contains("16") || !preview.chargeText().equals("100%")) throw new AssertionError("Panel must show real blueprint data");
                var execute = executeButton((BlueprintProjectionScreen) client.gui.screen());
                if (execute.active || execute.visible) throw new AssertionError("Execute must stay hidden before Show projection");
            });
            context.getInput().lookAt(fixture.origin.offset(4, 1, 4));
            context.waitTicks(5); screenshot(context, "blueprint-projection-panel");
            context.clickScreenButton("projection.prefablitematica.show");
            context.waitFor(client -> BlueprintProjectionClient.INSTANCE.shown() && BlueprintProjectionClient.INSTANCE.conflicts().cardinality() == 1);
            if (LitematicaIntegration.available()) context.waitFor(client -> BlueprintProjectionClient.INSTANCE.litematicaVisible());
            context.runOnClient(client -> { if (executeButton((BlueprintProjectionScreen) client.gui.screen()).active) throw new AssertionError("Red conflicts must disable Execute"); });
            context.waitTicks(12); screenshot(context, "blueprint-projection-conflict");
            context.clickScreenButton("projection.prefablitematica.inspect"); context.waitForScreen(null);
            for (int key : new int[]{InputConstants.KEY_R, InputConstants.KEY_LEFT, InputConstants.KEY_PAGEUP, InputConstants.KEY_RETURN, InputConstants.KEY_BACKSPACE}) context.getInput().pressKey(key);
            context.waitTicks(5);
            context.runOnClient(client -> {
                var preview = BlueprintProjectionClient.INSTANCE;
                preview.move(fixture.origin.above(), BlueprintRotation.CW_90); preview.confirm();
                if (!preview.active() || !preview.origin().equals(fixture.origin) || preview.rotation() != BlueprintRotation.NONE) throw new AssertionError("World keys or external pose calls must not change or execute the projection");
            });
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var data = PrefabLitematicaMod.manager(server).get(fixture.id);
                if (!data.fullyCharged() || data.locked || !player.level().getBlockState(fixture.origin).isAir()) throw new AssertionError("Preview must preserve world and charge");
                player.getInventory().setItem(1, player.getMainHandItem());
                player.setItemInHand(InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE)); player.containerMenu.broadcastChanges();
            });
            context.waitFor(client -> !BlueprintProjectionClient.INSTANCE.holdingBlueprint()); context.waitTicks(25);
            context.runOnClient(client -> { if (!BlueprintProjectionClient.INSTANCE.shown()) throw new AssertionError("Switching to a tool must keep the projection"); });
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); player.level().setBlockAndUpdate(fixture.origin.offset(1, 1, 0), Blocks.AIR.defaultBlockState());
                player.setItemInHand(InteractionHand.MAIN_HAND, player.getInventory().getItem(1)); player.getInventory().setItem(1, net.minecraft.world.item.ItemStack.EMPTY); player.containerMenu.broadcastChanges();
            });
            context.waitFor(client -> BlueprintProjectionClient.INSTANCE.canConfirm());
            context.getInput().pressKey(InputConstants.KEY_RETURN); context.waitTicks(4);
            world.getServer().runOnServer(server -> { if (!PrefabLitematicaMod.manager(server).get(fixture.id).fullyCharged()) throw new AssertionError("Enter in the world must never execute placement"); });
            context.getInput().pressKey(BlueprintProjectionClient.OPEN_UI); context.waitForScreen(BlueprintProjectionScreen.class);
            var moved = fixture.origin.offset(1, 1, 1); setCoordinates(context, moved);
            context.waitFor(client -> BlueprintProjectionClient.INSTANCE.origin().equals(moved) && BlueprintProjectionClient.INSTANCE.canConfirm());
            for (int turns = 1; turns <= 4; turns++) {
                context.clickScreenButton("+90°"); int expected = turns % 4;
                context.waitFor(client -> BlueprintProjectionClient.INSTANCE.rotation().ordinal() == expected && BlueprintProjectionClient.INSTANCE.canConfirm());
                if (LitematicaIntegration.available()) context.runOnClient(client -> assertLitematicaMatches(moved));
            }
            for (int expected : new int[]{3, 2, 1}) {
                context.clickScreenButton("−90°");
                context.waitFor(client -> BlueprintProjectionClient.INSTANCE.rotation().ordinal() == expected && BlueprintProjectionClient.INSTANCE.canConfirm());
            }
            context.getInput().lookAt(moved.offset(4, 1, 4)); context.waitTicks(100); screenshot(context, "blueprint-projection-ready");
            context.clickScreenButton("projection.prefablitematica.confirm");
            world.getServer().waitFor(server -> { var data = PrefabLitematicaMod.manager(server).get(fixture.id); return !data.fullyCharged() && !data.locked; });
            context.waitFor(client -> !BlueprintProjectionClient.INSTANCE.active() && client.gui.screen() == null);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst(); var data = PrefabLitematicaMod.manager(server).get(fixture.id);
                for (var block : data.blocks) {
                    var pos = moved.offset(BlueprintRotation.CW_90.apply(block.relativePos(), data.sizeX, data.sizeZ));
                    if (player.level().getBlockState(pos) != block.state().rotate(BlueprintRotation.CW_90.vanilla)) throw new AssertionError("Executed state must match the adjusted preview");
                }
            });
            context.getInput().pressKey(BlueprintProjectionClient.OPEN_UI); context.waitTicks(10);
            context.runOnClient(client -> { if (BlueprintProjectionClient.INSTANCE.active() || client.gui.screen() != null) throw new AssertionError("Uncharged blueprint must not open the panel"); });
            world.getServer().runOnServer(server -> { var data = PrefabLitematicaMod.manager(server).get(fixture.id); data.fill(); PrefabLitematicaMod.manager(server).saveProgress(data); });
            context.getInput().pressKey(BlueprintProjectionClient.OPEN_UI); context.waitForScreen(BlueprintProjectionScreen.class);
            context.waitFor(client -> BlueprintProjectionClient.INSTANCE.data() != null);
            setCoordinates(context, moved.above(6)); context.clickScreenButton("projection.prefablitematica.show");
            context.waitFor(client -> BlueprintProjectionClient.INSTANCE.canConfirm());
            context.clickScreenButton("projection.prefablitematica.cancel"); context.waitFor(client -> !BlueprintProjectionClient.INSTANCE.active());
            world.getServer().waitFor(server -> PrefabLitematicaMod.previews(server).session(server.getPlayerList().getPlayers().getFirst()) == null);
            world.getServer().runOnServer(server -> { if (!PrefabLitematicaMod.manager(server).get(fixture.id).fullyCharged()) throw new AssertionError("Cancel must preserve charge"); });
            if (LitematicaIntegration.available()) context.runOnClient(client -> {
                if (LitematicaIntegration.memorySources().stream().anyMatch(source -> source.name().startsWith("[Blueprint]"))) throw new AssertionError("Execute/cancel must remove temporary placements");
            });
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void setCoordinates(ClientGameTestContext context, BlockPos pos) {
        context.runOnClient(client -> {
            var screen = (BlueprintProjectionScreen) client.gui.screen(); var axes = screen.children().stream().filter(w -> w instanceof EditBox).map(w -> (EditBox) w).toList();
            axes.get(0).setValue(Integer.toString(pos.getX())); axes.get(1).setValue(Integer.toString(pos.getY())); axes.get(2).setValue(Integer.toString(pos.getZ()));
        });
    }
    private static Button executeButton(BlueprintProjectionScreen screen) {
        String label = net.minecraft.network.chat.Component.translatable("projection.prefablitematica.confirm").getString();
        return screen.children().stream().filter(w -> w instanceof Button b && b.getMessage().getString().equals(label)).map(w -> (Button) w).findFirst().orElseThrow();
    }
    private static void assertLitematicaMatches(BlockPos moved) throws Exception {
        var preview = BlueprintProjectionClient.INSTANCE; var projection = preview.projection();
        if (projection == null || !projection.pose().origin().equals(moved) || projection.pose().rotation() != preview.rotation()) throw new AssertionError("Litematica corner must track panel coordinates");
        Object placement = projection.placement();
        if ((boolean) placement.getClass().getMethod("shouldBeSaved").invoke(placement)) throw new AssertionError("Projection must be temporary");
        var source = new LitematicaIntegration.Source("Projection house", "", LitematicaIntegration.SourceKind.PLACEMENT, placement, null, null);
        var capture = new LitematicaIntegration.Capture(source); while (!capture.tick(4096)) {} var actual = new HashSet<>(capture.finish().blocks); var data = preview.data();
        for (var block : data.blocks) if (!actual.contains(new BlueprintBlock(preview.rotation().apply(block.relativePos(), data.sizeX, data.sizeZ), block.state().rotate(preview.rotation().vanilla), null)))
            throw new AssertionError("Projection must match both rotated coordinates and directional block states");
    }
    private static void screenshot(ClientGameTestContext context, String name) throws java.io.IOException {
        Path source = context.takeScreenshot(name);
        Path target = LitematicaIntegration.available() ? Path.of(System.getProperty("prefablitematica.projectDir"), "docs", "screenshots", name + ".png")
                : Path.of(System.getProperty("prefablitematica.projectDir"), "build", "projection-fallback-screenshots", name + ".png");
        Files.createDirectories(target.getParent()); Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
