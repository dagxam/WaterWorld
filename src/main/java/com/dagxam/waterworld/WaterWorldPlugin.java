package com.dagxam.waterworld;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

/** Главный класс WaterWorld. */
public final class WaterWorldPlugin extends JavaPlugin implements Listener {
    private WaterGenerator generator;
    private NaturalIslandDecorator decorator;
    private MountainDecorator mountain;
    private CaveDecorator cave;
    private VillageDecorator village;
    private MobDecorator mobs;
    private String waterWorldName;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        generator = new WaterGenerator(getConfig());
        waterWorldName = getConfig().getString("world.name", "waterworld");
        int seaLevel = getConfig().getInt("sea-level", 63);
        int centerX = getConfig().getInt("island.center-x", 0);
        int centerZ = getConfig().getInt("island.center-z", 0);
        int radius = getConfig().getInt("island.radius", 100);

        if (getConfig().getBoolean("island.enabled", true)) {
            decorator = new NaturalIslandDecorator(seaLevel, centerX, centerZ, radius);
            if (getConfig().getBoolean("island.mountain.enabled", true)) {
                int mx = centerX + getConfig().getInt("island.mountain.offset-x", 0);
                int mz = centerZ + getConfig().getInt("island.mountain.offset-z", -22);
                int mr = getConfig().getInt("island.mountain.radius", 38);
                int peak = getConfig().getInt("island.mountain.peak-height", 92);
                mountain = new MountainDecorator(seaLevel, mx, mz, mr, peak,
                        getConfig().getInt("island.mountain.snow-line", 120),
                        getConfig().getBoolean("island.mountain.secondary-peaks", false));
                if (getConfig().getBoolean("island.mountain.cave.enabled", true)) cave = new CaveDecorator(mx, mz, mr, seaLevel, peak);
            }
            if (getConfig().getBoolean("village.enabled", true)) village = new VillageDecorator(centerX, centerZ, radius,
                    getConfig().getInt("village.offset-x", 0), getConfig().getInt("village.offset-z", 55));
            if (getConfig().getBoolean("animals.enabled", true)) mobs = new MobDecorator(this, seaLevel, centerX, centerZ, radius,
                    getConfig().getInt("animals.ocean-radius", 280));
        }

        getServer().getPluginManager().registerEvents(this, this);
        if (mobs != null) getServer().getPluginManager().registerEvents(mobs, this);
        World world = createOrLoadWaterWorld();
        if (mobs != null) { mobs.initializeWorld(world); mobs.start(); }
        scheduleIslandSpawn(world);
        getLogger().info("WaterWorld успешно запущен. Мир: " + waterWorldName);
    }

    private World createOrLoadWaterWorld() {
        World existing = Bukkit.getWorld(waterWorldName);
        if (existing != null) return existing;
        WorldCreator creator = new WorldCreator(waterWorldName);
        // WorldCreator ожидает ChunkGenerator, поэтому передаём сам генератор, а не JavaPlugin.
        creator.generator(generator);
        World world = creator.createWorld();
        if (world == null) throw new IllegalStateException("Не удалось создать мир WaterWorld: " + waterWorldName);
        return world;
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (generator == null) generator = new WaterGenerator(getConfig());
        return generator;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (!event.getWorld().getName().equals(waterWorldName)) return;
        if (mobs != null) mobs.initializeWorld(event.getWorld());
        scheduleIslandSpawn(event.getWorld());
    }

    private void scheduleIslandSpawn(World world) {
        if (getConfig().getBoolean("island.enabled", true)) getServer().getScheduler().runTask(this, () -> setIslandSpawn(world));
    }

    private void setIslandSpawn(World world) {
        int cx = getConfig().getInt("island.center-x", 0), cz = getConfig().getInt("island.center-z", 0);
        int radius = getConfig().getInt("island.radius", 100), search = Math.min(16, Math.max(4, radius / 5));
        world.getChunkAt(cx >> 4, cz >> 4).load();
        Location safe = findSafeSpawn(world, cx, cz, search);
        if (safe != null) world.setSpawnLocation(safe.getBlockX(), safe.getBlockY(), safe.getBlockZ());
        else getLogger().warning("Не удалось найти безопасную точку появления на острове.");
    }

    private Location findSafeSpawn(World world, int cx, int cz, int radius) {
        Location best = null; double bestDistance = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            int x = cx + dx, z = cz + dz, y = world.getHighestBlockYAt(x, z);
            if (world.getBlockAt(x, y, z).getType() != Material.GRASS_BLOCK) continue;
            if (!world.getBlockAt(x, y + 1, z).isEmpty() || !world.getBlockAt(x, y + 2, z).isEmpty()) continue;
            double d = (double) dx * dx + (double) dz * dz;
            if (d < bestDistance) { bestDistance = d; best = new Location(world, x + .5D, y + 1, z + .5D); }
        }
        return best;
    }

    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        World world = event.getWorld();
        if (!world.getName().equals(waterWorldName)) return;
        Chunk chunk = event.getChunk();
        if (mountain != null) mountain.generate(world, chunk.getX(), chunk.getZ());
        if (cave != null) cave.generate(world, chunk.getX(), chunk.getZ());
        if (decorator != null && isChunkNearAnyIsland(chunk.getX(), chunk.getZ(), world.getSeed())) decorator.decorate(world, chunk.getX(), chunk.getZ());
        if (village != null && isVillageTriggerChunk(chunk.getX(), chunk.getZ())) village.generate(world);
        if (mobs != null) getServer().getScheduler().runTask(this, () -> mobs.populate(chunk));
    }

    private boolean isVillageTriggerChunk(int chunkX, int chunkZ) {
        int cx = getConfig().getInt("island.center-x", 0) + getConfig().getInt("village.offset-x", 0);
        int cz = getConfig().getInt("island.center-z", 0) + getConfig().getInt("village.offset-z", 55);
        return chunkX == (cx >> 4) && chunkZ == (cz >> 4);
    }

    private boolean isChunkNearAnyIsland(int chunkX, int chunkZ, long seed) {
        IslandLayout layout = new IslandLayout(getConfig());
        double x = chunkX * 16 + 8D, z = chunkZ * 16 + 8D;
        for (IslandLayout.Island island : layout.get(seed)) {
            int check = island.radius() + Math.max(20, island.radius() / 3) + 16;
            double dx = x - island.x(), dz = z - island.z();
            if (dx * dx + dz * dz <= (double) check * check) return true;
        }
        return false;
    }
}
