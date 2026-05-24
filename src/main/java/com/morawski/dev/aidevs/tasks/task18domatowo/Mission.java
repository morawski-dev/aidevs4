package com.morawski.dev.aidevs.tasks.task18domatowo;

import java.util.List;

/**
 * One transporter sortie: drive a transporter (carrying a scout) to {@code drop} (a road cell next to
 * a cluster of tallest blocks), dismount, then have the scout visit and inspect each cell in
 * {@code cells}, ordered as a cheap nearest-neighbour walk from the drop.
 *
 * @param drop  road cell label ({@code A1..K11}) to move the transporter to before dismounting
 * @param cells block-cell labels to inspect, in walk order
 */
record Mission(String drop, List<String> cells) {
}
