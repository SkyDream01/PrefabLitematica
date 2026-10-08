// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 Tensin

package dev.tensin.prefablitematica.datagen;

import com.google.common.hash.Hashing;
import net.minecraft.data.CachedOutput;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;

/** Original 16px pixel art. Kept in datagen so regenerating resources preserves the artwork. */
final class BlueprintTextures {
    private static final int WOOD_DARK = 0xFF59432A, WOOD_SHADOW = 0xFF806039;
    private static final int WOOD = 0xFF9F844D, WOOD_LIGHT = 0xFFB8945B, WOOD_HIGHLIGHT = 0xFFC5A66B;
    private static final int IRON_DARK = 0xFF555653, IRON = 0xFFAAAFA7, IRON_LIGHT = 0xFFE1E3DC;
    private static final int PAPER = 0xFFE8D7AA, PAPER_LIGHT = 0xFFF7EAC8, PAPER_SHADOW = 0xFFBDA374;
    private static final int BLUE_DARK = 0xFF29466E, BLUE = 0xFF3C6694, BLUE_LIGHT = 0xFF5484AF, INK = 0xFFD1E4DD;

    private BlueprintTextures() {}

    static void generate(CachedOutput cache, Path root) throws IOException {
        write(cache, root, "item/blank_blueprint", paper(false));
        write(cache, root, "item/blueprint", paper(true));
        write(cache, root, "item/creative_charge_battery", battery());
        write(cache, root, "block/workbench_top", workbenchTop());
        write(cache, root, "block/workbench_side", workbenchSide(false));
        write(cache, root, "block/workbench_front", workbenchSide(true));
    }

    private static BufferedImage paper(boolean loaded) {
        // Rolled parchment ends and a folded lower corner give both states the same silhouette.
        var colors = Map.of(
                '#', PAPER_SHADOW, 's', 0xFF947B52, 'e', PAPER, 'h', PAPER_LIGHT,
                'p', loaded ? BLUE : PAPER, 'l', loaded ? BLUE_LIGHT : PAPER_LIGHT,
                'd', loaded ? BLUE_DARK : 0xFFD6C394);
        var image = sprite(colors,
                "................",
                "...#######......",
                "..#hhhhhhe##....",
                ".#heellllleh#...",
                ".#hepppppppeh#..",
                ".#hepppppppeh#..",
                "..#epppppppeh#..",
                "..#epppppppeh#..",
                "..#epppppppeh#..",
                "..#epppppppeh#..",
                "..#epppppppeh#..",
                "..#edddddddeh#..",
                "..#eeeee#hhhh#..",
                "...#####hess#...",
                "........####....",
                "................");
        if (loaded) {
            // A floor plan, with a doorway and an offset room, readable at inventory size.
            rect(image, 5, 5, 10, 5, INK); rect(image, 5, 5, 5, 9, INK);
            rect(image, 10, 5, 10, 9, INK); rect(image, 5, 9, 6, 9, INK);
            rect(image, 9, 9, 10, 9, INK); rect(image, 8, 5, 8, 7, INK);
            pixel(image, 9, 7, INK); pixel(image, 6, 7, BLUE_LIGHT);
        } else {
            pixel(image, 5, 5, PAPER_LIGHT); pixel(image, 9, 6, PAPER_LIGHT);
            pixel(image, 6, 9, 0xFFD6C394); pixel(image, 7, 9, PAPER_LIGHT);
        }
        return image;
    }

    private static BufferedImage battery() {
        // Iron terminals, brass bands and an amethyst core echo vanilla mechanical items.
        return sprite(Map.ofEntries(
                Map.entry('#', IRON_DARK), Map.entry('i', IRON), Map.entry('h', IRON_LIGHT),
                Map.entry('b', 0xFF73552F), Map.entry('g', 0xFFB68A42), Map.entry('y', 0xFFE3C477),
                Map.entry('s', 0xFF3E3054), Map.entry('v', 0xFF674795), Map.entry('p', 0xFF9673C8),
                Map.entry('l', 0xFFC7A0E5), Map.entry('w', 0xFFEAD5F6)),
                "................",
                "......####......",
                "......#hh#......",
                "....##iii###....",
                "...#hiiiiiii#...",
                "...#yyyygggb#...",
                "...#bslppvsb#...",
                "...#bslwpvsb#...",
                "...#bspwlvvb#...",
                "...#bsvlpvvb#...",
                "...#bsvpvvsb#...",
                "...#yyyygggb#...",
                "...#hiiiiiii#...",
                "....########....",
                "................",
                "................");
    }

