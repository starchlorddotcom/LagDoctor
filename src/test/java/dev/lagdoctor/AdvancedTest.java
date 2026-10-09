package dev.lagdoctor;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AdvancedTest {
    Window w(long end, boolean slow, int entities, int chunks, boolean demo) {
        return new Window(end, 10, 200, slow ? 60 : 5, slow ? 90 : 8, slow ? 120 : 10,
                slow ? 100 : 0, 0, .5, entities, chunks, 0, 0, 0, demo);
    }
    @Test void persistentEntityPopulationIsWeakEvidence() {
        var findings = Evidence.rank(List.of(w(10000, false, 2000, 50, false), w(20000, false, 2000, 50, false),
                w(30000, true, 2000, 50, false), w(40000, true, 2000, 50, false)));
        assertEquals(1, findings.size());
        assertTrue(findings.getFirst().grade().startsWith("WEAK CONTRAST"));
        assertTrue(findings.getFirst().detail().contains("2/2 slow windows"));
    }
    @Test void changingPopulationGetsStrongerAssociationButNeverCertainty() {
        var lines = Evidence.lines(List.of(w(10000, false, 100, 50, false), w(20000, false, 150, 50, false),
                w(30000, true, 2000, 50, false), w(40000, true, 2500, 50, false)));
        String text = String.join("\n", lines);
        assertTrue(text.contains("STRONGER ASSOCIATION"));
        assertTrue(text.contains("not proof"));
    }
    @Test void oneHealthyWindowCannotUpgradeConfidence() {
        var finding = Evidence.rank(List.of(w(10000, false, 1, 50, false),
                w(20000, true, 3000, 50, false), w(30000, true, 3000, 50, false))).getFirst();
        assertTrue(finding.grade().startsWith("LIMITED"));
    }
    @Test void demoDoesNotRankIncidentalSignalsAsCauses() {
        String text = String.join("\n", Diagnosis.explain(List.of(w(10000, true, 4000, 50, true))));
        assertTrue(text.contains("SUPPRESSED"));
        assertTrue(text.contains("CAUSE UNRESOLVED"));
        assertFalse(text.contains("Entity population ["));
    }
    @Test void comparisonWarnsAboutWorkloadAndReportsSlowTickDelta() {
        String text = String.join("\n", Diagnosis.compare(List.of(w(10000, true, 4000, 300, false)),
                List.of(w(30000, false, 200, 50, false))));
        assertTrue(text.contains("entities changed 4000.0 -> 200.0"));
        assertTrue(text.contains("loaded chunks changed 300.0 -> 50.0"));
        assertTrue(text.contains("-50.0 percentage points"));
        assertTrue(text.contains("CONSISTENT DIRECTION"));
    }
    @Test void overlappingWindowRangesDoNotPromiseImprovement() {
        var before = List.of(w(10000, true, 0, 0, false), w(20000, false, 0, 0, false));
        var after = List.of(w(30000, false, 0, 0, false), w(40000, true, 0, 0, false));
        assertTrue(String.join("\n", Diagnosis.compare(before, after)).contains("MIXED WINDOWS"));
    }
    @Test void incidentRecoveryNeedsTwoHealthyWindowsAndPreservesSlowEvidence() {
        var log = new IncidentLog();
        log.accept(w(10000, true, 0, 0, true));
        log.accept(w(20000, false, 0, 0, false));
        assertFalse(log.snapshot().getFirst().recovered());
        log.accept(w(30000, true, 0, 0, false));
        log.accept(w(40000, false, 0, 0, false));
        log.accept(w(50000, false, 0, 0, false));
        var incident = log.snapshot().getFirst();
        assertTrue(incident.recovered()); assertTrue(incident.demo());
        assertEquals(2, incident.slowWindows()); assertEquals(30000, incident.end());
        assertEquals(60, incident.mean());
    }
    @Test void incidentAndEvidenceRetentionAreBounded() {
        var log = new IncidentLog(); long time = 0;
        for (int episode = 0; episode < 15; episode++) {
            for (int i = 0; i < 10; i++) log.accept(w(time += 10000, true, 0, 0, false));
            log.accept(w(time += 10000, false, 0, 0, false));
            log.accept(w(time += 10000, false, 0, 0, false));
        }
        assertEquals(12, log.snapshot().size());
        assertEquals(6, log.snapshot().getFirst().evidence().size());
        assertThrows(UnsupportedOperationException.class, () -> log.snapshot().clear());
    }
    @Test void scansRankByDensityAndKeepOnlyTopTen() {
        var chunks = new ArrayList<ScanResult.Hotspot>();
        for (int i = 0; i < 20; i++) chunks.add(new ScanResult.Hotspot("world", i, 0, i, "FULL", Map.of("SHEEP", i)));
        var top = ScanResult.top(chunks);
        assertEquals(10, top.size()); assertEquals(19, top.getFirst().entities());
        assertEquals(10, top.getLast().entities());
    }
    @Test void htmlEscapesUntrustedNamesAndRedactsLocationsByDefault() {
        String secret = "private-<script>alert('x')</script>";
        var scan = new ScanResult(10000, 1, 1, 0, .5, .5,
                List.of(new ScanResult.Hotspot(secret, -7, 4, 12, "FULL", Map.of("SHEEP", 12))), List.of());
        var ws = List.of(w(10000, false, 12, 1, false));
        String redacted = HtmlReport.render("0.2.0", ws, List.of("normal"), List.of(), List.of(), scan, false);
        assertFalse(redacted.contains("private-")); assertFalse(redacted.contains("chunk [-7"));
        String located = HtmlReport.render("0.2.0", ws, List.of("normal"), List.of(), List.of(), scan, true);
        assertFalse(located.contains("<script>")); assertTrue(located.contains("&lt;script&gt;"));
        assertTrue(located.contains("block X/Z -112, 64"));
        assertTrue(located.contains("default-src 'none'"));
    }
}
