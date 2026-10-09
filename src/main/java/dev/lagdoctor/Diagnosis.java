package dev.lagdoctor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Diagnosis {
    private Diagnosis() { }
    public static boolean slow(Window w) { return w.meanMs() > 50 || w.slowPercent() >= 10; }
    public static List<String> explain(List<Window> windows) {
        List<String> out = new ArrayList<>();
        long slowCount = windows.stream().filter(Diagnosis::slow).count();
        if (slowCount == 0) {
            out.add("HEALTHY WINDOW: no sustained tick slowdown detected; isolated spikes may still occur.");
        } else {
            out.add("SLOWDOWN: " + slowCount + "/" + windows.size() + " windows met mean >50 ms or at least 10% ticks >50 ms.");
        }
        if (windows.stream().anyMatch(Window::demo))
            out.add("DEMO DATA: intentional local tick delays occurred. Do not use this report to diagnose real workload.");
        var slow = windows.stream().filter(Diagnosis::slow).toList();
        boolean clue = !Evidence.rank(windows).isEmpty();
        out.addAll(Evidence.lines(windows));
        if (windows.stream().anyMatch(w -> w.heapRatio() >= .85))
            out.add("HEAP WATCH: used heap reached >=85% of max at a sample. A single reading is not a memory leak or proof of insufficient RAM; examine the post-GC trend.");
        if (!slow.isEmpty() && (!clue || windows.stream().anyMatch(Window::demo)))
            out.add("CAUSE UNRESOLVED: measured signals do not explain the slowdown. Plugin tasks, redstone, saves, host contention, and other work need profiling.");
        if (!slow.isEmpty()) out.add("NEXT: run /spark profiler start --timeout 60 during the issue. Inspect the busiest main-thread stacks; Lag Doctor does not access spark internals or attribute plugin blame.");
        out.add("Change one setting at a time; compare equal windows with similar player counts and workload. Correlation is not causation; client/network lag is outside this report.");
        return out;
    }
    public static String metrics(List<Window> ws) {
        if (ws.isEmpty()) return "No completed windows yet.";
        int ticks = ws.stream().mapToInt(Window::ticks).sum();
        double seconds = ws.stream().mapToDouble(Window::seconds).sum();
        return String.format(Locale.ROOT,
                "%.1fs | %d ticks | effective TPS %.2f | mean %.2f ms | worst window p95 %.2f ms | max %.2f ms | >50ms %.1f%%",
                seconds, ticks, Math.min(20, ticks / seconds), mean(ws),
                ws.stream().mapToDouble(Window::p95Ms).max().orElse(0),
                ws.stream().mapToDouble(Window::maxMs).max().orElse(0),
                100.0 * ws.stream().mapToInt(Window::slowTicks).sum() / ticks);
    }
    public static double mean(List<Window> ws) {
        if (ws.isEmpty()) return 0;
        return ws.stream().mapToDouble(w -> w.meanMs() * w.ticks()).sum() /
                ws.stream().mapToInt(Window::ticks).sum();
    }
    public static List<String> compare(List<Window> before, List<Window> after) {
        if (before.isEmpty() || after.isEmpty()) return List.of("Not enough data for comparison.");
        if (after.getFirst().endedAt() <= before.getLast().endedAt())
            return List.of("Wait for a full report of new windows after saving the baseline; overlapping data is not a comparison.");
        var out = new ArrayList<String>();
        out.add("BEFORE: " + metrics(before));
        out.add("AFTER:  " + metrics(after));
        double change = mean(after) - mean(before);
        out.add(String.format(Locale.ROOT, "Mean tick change: %+.2f ms (%s). This does not establish what caused the change.", change,
                change < 0 ? "lower" : change > 0 ? "higher" : "unchanged"));
        if (before.size() != after.size()) out.add("CAUTION: report lengths differ.");
        double bp = before.stream().mapToInt(Window::players).average().orElse(0);
        double ap = after.stream().mapToInt(Window::players).average().orElse(0);
        if (Math.abs(bp - ap) >= 1) out.add("CAUTION: average sampled player counts differ; workload is not controlled.");
        workload(out, "entities", before, after, Window::entities, 100);
        workload(out, "loaded chunks", before, after, Window::chunks, 50);
        workload(out, "chunk loads/sec", before, after, w -> w.chunkLoads() / w.seconds(), 2);
        workload(out, "new chunks/sec", before, after, w -> w.newChunks() / w.seconds(), 1);
        double slowBefore = 100.0 * before.stream().mapToInt(Window::slowTicks).sum() / before.stream().mapToInt(Window::ticks).sum();
        double slowAfter = 100.0 * after.stream().mapToInt(Window::slowTicks).sum() / after.stream().mapToInt(Window::ticks).sum();
        out.add(String.format(Locale.ROOT, "Slow-tick change: %+.1f percentage points (%.1f%% -> %.1f%%).", slowAfter - slowBefore, slowBefore, slowAfter));
        double beforeMin = before.stream().mapToDouble(Window::meanMs).min().orElse(0);
        double beforeMax = before.stream().mapToDouble(Window::meanMs).max().orElse(0);
        double afterMin = after.stream().mapToDouble(Window::meanMs).min().orElse(0);
        double afterMax = after.stream().mapToDouble(Window::meanMs).max().orElse(0);
        out.add(afterMax < beforeMin ? "CONSISTENT DIRECTION: every after-window mean was lower than every baseline-window mean. Repeat with matching workload."
                : afterMin > beforeMax ? "CONSISTENT DIRECTION: every after-window mean was higher than every baseline-window mean. Investigate before keeping the change."
                : "MIXED WINDOWS: before/after mean ranges overlap; one report does not show a consistent improvement.");
        if (before.stream().anyMatch(Window::demo) || after.stream().anyMatch(Window::demo)) out.add("DEMO comparison: includes intentional tick delays.");
        out.add("Keep exploration, farms, players and other plugins comparable. Repeat before claiming an improvement.");
        return out;
    }

    private static void workload(List<String> out, String label, List<Window> before, List<Window> after,
                                 java.util.function.ToDoubleFunction<Window> metric, double floor) {
        double b = before.stream().mapToDouble(metric).average().orElse(0);
        double a = after.stream().mapToDouble(metric).average().orElse(0);
        if (Math.abs(a - b) >= Math.max(floor, Math.abs(b) * .25))
            out.add(String.format(Locale.ROOT, "CAUTION: sampled %s changed %.1f -> %.1f; workload differs and may explain the performance change.", label, b, a));
    }
}
