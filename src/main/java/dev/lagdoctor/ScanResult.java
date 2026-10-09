package dev.lagdoctor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

record ScanResult(long capturedAt, int selected, int inspected, int skipped, double workMs,
                  double maxStepMs, List<Hotspot> hotspots, List<String> notes) {
    ScanResult { hotspots = List.copyOf(hotspots); notes = List.copyOf(notes); }
    record Hotspot(String world, int x, int z, int entities, String loadLevel, Map<String, Integer> types) {
        Hotspot { types = Map.copyOf(types); }
        String typesText() {
            return types.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey())).limit(3)
                    .map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", "));
        }
    }
    static List<Hotspot> top(List<Hotspot> all) {
        return all.stream().filter(h -> h.entities() > 0)
                .sorted(Comparator.comparingInt(Hotspot::entities).reversed().thenComparing(Hotspot::world)
                        .thenComparingInt(Hotspot::x).thenComparingInt(Hotspot::z)).limit(10).toList();
    }
    List<String> lines(boolean locations) {
        var lines = new ArrayList<String>();
        lines.add(String.format(Locale.ROOT, "ENTITY SCAN %s | selected %d, inspected %d, skipped %d | collection %.2fms, max step %.2fms",
                Instant.ofEpochMilli(capturedAt), selected, inspected, skipped, workMs, maxStepMs));
        lines.add("Sample of loaded chunks at scan time, not a full census or CPU profile. Missing chunks may contain larger concentrations.");
        for (var h : hotspots) {
            String where = locations ? h.world() + " chunk [" + h.x() + ", " + h.z() + "] (block X/Z "
                    + (h.x() * 16L) + ", " + (h.z() * 16L) + ")" : "[location omitted]";
            lines.add(where + " | " + h.entities() + " entities | " + h.loadLevel() + " | " + h.typesText());
        }
        if (hotspots.isEmpty()) lines.add("No non-empty entity chunks found in inspected sample; this does not rule out entity lag elsewhere.");
        lines.addAll(notes);
        lines.add("Investigate dense chunks in person and check spark entity-ticking stacks before changing a farm. Counts alone do not prove cost.");
        return lines;
    }
}
