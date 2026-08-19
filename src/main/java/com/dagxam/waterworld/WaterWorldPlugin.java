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

/**
 * Главный класс WaterWorld.
 *
 * Центральный остров остаётся главным местом появления игрока.
 * Вокруг него дополнительно создаются пять небольших островов.
 * Мобы полностью переданы стандартной системе Minecraft.
 */
public final class WaterWorldPlugin extends JavaPlugin implements Listener {

    private WaterGenerator generator;
    private IslandDecorator decorator;
    private SatelliteIslandGenerator satelliteIslands;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        generator = new WaterGenerator(getConfig());

        if (getConfig().getBoolean("island.enabled", true)) {
            int seaLevel = getConfig().getInt("sea-level", 63);
            int centerX = getConfig().getInt("island.center-x", 0);
            int centerZ = getConfig().getInt("island.center-z", 0);
            int radius = getConfig().getInt("island.radius", 48);

            decorator = new IslandDecorator(seaLevel, centerX, centerZ, radius);

            satelliteIslands = new SatelliteIslandGenerator(
                    seaLevel,
                    centerX,
                    centerZ,
                    getConfig().getInt("island.satellites.count", 5),
                    getConfig().getInt("island.satellites.distance", 130),
                    getConfig().getInt("island.satellites.min-radius", 12),
                    getConfig().getInt("island.satellites.max-radius", 19),
                    getConfig().getInt("island.satellites.slope-radius", 30),
                    getConfig().getInt("island.satellites.height", 6)
            );
        }

        getServer().getPluginManager().registerEvents(this, this);

        for (World world : getServer().getWorlds()) {
            scheduleIslandSpawn(world);
        }

        getLogger().info("WaterWorld успешно запущен.");
        getLogger().info("Создаётся большой центральный остров и пять островов вокруг него.");
        getLogger().info("Спавн животных и враждебных мобов передан стандартной системе Minecraft.");
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (generator == null) {
            generator = new WaterGenerator(getConfig());
        }
        return generator;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        scheduleIslandSpawn(event.getWorld());
    }

    private void scheduleIslandSpawn(World world) {
        if (!getConfig().getBoolean("island.enabled", true)) {
            return;
        }

        getServer().getScheduler().runTask(this, () -> setIslandSpawn(world));
    }

    private void setIslandSpawn(World world) {
        int centerX = getConfig().getInt("island.center-x", 0);
        int centerZ = getConfig().getInt("island.center-z", 0);
        int radius = getConfig().getInt("island.radius", 48);
        int searchRadius = Math.min(16, Math.max(4, radius / 4));

        world.getChunkAt(centerX >> 4, centerZ >> 4).load();

        Location safe = findSafeSpawn(world, centerX, centerZ, searchRadius);
        if (safe == null) {
            getLogger().warning("Не удалось найти безопасную точку spawn на острове.");
            return;
        }

        world.setSpawnLocation(safe.getBlockX(), safe.getBlockY(), safe.getBlockZ());
        getLogger().info("Spawn мира установлен на острове: "
                + safe.getBlockX() + ", " + safe.getBlockY() + ", " + safe.getBlockZ());
    }

    private Location findSafeSpawn(World world, int centerX, int centerZ, int searchRadius) {
        Location best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int y = world.getHighestBlockYAt(x, z);

                if (world.getBlockAt(x, y, z).getType() != Material.GRASS_BLOCK) {
                    continue;
                }

                if (!world.getBlockAt(x, y + 1, z).isEmpty()
                        || !world.getBlockAt(x, y + 2, z).isEmpty()) {
                    continue;
                }

                double distance = (double) dx * dx + (double) dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new Location(world, x + 0.5D, y + 1.0D, z + 0.5D);
                }
            }
        }

        return best;
    }

    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        World world = event.getWorld();
        Chunk chunk = event.getChunk();

        if (satelliteIslands != null) {
            satelliteIslands.generate(world, chunk.getX(), chunk.getZ());
        }

        if (decorator == null || !isChunkNearMainIsland(chunk.getX(), chunk.getZ())) {
            return;
        }

        decorator.decorate(world, chunk.getX(), chunk.getZ());
        // Никаких ручных spawnEntity здесь нет.
        // Пассивные и враждебные мобы появляются стандартно через Minecraft.
    }

    private boolean isChunkNearMainIsland(int chunkX, int chunkZ) {
        int centerX = getConfig().getInt("island.center-x", 0);
        int centerZ = getConfig().getInt("island.center-z", 0);
        int radius = getConfig().getInt("island.radius", 48);

        double x = chunkX * 16 + 8;
        double z = chunkZ * 16 + 8;
        double dx = x - centerX;
        double dz = z - centerZ;

        int checkRadius = radius + 16;
        return dx * dx + dz * dz <= (double) checkRadius * checkRadius;
    }
}
