package dev.lagdoctor;

/** Immutable aggregate; no player identifiers, coordinates, or world names. */
public record Window(long endedAt, double seconds, int ticks, double meanMs,
                     double p95Ms, double maxMs, int slowTicks, long gcMillis,
                     double heapRatio, int entities, int chunks, int chunkLoads,
                     int newChunks, int players, boolean demo) {
    public double tps() { return Math.min(20, ticks / seconds); }
    public double slowPercent() { return 100.0 * slowTicks / ticks; }
}
