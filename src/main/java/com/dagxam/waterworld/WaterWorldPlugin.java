package com.dagxam.waterworld;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        generator = new WaterGenerator(getConfig());

        if (getConfig().getBoolean("island.enabled", true)) {
            int seaLevel = getConfig().getInt("sea-level", 63);
            int centerX = getConfig().getInt("island.center-x", 0);
            int centerZ = getConfig().getInt("island.center-z", 0);
            int radius = getConfig().getInt("island.radius", 100);
            decorator = new NaturalIslandDecorator(seaLevel, centerX, centerZ, radius);

            if (getConfig().getBoolean("island.mountain.enabled", true)) {
                int offsetX = getConfig().getInt("island.mountain.offset-x", 0);
                int offsetZ = getConfig().getInt("island.mountain.offset-z", -22);
                int mountainRadius = getConfig().getInt("island.mountain.radius", 38);
                int peakHeight = getConfig().getInt("island.mountain.peak-height", 92);
                int snowLine = getConfig().getInt("island.mountain.snow-line", 120);
                boolean secondaryPeaks = getConfig().getBoolean("island.mountain.secondary-peaks", false);
                int mountainX = centerX + offsetX;
                int mountainZ = centerZ + offsetZ;
                mountain = new MountainDecorator(seaLevel, mountainX, mountainZ,
                        mountainRadius, peakHeight, snowLine, secondaryPeaks);

                if (getConfig().getBoolean("island.mountain.cave.enabled", true)) {
                    cave = new CaveDecorator(mountainX, mountainZ, mountainRadius,
                            seaLevel, peakHeight);
                }
            }

            if (getConfig().getBoolean("village.enabled", true)) {
                village = new VillageDecorator(centerX, centerZ, radius,
                        getConfig().getInt("village.offset-x", 0),
                        getConfig().getInt("village.offset-z", 55));
            }

            if (getConfig().getBoolean("animals.enabled", true)) {
                int oceanRadius = getConfig().getInt("animals.ocean-radius", 280);
                mobs = new MobDecorator(this, seaLevel, centerX, centerZ, radius, oceanRadius);
            }
        }

        getServer().getPluginManager().registerEvents(this, this);
        if (mobs != null) {
            getServer().getPluginManager().registerEvents(mobs, this);
            mobs.start();
        }
        for (World world : getServer().getWorlds()) scheduleIslandSpawn(world);

        getLogger().info("WaterWorld успешно запущен.");
        getLogger().info("Остров оптимизирован: декорация работает только в нужных чанках.");
        getLogger().info("В холме включена естественная тёмная пещера с ванильным спавном опасных мобов.");
        getLogger().info("Собственная генерация руд отключена: руды должен генерировать Ore-Plugin.");
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (generator == null) generator = new WaterGenerator(getConfig());
        return generator;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (mobs != null) mobs.initializeWorld(event.getWorld());
        scheduleIslandSpawn(event.getWorld());
    }

    private void scheduleIslandSpawn(World world) {
        if (!getConfig().getBoolean("island.enabled", true)) return;
        getServer().getScheduler().runTask(this, () -> setIslandSpawn(world));
    }

    private void setIslandSpawn(World world) {
        int cx = getConfig().getInt("island.center-x", 0);
        int cz = getConfig().getInt("island.center-z", 0);
        int radius = getConfig().getInt("island.radius", 100);
        int search = Math.min(16, Math.max(4, radius / 5));
        world.getChunkAt(cx >> 4, cz >> 4).load();
        Location safe = findSafeSpawn(world, cx, cz, search);
        if (safe != null) world.setSpawnLocation(safe.getBlockX(), safe.getBlockY(), safe.getBlockZ());
        else getLogger().warning("Не удалось найти безопасную точку spawn на острове.");
    }

    private Location findSafeSpawn(World world, int cx, int cz, int radius) {
        Location best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = cx + dx, z = cz + dz, y = world.getHighestBlockYAt(x, z);
                if (world.getBlockAt(x, y, z).getType() != Material.GRASS_BLOCK) continue;
                if (!world.getBlockAt(x, y + 1, z).isEmpty() || !world.getBlockAt(x, y + 2, z).isEmpty()) continue;
                double d = (double) dx * dx + (double) dz * dz;
                if (d < bestDistance) {
                    bestDistance = d;
                    best = new Location(world, x + .5D, y + 1, z + .5D);
                }
            }
        }
        return best;
    }

    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        World world = event.getWorld();
        Chunk chunk = event.getChunk();
        if (mountain != null) mountain.generate(world, chunk.getX(), chunk.getZ());
        if (cave != null) cave.generate(world, chunk.getX(), chunk.getZ());
        if (decorator != null && isChunkNearMainIsland(chunk.getX(), chunk.getZ())) {
            decorator.decorate(world, chunk.getX(), chunk.getZ());
        }
        if (village != null && isVillageTriggerChunk(chunk.getX(), chunk.getZ())) village.generate(world);
        if (mobs != null) getServer().getScheduler().runTask(this, () -> mobs.populate(chunk));
    }

    private boolean isVillageTriggerChunk(int chunkX, int chunkZ) {
        int cx = getConfig().getInt("island.center-x", 0) + getConfig().getInt("village.offset-x", 0);
        int cz = getConfig().getInt("island.center-z", 0) + getConfig().getInt("village.offset-z", 55);
        return chunkX == (cx >> 4) && chunkZ == (cz >> 4);
    }

    private boolean isChunkNearMainIsland(int chunkX, int chunkZ) {
        int cx = getConfig().getInt("island.center-x", 0);
        int cz = getConfig().getInt("island.center-z", 0);
        int radius = getConfig().getInt("island.radius", 100);
        double x = chunkX * 16 + 8, z = chunkZ * 16 + 8;
        double dx = x - cx, dz = z - cz;
        int check = radius + 16;
        return dx * dx + dz * dz <= (double) check * check;
    }
}
