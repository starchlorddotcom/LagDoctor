package dev.lagdoctor;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Opt-in main-thread scan. One world snapshot OR one loaded chunk per tick. */
final class EntityScanner extends BukkitRunnable {
    private record Target(UUID world, int x, int z) { }
    private final List<UUID> worlds;
    private final List<Target> targets = new ArrayList<>();
    private final List<ScanResult.Hotspot> found = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final Consumer<ScanResult> complete;
    private int worldIndex, targetIndex, inspected, skipped;
    private long totalNanos, maxNanos;
    private final long deadline = System.nanoTime() + 30_000_000_000L;
    private final int quota;
    EntityScanner(Consumer<ScanResult> complete) {
        this.complete = complete;
        var all = Bukkit.getWorlds();
        worlds = all.stream().limit(16).map(World::getUID).toList();
        quota = 256 / Math.max(1, worlds.size());
        if (all.size() > 16) notes.add("Only the first 16 worlds were considered (world limit).");
    }
    @Override public void run() {
        if (System.nanoTime() >= deadline) { notes.add("30-second deadline reached; partial scan."); finish(); return; }
        long began = System.nanoTime();
        try {
            if (worldIndex < worlds.size()) select(Bukkit.getWorld(worlds.get(worldIndex++)));
            else if (targetIndex < targets.size()) inspect(targets.get(targetIndex++));
            else { finish(); return; }
        } catch (RuntimeException e) {
            notes.add("Scan stopped after an API error; partial results only.");
            finish(); return;
        }
        long elapsed = System.nanoTime() - began;
        totalNanos += elapsed; maxNanos = Math.max(maxNanos, elapsed);
        // No hard real-time guarantee is possible around a single Bukkit API call.
        if (elapsed > 5_000_000L) { notes.add("Stopped after a collection step exceeded 5ms; partial results protect server responsiveness."); finish(); }
    }
    private void select(World world) {
        if (world == null) { notes.add("A world unloaded before selection."); return; }
        if (world.getChunkCount() > 4096 || world.getEntityCount() > 20000) {
            notes.add("A world was omitted by the 4096-chunk / 20000-entity safety limit. Use spark for that world."); return;
        }
        Chunk[] chunks = world.getLoadedChunks();
        int take = Math.min(quota, chunks.length);
        for (int i = 0; i < take; i++) {
            Chunk chunk = chunks[(int) ((long) i * chunks.length / take)];
            targets.add(new Target(world.getUID(), chunk.getX(), chunk.getZ()));
        }
        if (take < chunks.length) notes.add("A world was sampled rather than fully scanned (per-world quota " + quota + ").");
    }
    private void inspect(Target target) {
        World world = Bukkit.getWorld(target.world);
        if (world == null || world.getEntityCount() > 20000 || !world.isChunkLoaded(target.x, target.z)) { skipped++; return; }
        Chunk chunk = world.getChunkAt(target.x, target.z); // Already loaded; checked on the same main-thread turn.
        if (!chunk.isEntitiesLoaded()) { skipped++; return; } // getEntities would otherwise force entity loading.
        var entities = chunk.getEntities();
        var types = new HashMap<String,Integer>();
        for (var entity : entities) types.merge(entity.getType().name(), 1, Integer::sum);
        inspected++;
        found.add(new ScanResult.Hotspot(world.getName(), target.x, target.z, entities.length,
                chunk.getLoadLevel().name(), types));
    }
    private void finish() {
        cancel();
        complete.accept(new ScanResult(System.currentTimeMillis(), targets.size(), inspected,
                skipped + targets.size() - targetIndex, totalNanos / 1e6, maxNanos / 1e6, ScanResult.top(found), notes));
    }
}
