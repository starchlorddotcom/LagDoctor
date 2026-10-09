package dev.lagdoctor;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Self-contained, script-free, escaped HTML. No remote fonts, images, analytics or requests. */
final class HtmlReport {
    private HtmlReport() { }
    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
    static String render(String version, List<Window> windows, List<String> diagnosis,
                         List<String> comparison, List<String> incidents, ScanResult scan, boolean locations) {
        StringBuilder html = new StringBuilder("""
                <!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'">
                <title>Lag Doctor · Performance report</title>
                <style>
                :root{color-scheme:dark}*{box-sizing:border-box}body{margin:0;background:#0b1418;color:#edf3f2;font:16px/1.6 system-ui,sans-serif}
                main{max-width:1100px;margin:auto;padding:48px 24px}h1{font-size:46px;line-height:1.1;margin:12px 0}h2{margin:0 0 16px;font-size:23px}
                .eyebrow{color:#6de5b4;letter-spacing:.16em;font-size:12px;font-weight:700}p{color:#b6c8cc}.meta{font-size:14px}
                section{background:#132228;border:1px solid #294049;border-radius:16px;padding:24px;margin-top:24px}
                .cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:14px;margin-top:28px}.card{background:#193039;border-radius:12px;padding:20px}
                .value{font-size:30px;font-weight:700}.label{font-size:13px;color:#b6c8cc}li{margin:12px 0;overflow-wrap:anywhere}.table{overflow-x:auto}
                table{border-collapse:collapse;width:100%;font-variant-numeric:tabular-nums;white-space:nowrap}th,td{text-align:right;padding:11px;border-bottom:1px solid #294049}
                th:first-child,td:first-child{text-align:left}th{font-size:12px;color:#b6c8cc}.track{display:flex;align-items:end;gap:4px;height:150px;border-bottom:1px solid #526973}
                .bar{flex:1;background:#6de5b4;min-height:2px;border-radius:3px 3px 0 0}.bad{background:#ffb16e}.notice{border-left:3px solid #ffb16e;padding-left:14px}
                footer{margin:32px 0;color:#9eb3ba;font-size:13px}@media print{body{background:white;color:black}p,footer,.label,th{color:#333}section,.card{background:white;border:1px solid #888}section{break-inside:avoid}}
                </style><main><div class="eyebrow">LAG DOCTOR / LOCAL PERFORMANCE INTELLIGENCE</div>
                <h1>Evidence before changes.</h1>
                """);
        html.append("<p class=meta>Version ").append(escape(version)).append(" · Captured ")
                .append(escape(Instant.ofEpochMilli(windows.getLast().endedAt()).toString())).append(" · ")
                .append(windows.size()).append(" observed windows</p>");
        int ticks = windows.stream().mapToInt(Window::ticks).sum();
        double seconds = windows.stream().mapToDouble(Window::seconds).sum();
        html.append("<div class=cards>");
        card(html, f("%.2f ms", Diagnosis.mean(windows)), "Mean tick time");
        card(html, f("%.2f", Math.min(20, ticks / seconds)), "Effective TPS");
        card(html, f("%.1f%%", 100.0 * windows.stream().mapToInt(Window::slowTicks).sum() / ticks), "Ticks over 50 ms");
        card(html, f("%.1f s", seconds), "Observed duration");
        html.append("</div>");
        if (windows.stream().anyMatch(Window::demo)) html.append("<p class=notice>DEMO DATA — intentional delays. This report does not diagnose real workload.</p>");
        html.append("<section><h2>Tick pattern</h2><p>Oldest → newest. Height = mean tick time; amber = a slow window. Read p95 and slow ticks for recurring stalls.</p><div class=track role=img aria-label=\"Mean tick durations, detailed in the table below\">");
        double peak = Math.max(50, windows.stream().mapToDouble(Window::meanMs).max().orElse(50));
        for (Window w : windows) html.append("<div class=\"bar ").append(Diagnosis.slow(w) ? "bad" : "")
                .append("\" style=\"height:").append(f("%.2f", 100 * w.meanMs() / peak))
                .append("%\" title=\"").append(f("Mean %.2fms, p95 %.2fms", w.meanMs(), w.p95Ms())).append("\"></div>");
        html.append("</div></section>");
        section(html, "Diagnosis & next steps", diagnosis);
        html.append("<section><h2>Window evidence</h2><div class=table><table><thead><tr><th>Window</th><th>Mean ms</th><th>p95 ms</th><th>Slow %</th><th>GC %</th><th>Entities</th><th>Chunks</th><th>New/sec</th><th>Players</th></tr></thead><tbody>");
        for (int i = 0; i < windows.size(); i++) {
            Window w = windows.get(i);
            html.append(f("<tr><td>%d%s</td><td>%.2f</td><td>%.2f</td><td>%.1f</td><td>%.1f</td><td>%d</td><td>%d</td><td>%.1f</td><td>%d</td></tr>",
                    i + 1, w.demo() ? " · demo" : "", w.meanMs(), w.p95Ms(), w.slowPercent(),
                    100 * w.gcMillis() / (w.seconds() * 1000), w.entities(), w.chunks(), w.newChunks() / w.seconds(), w.players()));
        }
        html.append("</tbody></table></div></section>");
        section(html, "Before / after", comparison.isEmpty() ? List.of("Save /ld baseline, change one thing, wait for new windows, then /ld compare.") : comparison);
        section(html, "Recent incidents", incidents);
        section(html, "Entity concentrations", scan == null ? List.of("No scan captured. Run /ld scan during the issue, then export again.") : scan.lines(locations));
        html.append("<footer>Generated locally. No external resources or telemetry. ")
                .append(locations ? "This export includes world names and chunk coordinates." : "World names and coordinates omitted.")
                .append(" Counts and associations do not establish CPU cost or causation. Paper 1.21.11 compatibility target.</footer></main></html>");
        return html.toString();
    }
    private static String f(String pattern, Object... args) { return String.format(Locale.ROOT, pattern, args); }
    private static void card(StringBuilder h, String value, String label) { h.append("<div class=card><div class=value>").append(escape(value)).append("</div><div class=label>").append(escape(label)).append("</div></div>"); }
    private static void section(StringBuilder h, String title, List<String> lines) {
        h.append("<section><h2>").append(escape(title)).append("</h2><ul>");
        for (String line : lines) h.append("<li>").append(escape(line)).append("</li>");
        h.append("</ul></section>");
    }
}
