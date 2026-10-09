package dev.lagdoctor;

import java.util.Arrays;

/** Fixed-size storage and O(1) work per tick; sorting happens once per window. */
final class TickWindow {
    private final double[] durations = new double[200];
    private int count;
    boolean add(double ms) {
        durations[count++] = Math.max(0, ms);
        return count == durations.length;
    }
    Window finish(long now, double seconds, long gc, double heap, int entities,
                  int chunks, int loads, int generated, int players, boolean demo) {
        if (count == 0) throw new IllegalStateException("No ticks");
        double[] sorted = Arrays.copyOf(durations, count);
        Arrays.sort(sorted);
        double sum = 0;
        int slow = 0;
        for (double value : sorted) { sum += value; if (value > 50) slow++; }
        Window result = new Window(now, Math.max(.001, seconds), count, sum / count,
                sorted[(int) Math.ceil(count * .95) - 1], sorted[count - 1], slow,
                Math.max(0, gc), heap, entities, chunks, loads, generated, players, demo);
        count = 0;
        return result;
    }
}
