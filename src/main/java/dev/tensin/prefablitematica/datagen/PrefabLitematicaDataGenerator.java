// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.datagen;

import com.google.gson.*;
import dev.tensin.prefablitematica.material.MaterialGroup;
import net.fabricmc.fabric.api.datagen.v1.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Curated vanilla categories are generated as editable item tags; no name heuristics run during charging. */
public final class PrefabLitematicaDataGenerator implements DataGeneratorEntrypoint {
    @Override public void onInitializeDataGenerator(FabricDataGenerator generator) { generator.createPack().addProvider(Provider::new); }
    public static final class Provider implements DataProvider {
        private final Path root;
        public Provider(FabricPackOutput output) { root = output.getOutputFolder(); }
        @Override public String getName() { return "PrefabLitematica resources, recipes and material tags"; }
        @Override public CompletableFuture<?> run(CachedOutput cache) {
            try {
                Map<String, JsonElement> files = resources();
                List<CompletableFuture<?>> writes = new ArrayList<>();
                files.forEach((path, json) -> writes.add(DataProvider.saveStable(cache, json, root.resolve(path))));
                BlueprintTextures.generate(cache, root);
                return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
            } catch (Exception e) { return CompletableFuture.failedFuture(e); }
        }
    }
    private static JsonElement json(String text) { return JsonParser.parseString(text); }
    public static Map<String, JsonElement> resources() throws IOException {
        Map<String, JsonElement> out = new TreeMap<>(); Map<MaterialGroup, LinkedHashSet<String>> tags = new EnumMap<>(MaterialGroup.class);
        for (MaterialGroup g : MaterialGroup.values()) tags.put(g, new LinkedHashSet<>());
        Map<MaterialGroup, String> vanilla = Map.ofEntries(
                Map.entry(MaterialGroup.WOODEN_PLANKS,"planks"), Map.entry(MaterialGroup.WOODEN_SLABS,"wooden_slabs"), Map.entry(MaterialGroup.WOODEN_STAIRS,"wooden_stairs"),
                Map.entry(MaterialGroup.WOODEN_DOORS,"wooden_doors"), Map.entry(MaterialGroup.WOODEN_TRAPDOORS,"wooden_trapdoors"), Map.entry(MaterialGroup.WOODEN_FENCES,"wooden_fences"),
                Map.entry(MaterialGroup.WOODEN_FENCE_GATES,"fence_gates"), Map.entry(MaterialGroup.WOODEN_BUTTONS,"wooden_buttons"), Map.entry(MaterialGroup.WOODEN_PRESSURE_PLATES,"wooden_pressure_plates"),
                Map.entry(MaterialGroup.WOOL,"wool"), Map.entry(MaterialGroup.WOOL_SLABS,"wool_slabs"), Map.entry(MaterialGroup.WOOL_STAIRS,"wool_stairs"), Map.entry(MaterialGroup.CARPETS,"wool_carpets"),
                Map.entry(MaterialGroup.CONCRETE_SLABS,"concrete_slabs"), Map.entry(MaterialGroup.CONCRETE_STAIRS,"concrete_stairs"),
                Map.entry(MaterialGroup.CANDLES,"candles"), Map.entry(MaterialGroup.BEDS,"beds"), Map.entry(MaterialGroup.BANNERS,"banners"),
                Map.entry(MaterialGroup.LEAVES,"leaves"), Map.entry(MaterialGroup.SAPLINGS,"saplings"), Map.entry(MaterialGroup.WOODEN_SIGNS,"signs"), Map.entry(MaterialGroup.WOODEN_HANGING_SIGNS,"hanging_signs"));
        vanilla.forEach((group, tag) -> tags.get(group).add("#minecraft:" + tag));
        for (String id : vanillaTag("logs")) {
            boolean stripped = id.contains(":stripped_"); boolean bark = id.endsWith("_wood") || id.endsWith("_hyphae");
            tags.get(bark ? stripped ? MaterialGroup.STRIPPED_WOOD : MaterialGroup.WOOD : stripped ? MaterialGroup.STRIPPED_LOGS : MaterialGroup.LOGS).add(id);
        }
        String[] colors = {"white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"};
        Map<MaterialGroup,String> colored = Map.of(MaterialGroup.CONCRETE,"concrete", MaterialGroup.CONCRETE_POWDER,"concrete_powder", MaterialGroup.STAINED_GLASS,"stained_glass",
                MaterialGroup.STAINED_GLASS_PANES,"stained_glass_pane", MaterialGroup.TERRACOTTA,"terracotta", MaterialGroup.GLAZED_TERRACOTTA,"glazed_terracotta");
        colored.forEach((group, suffix) -> { for (String color : colors) tags.get(group).add("minecraft:" + color + "_" + suffix); });
        add(tags, MaterialGroup.STAINED_GLASS, "glass"); add(tags, MaterialGroup.STAINED_GLASS_PANES, "glass_pane"); add(tags, MaterialGroup.TERRACOTTA, "terracotta");
        add(tags, MaterialGroup.SAND, "sand","red_sand");
        add(tags, MaterialGroup.SANDSTONE_BLOCKS,"sandstone","cut_sandstone","chiseled_sandstone","smooth_sandstone","red_sandstone","cut_red_sandstone","chiseled_red_sandstone","smooth_red_sandstone");
        add(tags, MaterialGroup.SANDSTONE_SLABS,"sandstone_slab","cut_sandstone_slab","smooth_sandstone_slab","red_sandstone_slab","cut_red_sandstone_slab","smooth_red_sandstone_slab");
        add(tags, MaterialGroup.SANDSTONE_STAIRS,"sandstone_stairs","smooth_sandstone_stairs","red_sandstone_stairs","smooth_red_sandstone_stairs");
        add(tags, MaterialGroup.STONE_BLOCKS,"stone","cobblestone","mossy_cobblestone","smooth_stone","andesite","polished_andesite","diorite","polished_diorite","granite","polished_granite",
                "stone_bricks","mossy_stone_bricks","cracked_stone_bricks","chiseled_stone_bricks","deepslate","cobbled_deepslate","polished_deepslate","deepslate_bricks","cracked_deepslate_bricks","deepslate_tiles","cracked_deepslate_tiles","chiseled_deepslate",
                "tuff","polished_tuff","tuff_bricks","chiseled_tuff","chiseled_tuff_bricks","blackstone","polished_blackstone","polished_blackstone_bricks","cracked_polished_blackstone_bricks","chiseled_polished_blackstone",
                "bricks","mud_bricks","nether_bricks","red_nether_bricks","chiseled_nether_bricks","cracked_nether_bricks","end_stone","end_stone_bricks","prismarine","prismarine_bricks","dark_prismarine",
                "quartz_block","quartz_bricks","quartz_pillar","chiseled_quartz_block","smooth_quartz","purpur_block","purpur_pillar","basalt","polished_basalt","smooth_basalt","calcite","dripstone_block");
        for (String id : BuiltInRegistries.ITEM.keySet().stream().filter(id -> id.getNamespace().equals("minecraft")).map(Object::toString).sorted().toList()) {
            if (id.contains("copper") && (id.endsWith("copper") || id.endsWith("copper_block"))) tags.get(MaterialGroup.COPPER_BLOCKS).add(id);
            if (id.contains("copper") && id.endsWith("_grate")) tags.get(MaterialGroup.COPPER_GRATES).add(id);
        }
        classifyShape(tags, "slabs", "wooden_slabs", MaterialGroup.STONE_SLABS, MaterialGroup.COPPER_SLABS, MaterialGroup.SANDSTONE_SLABS, MaterialGroup.WOOL_SLABS, MaterialGroup.CONCRETE_SLABS);
        classifyShape(tags, "stairs", "wooden_stairs", MaterialGroup.STONE_STAIRS, MaterialGroup.COPPER_STAIRS, MaterialGroup.SANDSTONE_STAIRS, MaterialGroup.WOOL_STAIRS, MaterialGroup.CONCRETE_STAIRS);
        tags.get(MaterialGroup.STONE_WALLS).add("#minecraft:walls");
        for (var entry : tags.entrySet()) {
            JsonObject tag = new JsonObject(); tag.addProperty("replace", false); JsonArray values = new JsonArray(); entry.getValue().forEach(values::add); tag.add("values",values);
            out.put("data/prefablitematica/tags/item/materials/" + entry.getKey().id() + ".json",tag);
        }
        out.put("data/prefablitematica/tags/block/forbidden_blocks.json", json("{\"replace\":false,\"values\":[\"minecraft:command_block\",\"minecraft:chain_command_block\",\"minecraft:repeating_command_block\",\"minecraft:structure_block\",\"minecraft:jigsaw\",\"minecraft:barrier\",\"minecraft:light\",\"minecraft:nether_portal\",\"minecraft:moving_piston\",\"prefablitematica:workbench\"]}"));
        out.put("data/prefablitematica/recipe/prefablitematica.json", json("{\"type\":\"minecraft:crafting_shapeless\",\"category\":\"misc\",\"ingredients\":[\"minecraft:book\",\"minecraft:lapis_lazuli\",\"minecraft:redstone\",\"minecraft:ink_sac\"],\"result\":{\"id\":\"prefablitematica:blank_blueprint\",\"count\":1}}"));
        out.put("data/prefablitematica/recipe/workbench.json", json("{\"type\":\"minecraft:crafting_shaped\",\"category\":\"misc\",\"pattern\":[\" B \",\"LIR\",\" O \"],\"key\":{\"B\":\"minecraft:book\",\"L\":\"minecraft:lapis_block\",\"I\":\"minecraft:ink_sac\",\"R\":\"minecraft:redstone_block\",\"O\":\"minecraft:obsidian\"},\"result\":{\"id\":\"prefablitematica:workbench\",\"count\":1}}"));
        out.put("data/prefablitematica/loot_table/blocks/workbench.json", json("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"prefablitematica:workbench\"}],\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}"));
        out.put("data/minecraft/tags/block/mineable/axe.json",json("{\"replace\":false,\"values\":[\"prefablitematica:workbench\"]}"));
        out.put("assets/prefablitematica/blockstates/workbench.json",json("{\"variants\":{\"\":{\"model\":\"prefablitematica:block/workbench\"}}}"));
        out.put("assets/prefablitematica/models/block/workbench.json",json("{\"parent\":\"minecraft:block/cube\",\"textures\":{\"down\":\"minecraft:block/oak_planks\",\"up\":\"prefablitematica:block/workbench_top\",\"north\":\"prefablitematica:block/workbench_front\",\"south\":\"prefablitematica:block/workbench_front\",\"east\":\"prefablitematica:block/workbench_side\",\"west\":\"prefablitematica:block/workbench_side\",\"particle\":\"prefablitematica:block/workbench_side\"}}"));
        for (String id : new String[]{"blueprint","blank_blueprint","creative_charge_battery","workbench"}) {
            out.put("assets/prefablitematica/items/" + id + ".json", json("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"prefablitematica:" + (id.equals("workbench") ? "block/" : "item/") + id + "\"}}"));
            if (!id.equals("workbench")) out.put("assets/prefablitematica/models/item/" + id + ".json",json("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"prefablitematica:item/" + id + "\"}}"));
        }
        out.put("assets/prefablitematica/lang/en_us.json",language(false)); out.put("assets/prefablitematica/lang/zh_cn.json",language(true));
        return out;
    }
    private static void add(Map<MaterialGroup,LinkedHashSet<String>> tags, MaterialGroup group, String... names) { for (String name : names) tags.get(group).add("minecraft:" + name); }
    private static void classifyShape(Map<MaterialGroup,LinkedHashSet<String>> tags, String tag, String wooden, MaterialGroup stone, MaterialGroup copper, MaterialGroup sandstone, MaterialGroup wool, MaterialGroup concrete) throws IOException {
        Set<String> excluded = vanillaTag(wooden); excluded.addAll(vanillaTag(wool.id())); excluded.addAll(vanillaTag(concrete.id())); excluded.addAll(tags.get(sandstone));
        for (String id : vanillaTag(tag)) if (!excluded.contains(id)) tags.get(id.contains("copper") ? copper : stone).add(id);
    }
    private static Set<String> vanillaTag(String name) throws IOException {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        try (InputStream stream = PrefabLitematicaDataGenerator.class.getResourceAsStream("/data/minecraft/tags/item/" + name + ".json")) {
            if (stream == null) return result;
            JsonObject tag = JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement entry : tag.getAsJsonArray("values")) {
                String id = entry.isJsonPrimitive() ? entry.getAsString() : entry.getAsJsonObject().get("id").getAsString();
                if (id.startsWith("#minecraft:")) result.addAll(vanillaTag(id.substring(11))); else if (!id.startsWith("#")) result.add(id);
            }
        }
        return result;
    }
    private static JsonObject language(boolean zh) {
        JsonObject result = new JsonObject();
        String[][] translations = {
                {"item.prefablitematica.blueprint","Loaded Blueprint","已加载蓝图"},{"item.prefablitematica.blank_blueprint","Blank Blueprint","空白蓝图"},
                {"item.prefablitematica.loaded_named","Loaded Blueprint: %s","已加载蓝图：%s"},{"item.prefablitematica.creative_charge_battery","Creative Blueprint Charge Battery (Creative Only)","创造蓝图充能电池（创造专属）"},
                {"tooltip.prefablitematica.blank","Import a building at the Blueprint Workbench.","放入蓝图工程台，从列表载入建筑。"},
                {"tooltip.prefablitematica.loaded","Building data loaded. Charge at the workbench.","已载入建筑数据，可在工程台充能。"},
                {"tooltip.prefablitematica.size","Size: %s × %s × %s","尺寸：%s × %s × %s"},{"tooltip.prefablitematica.blocks","Blocks: %s","方块数量：%s"},
                {"tooltip.prefablitematica.use","Hold a charged blueprint and open its panel with the configured key (G by default). Show the projection, then click Execute placement.","手持充满的蓝图，按面板快捷键（默认 G）打开界面，点击显示投影，再点击执行放置。"},
                {"key.category.prefablitematica.projection","Blueprint projection","蓝图投影"},
                {"key.prefablitematica.open_panel","Open building panel","打开建筑面板"},
                {"projection.prefablitematica.title","Building placement","建筑释放"},
                {"projection.prefablitematica.info","%s × %s × %s · %s blocks","%s × %s × %s · %s 方块"},
                {"projection.prefablitematica.open_failed","Hold a fully charged, available blueprint to open the panel.","请手持已充满且可用的蓝图，再打开建筑面板。"},
                {"projection.prefablitematica.show_first","Click Show projection to preview the building.","点击「显示投影」预览建筑。"},
                {"projection.prefablitematica.show","Show projection","显示投影"},
                {"projection.prefablitematica.rotation_label","Rotation","旋转"},
                {"projection.prefablitematica.position","X %s  Y %s  Z %s  ·  %s°","X %s  Y %s  Z %s  ·  %s°"},
                {"projection.prefablitematica.keys","%s Open panel to adjust or execute","%s 打开面板，调整或执行放置"},
                {"projection.prefablitematica.loading","Loading projection…","正在加载投影…"},
                {"projection.prefablitematica.scanning","Checking the entire area…","正在检查整个范围…"},
                {"projection.prefablitematica.confirming","Rechecking before placement…","正在放置前复检…"},
                {"projection.prefablitematica.conflicts","%s red conflicts — clear or move them first","%s 处红色冲突，请清理或移动投影"},
                {"projection.prefablitematica.ready","No conflicts. Click Execute placement.","无冲突，可以点击执行放置。"},
                {"projection.prefablitematica.hold","Hold this blueprint to confirm placement.","请手持此蓝图再确认放置。"},
                {"projection.prefablitematica.unavailable","Blueprint needs charging or is already in use.","蓝图需要充能或正在被使用。"},
                {"projection.prefablitematica.failed","Could not load the projection. See the client log.","投影加载失败，请查看客户端日志。"},
                {"projection.prefablitematica.invalid_coordinates","Enter valid X/Y/Z coordinates.","请输入有效的 X/Y/Z 坐标。"},
                {"projection.prefablitematica.updating","Updating position…","正在更新位置…"},
                {"projection.prefablitematica.confirm","Execute placement","执行放置"},
                {"projection.prefablitematica.inspect","Inspect / clear","查看 / 清理"},
                {"projection.prefablitematica.cancel","Cancel preview","取消投影"},
                {"block.prefablitematica.workbench","Blueprint Workbench","蓝图工程台"},{"itemGroup.prefablitematica","PrefabLitematica","PrefabLitematica"},
                {"gui.prefablitematica.import","Load building","载入建筑"},{"gui.prefablitematica.charge","Charge","开始充能"},{"gui.prefablitematica.reading","Reading building…","读取建筑…"},
                {"gui.prefablitematica.cancel","Cancel","取消选择"},{"gui.prefablitematica.search","Search buildings","搜索建筑"},
                {"gui.prefablitematica.choose_source","Choose a building","选择建筑"},{"gui.prefablitematica.scanning","Reading schematic list…","正在读取原理图列表…"},
                {"gui.prefablitematica.no_sources","No matching buildings. Put schematics in Litematica's schematic folder.","没有找到建筑，请将原理图放入 Litematica 原理图目录。"},
                {"gui.prefablitematica.need_blank","Insert a blank blueprint before importing.","请先放入空白蓝图。"},
                {"gui.prefablitematica.portal_ignite","Nether portal blocks were cleared. Light the frame after placement.","地狱门已转为空气，放置后请手动点火。"},
                {"gui.prefablitematica.portal_slicing","Portal slicing is unsupported; slice it manually and light it after placement.","检测到切门结构；本软件不支持切门，请在放置后自行切门并点火。"},
                {"gui.prefablitematica.portal_ignite_short","Light portal manually\nafter placement.","地狱门已清空\n放置后手动点火"},
                {"gui.prefablitematica.portal_slicing_short","Slicing unsupported.\nSlice & light manually.","本软件不支持切门\n放置后自行切门点火"},
                {"gui.prefablitematica.compressing","Compressing building…","正在压缩建筑…"},{"gui.prefablitematica.uploading","Uploading building…","正在上传建筑…"},
                {"gui.prefablitematica.validating","Server validation…","服务端正在验证…"},{"gui.prefablitematica.imported","Building loaded","建筑已载入"},
                {"gui.prefablitematica.source.placement","Projection (enabled or disabled)","投影（已启用或未启用）"},
                {"gui.prefablitematica.source.loaded_schematic","Schematic in memory; no world projection required","已读入的原理图，无需放入世界"},
                {"gui.prefablitematica.source.file","Schematic file; no loading or projection required","原理图文件，无需预先加载或投影"},
                {"gui.prefablitematica.blueprint_slot","Blueprint","蓝图槽"},{"gui.prefablitematica.material_slot","Materials","材料输入"},{"gui.prefablitematica.battery_slot","Battery","创造电池"},
                {"gui.prefablitematica.return_slot","Returns","容器返还"},{"gui.prefablitematica.battery_input","Battery → input","电池放材料区"},
                {"gui.prefablitematica.material_input_help","Same materials merge up to 4096 per slot. Shulker box contents can charge the blueprint; unused contents stay in the returned box. Creative batteries also go in this input grid.","同种材料可在每槽累加至 4096。支持潜影盒内材料充能，未用完的材料保留在返还的盒内。创造电池也放在材料输入区。"},
                {"gui.prefablitematica.empty","Insert a blank blueprint. Choose a building from the list; no world projection is needed.","放入空白蓝图，从列表载入建筑，无需预先加载世界投影。"},{"gui.prefablitematica.remaining","Left","剩余"},
                {"message.prefablitematica.rotation","Blueprint rotation: %s°","蓝图旋转：%s°"},{"message.prefablitematica.validating","Checking the full building area…","正在验证整个建筑范围…"},
                {"message.prefablitematica.placing","Building placement started.","建筑开始分 Tick 放置。"},{"message.prefablitematica.complete","Building complete. Recharge to use again.","建筑已完成。再次使用需要重新充能。"}};
        for (String[] row : translations) result.addProperty(row[0], row[zh ? 2 : 1]);
        String[] names = {"木板类","原木/菌柄类","去皮原木/菌柄类","全树皮木/菌核类","去皮木/菌核类","木台阶类","木楼梯类","木门类","木活板门类","木栅栏类","木栅栏门类","木按钮类","木压力板类","砂岩完整方块类","砂岩台阶类","砂岩楼梯类","石材完整方块类","石台阶类","石楼梯类","石墙类","沙类","染色沙类","羊毛类","羊毛台阶类","羊毛楼梯类","地毯类","混凝土类","混凝土台阶类","混凝土楼梯类","混凝土粉末类","玻璃类（所有颜色）","玻璃板类（所有颜色）","陶瓦类","釉面陶瓦类","蜡烛类","床类","旗帜类","树叶类","树苗类","告示牌类","悬挂告示牌类","铜完整方块类","铜楼梯类","铜台阶类","铜格栅类"};
        int index = 0; for (MaterialGroup group : MaterialGroup.values()) {
            String label = zh ? names[index++] : group.name().replace('_',' ').toLowerCase(Locale.ROOT);
            result.addProperty("material.prefablitematica." + group.id(), label); result.addProperty("tag.item.prefablitematica.materials." + group.id(), label);
        }
        result.addProperty("material.prefablitematica.exact", zh ? "相同物品" : "Exact item"); result.addProperty("material.prefablitematica.water",zh ? "水：固定两个水桶" : "Water: two buckets total"); result.addProperty("material.prefablitematica.lava",zh ? "岩浆：每源方块一个桶" : "Lava: one bucket per source");
        String[][] conversions = {
                {"grass", "Also accepts dirt; no tool needed.", "也可提交泥土，无需工具。"},
                {"path", "Also accepts dirt-like blocks + shovel (1 durability each).", "也可提交泥土类方块 + 铲子，每次扣 1 耐久。"},
                {"farmland", "Also accepts dirt, grass or dirt path + hoe (1 durability each).", "也可提交泥土、草方块或土径 + 锄头，每次扣 1 耐久。"},
                {"till_dirt", "Also accepts coarse/rooted dirt + hoe (1 durability each).", "也可提交砂土或缠根泥土 + 锄头，每次扣 1 耐久。"},
                {"farmland_twice", "Coarse/rooted dirt + hoe needs 2 uses (2 durability each).", "砂土或缠根泥土 + 锄头需转换两次，共扣 2 耐久。"},
                {"carve", "Also accepts pumpkin + shears (1 durability each).", "也可提交南瓜 + 剪刀，每次扣 1 耐久。"},
                {"strip", "Also accepts matching unstripped logs/wood + axe (1 durability each).", "也可提交对应未去皮原木或木 + 斧头，每次扣 1 耐久。"},
                {"scrape", "Also accepts oxidized copper + axe (1 durability per oxidation stage).", "也可提交氧化铜材料 + 斧头，每去除一级氧化扣 1 耐久。"},
                {"wax_off", "Also accepts matching waxed copper + axe (1 durability each).", "也可提交对应涂蜡铜材料 + 斧头，每次除蜡扣 1 耐久。"},
                {"wax_scrape", "Waxed oxidized copper + axe: 1 durability for wax plus 1 per oxidation stage.", "涂蜡氧化铜材料 + 斧头：除蜡扣 1，每去除一级氧化再扣 1 耐久。"}
        };
        for (String[] row : conversions) result.addProperty("conversion.prefablitematica." + row[0], row[zh ? 2 : 1]);
        return result;
    }
}
