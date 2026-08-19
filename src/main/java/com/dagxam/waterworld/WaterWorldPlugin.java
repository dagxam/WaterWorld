package com.dagxam.waterworld;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

/**
 * Главный класс WaterWorld.
 *
 * Создаёт генератор океана и единственного острова, а также оформляет
 * остров растительностью и заселяет его животными.
 */
public final class WaterWorldPlugin extends JavaPlugin implements Listener {

    private WaterGenerator generator;
    private IslandDecorator decorator;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        generator = new WaterGenerator(getConfig());

        if (getConfig().getBoolean("island.enabled", true)) {
            decorator = new IslandDecorator(
                    getConfig().getInt("sea-level", 63),
                    getConfig().getInt("island.center-x", 0),
                    getConfig().getInt("island.center-z", 0),
                    getConfig().getInt("island.radius", 32)
            );
        }

        getServer().getPluginManager().registerEvents(this, this);

        // Если мир уже загружен к моменту включения плагина, сразу переносим
        // мировой spawn на безопасную точку центрального острова.
        for (World world : getServer().getWorlds()) {
            scheduleIslandSpawn(world);
        }

        getLogger().info("WaterWorld успешно запущен.");
        getLogger().info("Создаётся один большой остров с плавным подводным склоном.");
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
        int radius = getConfig().getInt("island.radius", 32);
        int searchRadius = Math.min(10, Math.max(4, radius / 4));

        // Загружаем центральный чанк, чтобы высота острова была доступна.
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
        if (decorator == null) {
            return;
        }

        World world = event.getWorld();
        Chunk chunk = event.getChunk();

        if (!isChunkNearIsland(chunk.getX(), chunk.getZ())) {
            return;
        }

        decorator.decorate(world, chunk.getX(), chunk.getZ());

        if (getConfig().getBoolean("island.animals.enabled", true)) {
            spawnAnimals(world, chunk);
        }
    }

    private boolean isChunkNearIsland(int chunkX, int chunkZ) {
        int centerX = getConfig().getInt("island.center-x", 0);
        int centerZ = getConfig().getInt("island.center-z", 0);
        int radius = getConfig().getInt("island.radius", 32);

        double x = chunkX * 16 + 8;
        double z = chunkZ * 16 + 8;
        double dx = x - centerX;
        double dz = z - centerZ;

        // Оформляем только сухую часть + ближайшие чанки.
        int checkRadius = radius + 16;
        return dx * dx + dz * dz <= (double) checkRadius * checkRadius;
    }

    private void spawnAnimals(World world, Chunk chunk) {
        int centerX = getConfig().getInt("island.center-x", 0);
        int centerZ = getConfig().getInt("island.center-z", 0);
        int radius = Math.max(4, getConfig().getInt("island.radius", 32) - 5);
        int seaLevel = getConfig().getInt("sea-level", 63);

        int maxAnimals = Math.max(1, getConfig().getInt("island.animals.max-total", 20));
        int animalsPerChunk = Math.max(1, getConfig().getInt("island.animals.per-chunk", 2));

        if (countIslandAnimals(world, centerX, centerZ, radius) >= maxAnimals) {
            return;
        }

        Random random = new Random(
                world.getSeed()
                        ^ ((long) chunk.getX() * 341873128712L)
                        ^ ((long) chunk.getZ() * 132897987541L)
                        ^ 0x5DEECE66DL
        );

        for (int i = 0; i < animalsPerChunk; i++) {
            if (countIslandAnimals(world, centerX, centerZ, radius) >= maxAnimals) {
                return;
            }

            int x = chunk.getX() * 16 + 2 + random.nextInt(12);
            int z = chunk.getZ() * 16 + 1 + random.nextInt(14);

            double dx = x - centerX;
            double dz = z - centerZ;

            if (dx * dx + dz * dz > (double) radius * radius) {
                continue;
            }

            int y = world.getHighestBlockYAt(x, z);

            if (y <= seaLevel
                    || world.getBlockAt(x, y, z).getType() != org.bukkit.Material.GRASS_BLOCK) {
                continue;
            }

            EntityType type;
            switch (random.nextInt(4)) {
                case 0:
                    type = EntityType.COW;
                    break;
                case 1:
                    type = EntityType.SHEEP;
                    break;
                case 2:
                    type = EntityType.PIG;
                    break;
                default:
                    type = EntityType.CHICKEN;
                    break;
            }

            Entity spawned = world.spawnEntity(
                    world.getBlockAt(x, y + 1, z).getLocation(),
                    type
            );

            if (spawned instanceof LivingEntity) {
                ((LivingEntity) spawned).setPersistent(true);
            }
        }
    }

    private int countIslandAnimals(World world, int centerX, int centerZ, int radius) {
        int count = 0;

        for (Entity entity : world.getEntities()) {
            EntityType type = entity.getType();

            if (type != EntityType.COW
                    && type != EntityType.SHEEP
                    && type != EntityType.PIG
                    && type != EntityType.CHICKEN) {
                continue;
            }

            double dx = entity.getLocation().getX() - centerX;
            double dz = entity.getLocation().getZ() - centerZ;

            if (dx * dx + dz * dz <= (double) radius * radius) {
                count++;
            }
        }

        return count;
    }
}
