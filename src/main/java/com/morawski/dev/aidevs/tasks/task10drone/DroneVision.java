package com.morawski.dev.aidevs.tasks.task10drone;

import com.morawski.dev.aidevs.config.DroneProperties;
import com.morawski.dev.aidevs.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates the dam sector on the terrain map. Both signals on this map are deliberate and
 * unambiguous, so perception is done in <strong>code</strong>, not by a (flaky) vision model:
 * <ul>
 *   <li>the grid is drawn as solid <strong>red lines</strong> — detecting them gives the exact
 *       columns × rows and cell boundaries (a vision model miscounts rows, e.g. reads 3×3 for a
 *       3×4 grid, which lands the bomb one row off);</li>
 *   <li>the dam's water colour is <strong>deliberately over-saturated to turquoise</strong> — the
 *       cell with the most turquoise pixels is the dam.</li>
 * </ul>
 *
 * <p>This is the task's gate (a wrong sector bombs the plant or floods the valley), so deterministic
 * detection is far safer than asking a model to count a grid. A vision-model read is kept only as a
 * fallback for when the grid or the water can't be detected.
 */
@Component
class DroneVision {

    private static final Logger log = LoggerFactory.getLogger(DroneVision.class);

    /** A red grid-line pixel: strong red, weak green/blue. */
    private static boolean isRed(int rgb) {
        int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
        return r > 150 && g < 90 && b < 90;
    }

    /** A boosted-turquoise water pixel: green and blue both dominate red. */
    private static boolean isTurquoise(int rgb) {
        int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
        return g > 130 && b > 130 && r < g - 30 && r < b - 30;
    }

    /** A line whose red pixels reach this fraction of the cross dimension counts as a grid line. */
    private static final double LINE_FRACTION = 0.25;
    /** Merge grid-line pixels closer than this (lines are a few px thick). */
    private static final int LINE_GAP = 8;

    private static final String FALLBACK_PROMPT = """
            This is an aerial overview MAP of terrain, divided by a red grid into rectangular SECTORS.
            Count the COLUMNS (left-to-right) and ROWS (top-to-bottom). Find the DAM sector: the map
            deliberately over-saturates the water colour (bright turquoise) at the dam. Report the dam
            sector's COLUMN (from the left, starting at 1) and ROW (from the top, starting at 1) and
            the grid size. Sector (1,1) is the upper-left corner.
            """;

    private final LlmService llm;
    private final DroneProperties props;

    DroneVision(LlmService llm, DroneProperties props) {
        this.llm = llm;
        this.props = props;
    }

    /**
     * Locate the dam sector. The map endpoint rotates between an annotated frame (red grid + boosted
     * turquoise water — parseable) and a raw frame (no grid), so this re-downloads via {@code
     * mapDownloader} until it gets an annotated frame (or {@code mapRetries} is hit), then detects the
     * grid and the dam deterministically. Falls back to the vision model on the last frame if no
     * annotated map arrives.
     */
    DamLocation locateDam(java.util.function.Supplier<byte[]> mapDownloader) {
        int retries = Math.max(1, props.mapRetries());
        byte[] lastPng = null;
        for (int attempt = 1; attempt <= retries; attempt++) {
            var png = mapDownloader.get();
            lastPng = png;
            var image = read(png);
            int[] xs = lines(image, true);
            int[] ys = lines(image, false);
            if (xs.length < 2 || ys.length < 2) {
                log.info("Map attempt {}/{}: raw frame ({}x{}, {} v-lines / {} h-lines) — waiting for the annotated map.",
                        attempt, retries, image.getWidth(), image.getHeight(), xs.length, ys.length);
                pause();
                continue;
            }

            int cols = xs.length - 1;
            int rows = ys.length - 1;
            int bestCol = 0, bestRow = 0, bestCount = -1;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int count = turquoiseInCell(image, xs[c], xs[c + 1], ys[r], ys[r + 1]);
                    if (count > bestCount) {
                        bestCount = count;
                        bestCol = c + 1;
                        bestRow = r + 1;
                    }
                }
            }

            if (bestCount <= 0) {
                log.warn("Annotated map ({}x{}) but no turquoise water in any cell; retrying.", cols, rows);
                pause();
                continue;
            }

            log.info("Dam sector → column {}, row {} (grid {}x{}, {} turquoise px)",
                    bestCol, bestRow, cols, rows, bestCount);
            return new DamLocation(cols, rows, bestCol, bestRow,
                    "deterministic: most over-saturated turquoise water cell");
        }

        log.warn("No annotated map after {} attempts; falling back to the vision model on the last frame.", retries);
        return llmFallback(lastPng);
    }

    private void pause() {
        long ms = props.mapRetryPauseMs();
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the annotated map", e);
        }
    }

    private DamLocation llmFallback(byte[] mapPng) {
        var read = llm.extractFromImage(FALLBACK_PROMPT, mapPng, MimeTypeUtils.IMAGE_PNG,
                props.visionModel(), DamLocation.class);
        log.info("Vision fallback: grid {}x{}, dam at ({},{}) — {}",
                read.cols(), read.rows(), read.damCol(), read.damRow(), read.reasoning());
        if (!read.withinGrid()) {
            log.warn("Vision fallback returned an out-of-grid sector — the read is likely unreliable.");
        }
        return read;
    }

    // --- deterministic detection ---------------------------------------------

    /** Count turquoise pixels in the cell rectangle {@code [x0,x1) × [y0,y1)} (sampled every 2 px). */
    private int turquoiseInCell(BufferedImage image, int x0, int x1, int y0, int y1) {
        int count = 0;
        for (int y = y0; y < y1; y += 2) {
            for (int x = x0; x < x1; x += 2) {
                if (isTurquoise(image.getRGB(x, y))) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Find the positions of the red grid lines along one axis. For each candidate row/column, the
     * total number of red pixels across the cross dimension must reach {@link #LINE_FRACTION} of it
     * (counting total — not the longest contiguous run — so a line interrupted where it overlaps
     * bright/dark features is still detected); adjacent hits (a line is a few px thick) are merged.
     *
     * @param vertical {@code true} to find vertical lines (x positions), {@code false} for horizontal (y)
     */
    private int[] lines(BufferedImage image, boolean vertical) {
        int w = image.getWidth();
        int h = image.getHeight();
        int along = vertical ? w : h;     // iterate candidate positions along this axis
        int across = vertical ? h : w;    // count red across this axis
        int threshold = (int) (LINE_FRACTION * across);

        var hits = new ArrayList<Integer>();
        for (int i = 0; i < along; i++) {
            int red = 0;
            for (int j = 0; j < across; j++) {
                int rgb = vertical ? image.getRGB(i, j) : image.getRGB(j, i);
                if (isRed(rgb)) {
                    red++;
                }
            }
            if (red >= threshold) {
                hits.add(i);
            }
        }
        return collapse(hits);
    }

    /** Collapse consecutive line indices (within {@link #LINE_GAP}) into one midpoint each. */
    private int[] collapse(List<Integer> hits) {
        var out = new ArrayList<Integer>();
        int start = -1, prev = -100;
        for (int i : hits) {
            if (i - prev > LINE_GAP) {
                if (start >= 0) {
                    out.add((start + prev) / 2);
                }
                start = i;
            }
            prev = i;
        }
        if (start >= 0) {
            out.add((start + prev) / 2);
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private BufferedImage read(byte[] png) {
        try {
            var image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) {
                throw new IllegalStateException("Could not decode map PNG (" + png.length + " bytes)");
            }
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read map PNG", e);
        }
    }
}
