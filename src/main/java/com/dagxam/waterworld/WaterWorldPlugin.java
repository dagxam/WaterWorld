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
import org.bukkit.scheduler.BukkitTask;

/** Главный класс WaterWorld. */
public final class WaterWorldPlugin extends JavaPlugin implements Listener {
    private WaterGenerator generator;
    private NaturalIslandDecorator decorator;
    private MountainDecorator mountain;
    private CaveDecorator cave;
    private VillageDecorator village;
    private MobDecorator mobs;
    private String waterWorldName;
    private BukkitTask timeCycleTask;

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
            if (getConfig().getBoolean("village.enabled", true)) {
                village = new VillageDecorator(centerX, centerZ, radius,
                        getConfig().getInt("village.offset-x", 0), getConfig().getInt("village.offset-z", 55));
            }
            if (getConfig().getBoolean("animals.enabled", true)) {
                mobs = new MobDecorator(this, seaLevel, centerX, centerZ, radius,
                        getConfig().getInt("animals.ocean-radius", 280));
            }
        }

        getServer().getPluginManager().registerEvents(this, this);
        if (mobs != null) getServer().getPluginManager().registerEvents(mobs, this);

        World world = createOrLoadWaterWorld();
        // Сразу генерируем центральные чанки. Это исключает появление игрока в пустом океане
        // до первой естественной генерации чанков.
        generateSpawnIslandChunks(world);

        if (mobs != null) {
            mobs.initializeWorld(world);
            mobs.start();
        }
        setIslandSpawn(world);
        startCustomTimeCycle(world);
        getLogger().info("WaterWorld успешно запущен. Мир: " + waterWorldName + ", генератор: " + world.getGenerator());
    }

    @Override
    public void onDisable() {
        if (timeCycleTask != null) timeCycleTask.cancel();
    }

    private World createOrLoadWaterWorld() {
        World existing = Bukkit.getWorld(waterWorldName);
        if (existing != null) return existing;
        WorldCreator creator = new WorldCreator(waterWorldName);
        creator.generator(generator);
        World world = creator.createWorld();
        if (world == null) throw new IllegalStateException("Не удалось создать мир WaterWorld: " + waterWorldName);
        return world;
    }

    private void generateSpawnIslandChunks(World world) {
        int radius = Math.max(16, getConfig().getInt("island.radius", 100));
        int cx = getConfig().getInt("island.center-x", 0);
        int cz = getConfig().getInt("island.center-z", 0);
        int chunkRadius = Math.max(1, (radius + 31) / 16);
        int centerChunkX = Math.floorDiv(cx, 16);
        int centerChunkZ = Math.floorDiv(cz, 16);

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                world.getChunkAt(chunkX, chunkZ).load(true);
            }
        }
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
    }

    private void startCustomTimeCycle(World world) {
        if (!getConfig().getBoolean("time-cycle.enabled", true)) return;
        int daySeconds = getConfig().getInt("time-cycle.day-duration-seconds", 600);
        int nightSeconds = getConfig().getInt("time-cycle.night-duration-seconds", 600);
        if (daySeconds <= 0 || nightSeconds <= 0) return;

        long interval = Math.max(1L, getConfig().getLong("time-cycle.update-interval-ticks", 1L));
        double daySpeed = 12000.0D / (daySeconds * 20.0D);
        double nightSpeed = 12000.0D / (nightSeconds * 20.0D);
        final double[] remainder = {0.0D};
        world.setGameRuleValue("doDaylightCycle", "false");

        timeCycleTask = getServer().getScheduler().runTaskTimer(this, () -> {
            long current = Math.floorMod(world.getTime(), 24000L);
            double speed = current < 12000L ? daySpeed : nightSpeed;
            remainder[0] += speed * interval;
            long advance = (long) remainder[0];
            if (advance <= 0L) return;
            remainder[0] -= advance;
            world.setTime((current + advance) % 24000L);
        }, interval, interval);
    }

    private void setIslandSpawn(World world) {
        int cx = getConfig().getInt("island.center-x", 0);
        int cz = getConfig().getInt("island.center-z", 0);
        int radius = Math.min(32, Math.max(8, getConfig().getInt("island.radius", 100) / 4));
        Location safe = findSafeSpawn(world, cx, cz, radius);
        if (safe != null) {
            world.setSpawnLocation(safe.getBlockX(), safe.getBlockY(), safe.getBlockZ());
            getLogger().info("Точка появления установлена на острове: " + safe.getBlockX() + ", " + safe.getBlockY() + ", " + safe.getBlockZ());
        } else {
            getLogger().severe("Остров не найден в центральных чанках. Если мир был создан старой версией плагина, удалите только папку мира '" + waterWorldName + "' и запустите сервер снова.");
        }
    }

    private Location findSafeSpawn(World world, int cx, int cz, int radius) {
        Location best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                int y = world.getHighestBlockYAt(x, z);
                if (world.getBlockAt(x, y, z).getType() != Material.GRASS_BLOCK) continue;
                if (!world.getBlockAt(x, y + 1, z).isEmpty() || !world.getBlockAt(x, y + 2, z).isEmpty()) continue;
                double d = (double) dx * dx + (double) dz * dz;
                if (d < bestDistance) {
                    bestDistance = d;
                    best = new Location(world, x + 0.5D, y + 1, z + 0.5D);
                }
            }
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
        return chunkX == Math.floorDiv(cx, 16) && chunkZ == Math.floorDiv(cz, 16);
    }

    private boolean isChunkNearAnyIsland(int chunkX, int chunkZ, long seed) {
        IslandLayout layout = new IslandLayout(getConfig());
        double x = chunkX * 16 + 8.0D;
        double z = chunkZ * 16 + 8.0D;
        for (IslandLayout.Island island : layout.get(seed)) {
            int check = island.radius() + Math.max(20, island.radius() / 3) + 16;
            double dx = x - island.x();
            double dz = z - island.z();
            if (dx * dx + dz * dz <= (double) check * check) return true;
        }
        return false;
    }
}
