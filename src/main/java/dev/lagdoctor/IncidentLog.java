package dev.lagdoctor;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Two healthy windows confirm recovery; at most twelve completed incidents retained. */
final class IncidentLog {
    record Incident(long start, long end, int slowWindows, double mean, double max,
                    boolean demo, boolean recovered, List<Window> evidence) {
        Incident { evidence = List.copyOf(evidence); }
        String summary() {
            return String.format(Locale.ROOT, "%s -> %s | %s | %d slow windows | mean %.2fms, max %.2fms%s",
                    Instant.ofEpochMilli(start), Instant.ofEpochMilli(end), recovered ? "RECOVERED" : "OPEN",
                    slowWindows, mean, max, demo ? " [DEMO]" : "");
        }
    }
    private final ArrayDeque<Incident> completed = new ArrayDeque<>();
    private final ArrayDeque<Window> evidence = new ArrayDeque<>();
    private long start, end;
    private int count, healthy, ticks;
    private double sum, max;
    private boolean demo;
    void accept(Window w) {
        if (Diagnosis.slow(w)) {
            if (count == 0) start = w.endedAt() - Math.round(w.seconds() * 1000);
            end = w.endedAt();
            count++; ticks += w.ticks(); sum += w.meanMs() * w.ticks();
            max = Math.max(max, w.maxMs()); demo |= w.demo(); healthy = 0;
            evidence.addLast(w);
            if (evidence.size() > 6) evidence.removeFirst();
        } else if (count > 0 && ++healthy >= 2) {
            completed.addFirst(snapshot(true));
            while (completed.size() > 12) completed.removeLast();
            count = 0; healthy = 0; ticks = 0; sum = 0; max = 0; demo = false; evidence.clear();
        }
    }
    private Incident snapshot(boolean recovered) {
        return new Incident(start, end, count, sum / ticks, max, demo, recovered, List.copyOf(evidence));
    }
    List<Incident> snapshot() {
        var result = new ArrayList<Incident>();
        if (count > 0) result.add(snapshot(false));
        result.addAll(completed);
        return List.copyOf(result);
    }
    List<String> lines() {
        var result = new ArrayList<String>();
        result.add("INCIDENTS: restart clears history; recovery requires two healthy windows. Means cover slow windows only.");
        for (var incident : snapshot()) result.add(incident.summary());
        if (snapshot().isEmpty()) result.add("No slowdown incidents recorded this session.");
        return result;
    }
}
