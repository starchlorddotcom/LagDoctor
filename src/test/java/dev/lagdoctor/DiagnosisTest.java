package dev.lagdoctor;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosisTest {
    Window window(long end, double mean, int slow, long gc, int loads, int fresh, int entities, boolean demo) {
        return new Window(end, 10, 200, mean, 70, 150, slow, gc, .5, entities, 100, loads, fresh, 0, demo);
    }
    String explain(Window w) { return String.join("\n", Diagnosis.explain(List.of(w))); }
    @Test void healthyWorkloadDoesNotAccuseEntitiesOrGeneration() {
        String result = explain(window(1, 5, 0, 900, 500, 100, 5000, false));
        assertTrue(result.contains("HEALTHY WINDOW"));
        assertFalse(result.contains("Entity population"));
        assertFalse(result.contains("New chunk activity"));
        assertFalse(result.contains("GC activity"));
    }
    @Test void slowWindowsShowEvidenceAndUncertainty() {
        String result = explain(window(1, 60, 100, 900, 500, 100, 5000, false));
        assertTrue(result.contains("GC activity [LIMITED:"));
        assertTrue(result.contains("New chunk activity [LIMITED:"));
        assertTrue(result.contains("Entity population [LIMITED:"));
        assertTrue(result.contains("Correlation is not causation"));
    }
    @Test void unresolvedSlowdownDoesNotInventCause() {
        String result = explain(window(1, 60, 100, 0, 0, 0, 10, false));
        assertTrue(result.contains("CAUSE UNRESOLVED"));
        assertTrue(result.contains("/spark profiler"));
    }
    @Test void loadingAndGenerationAreDistinguished() {
        String result = explain(window(1, 60, 100, 0, 100, 0, 10, false));
        assertTrue(result.contains("Chunk loading"));
        assertFalse(result.contains("New chunk activity"));
    }
    @Test void overlappingComparisonIsRejected() {
        var before = List.of(window(10, 5, 0, 0, 0, 0, 0, false));
        assertTrue(Diagnosis.compare(before, before).getFirst().contains("overlapping"));
    }
    @Test void comparisonShowsDirectionAndLabelsDemo() {
        var before = List.of(window(10, 60, 100, 0, 0, 0, 0, true));
        var after = List.of(window(20, 5, 0, 0, 0, 0, 0, false));
        String result = String.join("\n", Diagnosis.compare(before, after));
        assertTrue(result.contains("-55.00 ms (lower)"));
        assertTrue(result.contains("DEMO comparison"));
    }
    @Test void sustainedThresholdBoundary() {
        assertFalse(Diagnosis.slow(window(1, 50, 19, 0, 0, 0, 0, false)));
        assertTrue(Diagnosis.slow(window(1, 50, 20, 0, 0, 0, 0, false)));
    }
    @Test void aggregationUsesActualElapsedTimeAndNearestRankPercentile() {
        TickWindow accumulator = new TickWindow();
        for (int i = 0; i < 200; i++) assertEquals(i == 199, accumulator.add(i < 190 ? 5 : 100));
        Window w = accumulator.finish(1, 20, -1, .5, 0, 0, 0, 0, 0, false);
        assertEquals(9.75, w.meanMs());
        assertEquals(5, w.p95Ms());
        assertEquals(100, w.maxMs());
        assertEquals(10, w.slowTicks());
        assertEquals(10, w.tps());
        assertEquals(0, w.gcMillis());
        for (int i = 0; i < 200; i++) accumulator.add(1);
        assertEquals(1, accumulator.finish(2, 10, 0, 0, 0, 0, 0, 0, 0, false).meanMs());
    }
}
