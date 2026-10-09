package dev.lagdoctor;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public final class LagDoctorPlugin extends JavaPlugin implements Listener {
    private final TickWindow ticks = new TickWindow();
    private final ArrayDeque<Window> history = new ArrayDeque<>();
    private List<Window> baseline = List.of();
    private final AtomicBoolean exporting = new AtomicBoolean();
    private int capacity, loads, generated, slowStreak, exportSlot;
    private long started, lastGc, lastAlert;
    private boolean demoInWindow;
    private BukkitTask demo;
    private long demoUntil;
    private final IncidentLog incidents = new IncidentLog();
    private EntityScanner scanner;
    private ScanResult lastScan;
    private long nextScan;


    @Override public void onEnable() {
        saveDefaultConfig();
        capacity = Math.clamp(getConfig().getInt("report-windows", 6), 3, 30);
        started = System.nanoTime();
        lastGc = gcMillis();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Lag Doctor ready. /lagdoctor report after approximately 10 seconds. No telemetry or automatic fixes.");
    }
    @Override public void onDisable() { stopDemo(); if (scanner != null) scanner.cancel(); }
    private long gcMillis() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(b -> Math.max(0, b.getCollectionTime())).sum();
    }
    @EventHandler(priority = EventPriority.MONITOR) public void chunk(ChunkLoadEvent event) {
        loads++;
        if (event.isNewChunk()) generated++;
    }
    @EventHandler(priority = EventPriority.MONITOR) public void tick(ServerTickEndEvent event) {
        if (!ticks.add(event.getTickDuration())) return;
        long now = System.nanoTime();
        long gc = gcMillis();
        var memory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        int entities = 0, chunks = 0;
        for (var world : Bukkit.getWorlds()) {
            entities += world.getEntityCount();
            chunks += world.getChunkCount();
        }
        Window w = ticks.finish(System.currentTimeMillis(), (now - started) / 1e9,
                gc - lastGc, memory.getMax() > 0 ? (double) memory.getUsed() / memory.getMax() : 0,
                entities, chunks, loads, generated, Bukkit.getOnlinePlayers().size(), demoInWindow);
        started = now; lastGc = gc; loads = 0; generated = 0; demoInWindow = false;
        history.addLast(w);
        incidents.accept(w);
        while (history.size() > capacity) history.removeFirst();
        slowStreak = Diagnosis.slow(w) ? slowStreak + 1 : 0;
        if (getConfig().getBoolean("console-alerts", true) && slowStreak >= 3
                && (lastAlert == 0 || now - lastAlert >= 300_000_000_000L)) {
            getLogger().warning("Sustained slowdown detected" + (w.demo() ? " (LOCAL DEMO)" : "")
                    + ". Run /lagdoctor report for evidence and next steps.");
            lastAlert = now;
        }
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lagdoctor.admin")) { sender.sendMessage("Lag Doctor: permission required."); return true; }
        String action = args.length == 0 ? "report" : args[0].toLowerCase(Locale.ROOT);
        List<Window> current = List.copyOf(history);
        if (action.equals("scan")) { scanCommand(sender, args); return true; }
        if (action.equals("hotspots")) {
            if (lastScan == null) sender.sendMessage("Run /ld scan first. It samples already-loaded chunks without loading new ones.");
            else lastScan.lines(true).forEach(sender::sendMessage);
            return true;
        }
        if (action.equals("incidents")) {
            incidents.lines().forEach(sender::sendMessage);
            if (args.length == 2 && args[1].equalsIgnoreCase("latest") && !incidents.snapshot().isEmpty()) {
                sender.sendMessage("Evidence from up to six most recent slow windows of the latest incident:");
                Diagnosis.explain(incidents.snapshot().getFirst().evidence()).forEach(sender::sendMessage);
            }
            return true;
        }
        if (action.equals("demo")) { demoCommand(sender, args); return true; }
        if (!List.of("report", "baseline", "compare", "export").contains(action)) {
            sender.sendMessage("/ld report | baseline | compare | scan [stop] | hotspots | incidents [latest] | export [locations] | demo steady|spikes|stop"); return true;
        }
        if (current.isEmpty()) { sender.sendMessage("Lag Doctor: collecting the first 200 ticks; try again in about 10 seconds."); return true; }
        switch (action) {
            case "baseline" -> {
                if (current.size() < capacity) { sender.sendMessage("Wait for " + capacity + " complete windows before saving a baseline."); break; }
                baseline = current;
                sender.sendMessage("Baseline saved in memory at " + Instant.ofEpochMilli(current.getLast().endedAt())
                        + ". Change one thing, wait " + capacity + " new windows, then /lagdoctor compare. Restart clears baseline.");
            }
            case "compare" -> {
                if (baseline.isEmpty()) sender.sendMessage("Save /lagdoctor baseline first.");
                else Diagnosis.compare(baseline, current).forEach(sender::sendMessage);
            }
            case "export" -> {
                if (args.length > 2 || (args.length == 2 && !args[1].equalsIgnoreCase("locations"))) {
                    sender.sendMessage("/ld export [locations] — locations includes world names and chunk coordinates.");
                } else export(sender, current, args.length == 2);
            }
            default -> report(current).forEach(sender::sendMessage);
        }
        return true;
    }
    private List<String> report(List<Window> current) {
        List<String> lines = new ArrayList<>();
        lines.add("=== Lag Doctor " + getPluginMeta().getVersion() + " | " + current.size() + "/" + capacity + " windows ===");
        lines.add("Captured " + Instant.ofEpochMilli(current.getLast().endedAt()) + " | Paper " + Bukkit.getMinecraftVersion());
        lines.add(Diagnosis.metrics(current));
        if (current.size() < capacity) lines.add("PARTIAL REPORT: wait for a full window set before comparing changes.");
        for (int i = 0; i < current.size(); i++) {
            Window w = current.get(i);
            lines.add(String.format(Locale.ROOT,
                    "W%d: %.1fs, mean %.1fms, p95 %.1fms, slow %.1f%%, GC %.0f%%, heap %.0f%%, entities %d, chunks %d, loads %d (new %d), players %d%s",
                    i + 1, w.seconds(), w.meanMs(), w.p95Ms(), w.slowPercent(),
                    100 * w.gcMillis() / (w.seconds() * 1000), 100 * w.heapRatio(), w.entities(), w.chunks(),
                    w.chunkLoads(), w.newChunks(), w.players(), w.demo() ? " [DEMO]" : ""));
        }
        lines.addAll(Diagnosis.explain(current));
        return lines;
    }
    private void export(CommandSender sender, List<Window> current, boolean locations) {
        if (!exporting.compareAndSet(false, true)) { sender.sendMessage("An export is already running."); return; }
        List<String> lines = report(current);
        List<String> comparison = baseline.isEmpty() ? List.of() : Diagnosis.compare(baseline, current);
        lines.addAll(comparison);
        lines.addAll(incidents.lines());
        if (lastScan != null) lines.addAll(lastScan.lines(locations));
        String html = HtmlReport.render(getPluginMeta().getVersion(), current, Diagnosis.explain(current),
                comparison, incidents.lines(), lastScan, locations);
        Path path = getDataFolder().toPath().resolve("reports");
        // Fixed rotating slots. Precise locations only on explicit export request.
        Path output = path.resolve("report-" + (exportSlot++ % 10) + ".txt");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            String message;
            try {
                Files.createDirectories(path);
                Files.write(output, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                Path htmlOutput = output.resolveSibling(output.getFileName().toString().replace(".txt", ".html"));
                Files.writeString(htmlOutput, html, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                message = "Reports saved: " + output + " and " + htmlOutput + " (10 rotating pairs; "
                        + (locations ? "includes locations" : "locations omitted") + ").";
            } catch (Exception e) { message = "Report export failed: " + e.getMessage(); }
            finally { exporting.set(false); }
            String result = message;
            if (isEnabled()) Bukkit.getScheduler().runTask(this, () -> sender.sendMessage(result));
        });
    }
    private void scanCommand(CommandSender sender, String[] args) {
        if (args.length == 2 && args[1].equalsIgnoreCase("stop")) {
            if (scanner != null) { scanner.cancel(); scanner = null; sender.sendMessage("Scan cancelled. Previous completed result retained."); }
            else sender.sendMessage("No scan is running.");
            return;
        }
        if (args.length != 1) { sender.sendMessage("/ld scan [stop]"); return; }
        if (scanner != null) { sender.sendMessage("A scan is already running. /ld scan stop to cancel."); return; }
        long now = System.nanoTime();
        if (now < nextScan) { sender.sendMessage("Scan cooldown: wait " + ((nextScan - now) / 1_000_000_000L + 1) + " seconds."); return; }
        nextScan = now + 60_000_000_000L;
        scanner = new EntityScanner(result -> {
            lastScan = result; scanner = null;
            result.lines(true).forEach(sender::sendMessage);
        });
        scanner.runTaskTimer(this, 1, 1);
        sender.sendMessage("Scanning up to 256 loaded chunks across at most 16 worlds, one step per tick. Results include locations for admins; exports omit them by default.");
    }
    private void demoCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) { sender.sendMessage("Demo commands require the local server console."); return; }
        if (args.length == 2 && args[1].equalsIgnoreCase("stop")) { stopDemo(); sender.sendMessage("Demo stopped; old windows retain DEMO labels until they expire."); return; }
        if (!getConfig().getBoolean("local-demo-enabled") || !"127.0.0.1".equals(Bukkit.getIp())) {
            sender.sendMessage("Demo disabled. Requires local-demo-enabled: true and server-ip=127.0.0.1, then restart."); return;
        }
        if (args.length != 2 || !List.of("steady", "spikes").contains(args[1])) {
            sender.sendMessage("lagdoctor demo steady|spikes|stop (90 seconds maximum)"); return;
        }
        stopDemo();
        boolean spikes = args[1].equals("spikes");
        demoUntil = System.nanoTime() + 90_000_000_000L;
        demo = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            int count;
            @Override public void run() {
                if (System.nanoTime() >= demoUntil) { stopDemo(); getLogger().info("Local demo finished."); return; }
                if (!spikes || ++count % 10 == 0) {
                    demoInWindow = true;
                    long until = System.nanoTime() + (spikes ? 120_000_000L : 65_000_000L);
                    while (System.nanoTime() < until && !Thread.currentThread().isInterrupted())
                        LockSupport.parkNanos(until - System.nanoTime());
                }
            }
        }, 1, 1);
        sender.sendMessage("LOCAL DEMO: " + args[1] + " intentionally delays ticks for up to 90 seconds. Stop with lagdoctor demo stop.");
    }
    private void stopDemo() { if (demo != null) { demo.cancel(); demo = null; } }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("lagdoctor.admin")) return List.of();
        var options = args.length == 1 ? List.of("report", "baseline", "compare", "export", "demo", "scan", "hotspots", "incidents")
                : args.length == 2 && args[0].equalsIgnoreCase("demo") ? List.of("steady", "spikes", "stop") : args.length == 2 && args[0].equalsIgnoreCase("export") ? List.of("locations")
                : args.length == 2 && args[0].equalsIgnoreCase("incidents") ? List.of("latest")
                : args.length == 2 && args[0].equalsIgnoreCase("scan") ? List.of("stop") : List.<String>of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.startsWith(prefix)).toList();
    }
}
