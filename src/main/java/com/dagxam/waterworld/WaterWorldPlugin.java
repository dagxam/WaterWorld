package com.dagxam.waterworld;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

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
                    getConfig().getInt("island.radius", 16)
            );
        }

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("WaterWorld 2.1 успешно запущен.");
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (generator == null) {
            generator = new WaterGenerator(getConfig());
        }
        return generator;
    }

    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        if (decorator == null) {
            return;
        }

        World world = event.getWorld();
        Chunk chunk = event.getChunk();

        // Decorations are only for the central island.
        if (!isChunkNearIsland(chunk.getX(), chunk.getZ())) {
            return;
        }

        decorator.decorate(world, chunk.getX(), chunk.getZ());

        /*
         * A few passive animals are seeded when the chunk is first generated.
         * ChunkPopulateEvent fires on initial population, avoiding respawning
         * the same animals every time a player returns.
         */
        spawnAnimals(world, chunk);
    }

    private boolean isChunkNearIsland(int chunkX, int chunkZ) {
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;

        double dx = minX + 8 - getConfig().getInt("island.center-x", 0);
        double dz = minZ + 8 - getConfig().getInt("island.center-z", 0);

        int radius = getConfig().getInt("island.radius", 16) + 16;
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    private void spawnAnimals(World world, Chunk chunk) {
        int centerX = getConfig().getInt("island.center-x", 0);
        int centerZ = getConfig().getInt("island.center-z", 0);
        int radius = getConfig().getInt("island.radius", 16) - 3;
        int seaLevel = getConfig().getInt("sea-level", 63);

        Random random = new Random(
                world.getSeed()
                        ^ ((long) chunk.getX() * 341873128712L)
                        ^ ((long) chunk.getZ() * 132897987541L)
                        ^ 0x5DEECE66DL
        );

        for (int i = 0; i < 2; i++) {
            int x = chunk.getX() * 16 + 2 + random.nextInt(12);
            int z = chunk.getZ() * 16 + 2 + random.nextInt(12);

            double dx = x - centerX;
            double dz = z - centerZ;

            if (dx * dx + dz * dz > (double) radius * radius) {
                continue;
            }

            int y = world.getHighestBlockYAt(x, z);

            if (y <= seaLevel || !world.getBlockAt(x, y, z).getType().isSolid()) {
                continue;
            }

            EntityType type = random.nextBoolean()
                    ? EntityType.COW
                    : EntityType.SHEEP;

            LivingEntity entity = (LivingEntity) world.spawnEntity(
                    world.getBlockAt(x, y + 1, z).getLocation(),
                    type
            );

            entity.setPersistent(true);
        }
    }
}
