package dev.lagdoctor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/** Within-report contrast. Explicit evidence grades, not probabilities of causation. */
final class Evidence {
    record Finding(String name, String grade, double contrast, String detail, String action) { }
    private record Signal(String name, double threshold, String unit, ToDoubleFunction<Window> value, String action) { }
    private static final List<Signal> SIGNALS = List.of(
            new Signal("GC activity", 5, "% elapsed", w -> 100 * w.gcMillis() / (w.seconds() * 1000),
                    "Profile allocations with spark and inspect post-GC heap trends. Collection counters are not exact pause time."),
            new Signal("New chunk activity", 2, "/sec", w -> w.newChunks() / w.seconds(),
                    "Repeat with exploration stopped. If generation stacks are hot in spark, plan backed-up, off-peak pregeneration."),
            new Signal("Chunk loading", 10, "/sec", w -> w.chunkLoads() / w.seconds(),
                    "Repeat without teleports or exploration; inspect chunk-loading stacks before changing view distance."),
            new Signal("Entity population", 1000, "entities", Window::entities,
                    "Run /ld scan to locate concentrations, then verify entity-ticking cost with spark. Pause one known farm and compare; do not mass-delete entities.")
    );
    static List<Finding> rank(List<Window> windows) {
        var slow = windows.stream().filter(Diagnosis::slow).toList();
        var healthy = windows.stream().filter(w -> !Diagnosis.slow(w)).toList();
        if (slow.isEmpty()) return List.of();
        List<Finding> findings = new ArrayList<>();
        for (Signal s : SIGNALS) {
            long hits = slow.stream().filter(w -> s.value.applyAsDouble(w) >= s.threshold).count();
            if (hits == 0) continue;
            double bad = slow.stream().mapToDouble(s.value).average().orElse(0);
            double good = healthy.stream().mapToDouble(s.value).average().orElse(0);
            boolean contrast = healthy.size() >= 2 && slow.size() >= 2;
            // A large constant background workload must not be promoted as discriminating evidence.
            double ratio = bad / Math.max(good, s.threshold * .1);
            String grade = !contrast ? "LIMITED: no adequate healthy/slow contrast"
                    : ratio >= 2 && hits * 2 >= slow.size() ? "STRONGER ASSOCIATION: >=2x healthy average"
                    : "WEAK CONTRAST: also present in healthy windows or inconsistent";
            String detail = String.format(Locale.ROOT,
                    "%d/%d slow windows met %.1f %s; slow avg %.2f %s; healthy avg %s (%d windows).",
                    hits, slow.size(), s.threshold, s.unit, bad, s.unit,
                    healthy.isEmpty() ? "unavailable" : String.format(Locale.ROOT, "%.2f %s", good, s.unit), healthy.size());
            findings.add(new Finding(s.name, grade, contrast ? ratio : 0, detail, s.action));
        }
        findings.sort(Comparator.comparingDouble(Finding::contrast).reversed());
        return List.copyOf(findings);
    }
    static List<String> lines(List<Window> windows) {
        var lines = new ArrayList<String>();
        if (windows.stream().anyMatch(Window::demo)) {
            lines.add("ASSOCIATION ANALYSIS SUPPRESSED: intentional demo delays confound cause ranking.");
            return lines;
        }
        for (var f : rank(windows)) {
            lines.add(f.name() + " [" + f.grade() + "]: " + f.detail());
            lines.add("Try: " + f.action());
        }
        if (!lines.isEmpty()) lines.add("Evidence grades describe within-report association, not proof, probabilities, or independent causal validation.");
        return lines;
    }
}
