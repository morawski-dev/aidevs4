package com.morawski.dev.aidevs.tasks.task07electricity;

import com.morawski.dev.aidevs.config.ElectricityProperties;
import com.morawski.dev.aidevs.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumSet;
import java.util.HashMap;

/**
 * Turns a board PNG into a {@link Board} (9 × edge set).
 *
 * <p>The 3×3 wiring grid does <strong>not</strong> fill the image — there is a title above, three
 * power-plant icons on the left, and {@code PWR...} labels on the right, and the current board
 * ({@code 800×450}) and the target schematic ({@code 598×422}) are framed differently. So the grid
 * is <strong>auto-detected</strong> first ({@link #detectGrid}: the only long horizontal/vertical
 * black runs in the image are the grid's frame and inner lines), then each cell is cropped from that
 * box, upscaled, and sent to the vision model on its own — a clean single tile is an easy read,
 * whereas a blind {@code image/3} crop (title/icons/labels) is not. The model only answers "which
 * borders does the cable touch?" ({@link TileReading}); the rotation maths stays in {@link Rotations}.
 */
@Component
class BoardVision {

    private static final Logger log = LoggerFactory.getLogger(BoardVision.class);

    /** Luminance below this is treated as a black cable/line pixel (the background is light parchment). */
    private static final int DARK_THRESHOLD = 100;
    /** A dark run at least this fraction of the image's width/height counts as a grid line. */
    private static final double LINE_FRACTION = 0.18;

    private static final String TILE_PROMPT = """
            This image is ONE square tile cut from a 3x3 electrical wiring puzzle.
            A thick black cable runs across the tile. A side "connects" if the cable reaches the
            MIDDLE of that side and would continue into the neighbouring tile through it.
            Report, for each side of the square:
              - top    = a cable arm goes up and meets the middle of the upper side
              - right  = a cable arm goes right and meets the middle of the right side
              - bottom = a cable arm goes down and meets the middle of the lower side
              - left   = a cable arm goes left and meets the middle of the left side
            A bar that merely runs parallel along a side (without an arm reaching its middle) does
            NOT count. Judge THIS tile only. If the tile has no cable, all four are false.
            """;

    private final LlmService llm;
    private final ElectricityProperties props;

    BoardVision(LlmService llm, ElectricityProperties props) {
        this.llm = llm;
        this.props = props;
    }

    /** Read every tile of {@code png} and return the perceived board; logs an ASCII rendering. */
    Board describe(byte[] png) {
        var image = read(png);
        var grid = detectGrid(image);
        log.info("Grid detected at x[{}..{}] y[{}..{}] in {}x{} image",
                grid.x0, grid.x1, grid.y0, grid.y1, image.getWidth(), image.getHeight());

        var tiles = new HashMap<Cell, EnumSet<Edge>>();
        for (var cell : Cell.grid()) {
            tiles.put(cell, readTile(toTilePng(image, grid, cell)));
        }
        var board = new Board(tiles);
        log.info("Board perceived:\n{}", board.toAscii());
        return board;
    }

    /** Vision read of a single tile, taking a per-edge majority over {@code votesPerTile} reads. */
    private EnumSet<Edge> readTile(byte[] tilePng) {
        int votes = Math.max(1, props.votesPerTile());
        int top = 0, right = 0, bottom = 0, left = 0;
        for (int v = 0; v < votes; v++) {
            var r = llm.extractFromImage(TILE_PROMPT, tilePng, MimeTypeUtils.IMAGE_PNG,
                    props.visionModel(), TileReading.class);
            if (r.top()) top++;
            if (r.right()) right++;
            if (r.bottom()) bottom++;
            if (r.left()) left++;
        }
        int majority = votes / 2 + 1;
        var edges = EnumSet.noneOf(Edge.class);
        if (top >= majority) edges.add(Edge.N);
        if (right >= majority) edges.add(Edge.E);
        if (bottom >= majority) edges.add(Edge.S);
        if (left >= majority) edges.add(Edge.W);
        return edges;
    }

    // --- grid detection ------------------------------------------------------

    private record GridBox(int x0, int y0, int x1, int y1) {
        int width() {
            return x1 - x0;
        }

        int height() {
            return y1 - y0;
        }
    }

    /**
     * Find the 3×3 grid's bounding box. The grid's frame and inner lines are the only long solid
     * black runs in the image (title text, plant icons and labels produce only short runs), so the
     * box spans from the first to the last row/column that contains a run ≥ {@link #LINE_FRACTION}
     * of the image size. Falls back to the whole image if nothing qualifies.
     */
    private GridBox detectGrid(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        boolean[][] dark = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int lum = ((rgb >> 16 & 0xff) + (rgb >> 8 & 0xff) + (rgb & 0xff)) / 3;
                dark[y][x] = lum < DARK_THRESHOLD;
            }
        }

        int hThreshold = (int) (LINE_FRACTION * w);
        int vThreshold = (int) (LINE_FRACTION * h);

        int y0 = -1, y1 = -1;
        for (int y = 0; y < h; y++) {
            int run = 0, best = 0;
            for (int x = 0; x < w; x++) {
                run = dark[y][x] ? run + 1 : 0;
                best = Math.max(best, run);
            }
            if (best >= hThreshold) {
                if (y0 < 0) {
                    y0 = y;
                }
                y1 = y;
            }
        }

        int x0 = -1, x1 = -1;
        for (int x = 0; x < w; x++) {
            int run = 0, best = 0;
            for (int y = 0; y < h; y++) {
                run = dark[y][x] ? run + 1 : 0;
                best = Math.max(best, run);
            }
            if (best >= vThreshold) {
                if (x0 < 0) {
                    x0 = x;
                }
                x1 = x;
            }
        }

        if (x0 < 0 || y0 < 0) {
            log.warn("Could not detect the grid frame — falling back to the whole image.");
            return new GridBox(0, 0, w, h);
        }
        return new GridBox(x0, y0, x1, y1);
    }

    // --- image handling ------------------------------------------------------

    private BufferedImage read(byte[] png) {
        try {
            var image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) {
                throw new IllegalStateException("Could not decode board PNG (" + png.length + " bytes)");
            }
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read board PNG", e);
        }
    }

    /** Crop one cell from the detected grid box (equal thirds) and re-encode it as an upscaled PNG. */
    private byte[] toTilePng(BufferedImage image, GridBox grid, Cell cell) {
        int tileW = grid.width() / 3;
        int tileH = grid.height() / 3;
        int x = grid.x0() + (cell.col() - 1) * tileW;
        int y = grid.y0() + (cell.row() - 1) * tileH;

        int scale = Math.max(1, props.tileUpscale());
        int outW = tileW * scale;
        int outH = tileH * scale;
        var out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        var g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(image, 0, 0, outW, outH, x, y, x + tileW, y + tileH, null);
        g.dispose();

        try {
            var baos = new ByteArrayOutputStream();
            ImageIO.write(out, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode tile " + cell.label(), e);
        }
    }
}