    private static BufferedImage wood() {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        int[] palette = {WOOD_DARK, WOOD_SHADOW, WOOD, WOOD_LIGHT, WOOD_HIGHLIGHT};
        int[] rows = {3, 2, 2, 1, 4, 3, 2, 1, 3, 2, 3, 1, 4, 3, 2, 1};
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int grain = (x + (y / 4) * 5) % 11;
            int shade = rows[y];
            if (y % 4 != 3 && grain < 3) shade = Math.max(1, shade - 1);
            if (y % 4 != 3 && grain > 8) shade = Math.min(4, shade + 1);
            pixel(image, x, y, palette[shade]);
        }
        // Staggered plank joints, rather than random pixel noise.
        rect(image, 5, 0, 5, 2, WOOD_SHADOW); rect(image, 12, 4, 12, 6, WOOD_SHADOW);
        rect(image, 2, 8, 2, 10, WOOD_SHADOW); rect(image, 9, 12, 9, 14, WOOD_SHADOW);
        return image;
    }

    private static BufferedImage workbenchTop() {
        var image = wood();
        rect(image, 0, 0, 15, 0, WOOD_DARK); rect(image, 0, 15, 15, 15, WOOD_DARK);
        rect(image, 0, 0, 0, 15, WOOD_DARK); rect(image, 15, 0, 15, 15, WOOD_DARK);
        rect(image, 3, 2, 13, 12, PAPER_SHADOW); rect(image, 4, 3, 12, 11, BLUE);
        rect(image, 4, 3, 12, 3, BLUE_LIGHT); rect(image, 4, 11, 12, 11, BLUE_DARK);
        rect(image, 3, 2, 3, 11, PAPER); rect(image, 4, 2, 12, 2, PAPER_LIGHT);
        rect(image, 5, 12, 13, 12, PAPER); rect(image, 13, 3, 13, 11, PAPER_LIGHT);
        rect(image, 6, 5, 10, 5, INK); rect(image, 6, 5, 6, 9, INK);
        rect(image, 10, 5, 10, 9, INK); rect(image, 6, 9, 7, 9, INK);
        pixel(image, 10, 9, INK); rect(image, 8, 5, 8, 7, INK); pixel(image, 9, 7, INK);
        // Brass measuring rule down the left edge of the desk.
        rect(image, 1, 4, 2, 12, 0xFFD3B36A);
        for (int y = 5; y < 12; y += 2) pixel(image, 1, y, 0xFF806039);
        for (int x : new int[]{0, 13}) for (int y : new int[]{0, 13}) {
            rect(image, x, y, x + 2, y + 2, IRON_DARK);
            rect(image, x, y, x + 1, y + 1, IRON); pixel(image, x, y, IRON_LIGHT);
        }
        return image;
    }

    private static BufferedImage workbenchSide(boolean front) {
        var image = wood();
        // Raised desk lip, sturdy oak posts and iron corner fasteners.
        rect(image, 0, 0, 15, 2, WOOD_DARK); rect(image, 0, 0, 15, 0, WOOD_LIGHT);
        rect(image, 0, 1, 15, 1, WOOD_SHADOW); rect(image, 0, 14, 15, 15, WOOD_DARK);
        for (int x : new int[]{0, 13}) {
            rect(image, x, 3, x + 2, 13, WOOD_SHADOW); rect(image, x, 3, x, 13, WOOD_LIGHT);
            for (int y : new int[]{3, 12}) {
                rect(image, x, y, x + 2, y + 1, IRON_DARK);
                pixel(image, x, y, IRON_LIGHT); pixel(image, x + 1, y, IRON);
            }
        }
        if (front) {
            // A shallow plan drawer and a small pinned blueprint distinguish this from a crafting table.
            rect(image, 4, 4, 11, 8, WOOD_DARK); rect(image, 4, 4, 11, 4, WOOD_LIGHT);
            rect(image, 4, 5, 11, 7, WOOD); rect(image, 7, 5, 8, 5, IRON_LIGHT);
            rect(image, 7, 6, 8, 6, IRON_DARK);
            rect(image, 5, 9, 10, 12, PAPER_SHADOW); rect(image, 6, 9, 10, 11, BLUE);
            rect(image, 7, 10, 9, 10, INK); pixel(image, 7, 11, INK);
        } else {
            rect(image, 4, 5, 11, 5, WOOD_DARK); rect(image, 4, 6, 11, 6, WOOD_LIGHT);
            // Recessed measuring tools, using the same metal and brass as the top.
            rect(image, 5, 7, 5, 11, IRON_DARK); rect(image, 5, 7, 5, 9, IRON);
            rect(image, 8, 7, 9, 11, 0xFF73552F); rect(image, 8, 7, 8, 10, 0xFFD3B36A);
            pixel(image, 8, 8, WOOD_SHADOW); pixel(image, 8, 10, WOOD_SHADOW);
            rect(image, 4, 12, 11, 12, WOOD_SHADOW);
        }
        return image;
    }

    private static BufferedImage sprite(Map<Character, Integer> colors, String... rows) {
        if (rows.length != 16) throw new IllegalArgumentException("Texture must have 16 rows");
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            if (rows[y].length() != 16) throw new IllegalArgumentException("Texture row " + y + " must have 16 pixels");
            for (int x = 0; x < 16; x++) {
                char key = rows[y].charAt(x);
                if (key != '.') pixel(image, x, y, java.util.Objects.requireNonNull(colors.get(key), "Unknown palette entry"));
            }
        }
        return image;
    }
    private static void pixel(BufferedImage image, int x, int y, int color) { image.setRGB(x, y, color); }
    private static void rect(BufferedImage image, int x0, int y0, int x1, int y1, int color) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) pixel(image, x, y, color);
    }
    private static void write(CachedOutput cache, Path root, String name, BufferedImage image) throws IOException {
        Path path = root.resolve("assets/prefablitematica/textures/" + name + ".png");
        Files.createDirectories(path.getParent());
        var output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output);
        byte[] bytes = output.toByteArray(); cache.writeIfNeeded(path, bytes, Hashing.sha1().hashBytes(bytes));
    }
}
