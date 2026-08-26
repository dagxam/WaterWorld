package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Random;

/** Deterministic ocean-floor decoration: sand ripples, gravel/clay patches, seagrass, kelp and coral reefs. */
public final class OceanDecorator {
    private final IslandLayout layout;
    private final int seaLevel;
    private final int attempts;
    private final int plantChance;
    private final int kelpChance;
    private final int coralChance;

    public OceanDecorator(FileConfiguration config, IslandLayout layout) {
        this.layout = layout;
        this.seaLevel = config.getInt("sea-level", 63);
        this.attempts = Math.max(0, config.getInt("ocean-life.attempts-per-chunk", 14));
        this.plantChance = clamp(config.getInt("ocean-life.seagrass-chance-percent", 42), 0, 100);
        this.kelpChance = clamp(config.getInt("ocean-life.kelp-chance-percent", 22), 0, 100);
        this.coralChance = clamp(config.getInt("ocean-life.coral-chance-percent", 12), 0, 100);
    }

    public void decorate(World world, int chunkX, int chunkZ) {
        if (attempts == 0 || !isOceanChunk(world, chunkX, chunkZ)) return;
        Random random = new Random(world.getSeed() ^ 0x51A7E5EEDL ^ ((long) chunkX * 341873128712L) ^ ((long) chunkZ * 132897987541L));
        for (int i = 0; i < attempts; i++) {
            int x = chunkX * 16 + random.nextInt(16);
            int z = chunkZ * 16 + random.nextInt(16);
            if (insideIsland(world, x, z)) continue;
            int floorY = world.getHighestBlockYAt(x, z);
            if (floorY >= seaLevel - 1 || floorY < world.getMinHeight() + 2) continue;
            if (world.getBlockAt(x, floorY + 1, z).getType() != Material.WATER) continue;
            decorateFloor(world, x, floorY, z, random);
            int roll = random.nextInt(100);
            if (roll < coralChance && floorY >= seaLevel - 18) coralPatch(world, x, floorY, z, random);
            else if (roll < coralChance + kelpChance) kelp(world, x, floorY + 1, z, random);
            else if (roll < coralChance + kelpChance + plantChance) seagrass(world, x, floorY + 1, z, random);
        }
    }

    private boolean isOceanChunk(World world, int chunkX, int chunkZ) {
        int x = chunkX * 16 + 8, z = chunkZ * 16 + 8;
        long dx = x, dz = z;
        long radius = 900L;
        return dx * dx + dz * dz <= radius * radius && !insideIsland(world, x, z);
    }

    private boolean insideIsland(World world, int x, int z) {
        for (IslandLayout.Island island : layout.get(world.getSeed())) {
            long dx = (long) x - island.x(), dz = (long) z - island.z();
            long r = island.radius() + 10L;
            if (dx * dx + dz * dz <= r * r) return true;
        }
        return false;
    }

    private void decorateFloor(World world, int x, int y, int z, Random random) {
        Material current = world.getBlockAt(x, y, z).getType();
        if (current != Material.SAND && current != Material.GRAVEL && current != Material.CLAY) return;
        int roll = random.nextInt(100);
        if (roll < 10) world.getBlockAt(x, y, z).setType(Material.GRAVEL, false);
        else if (roll < 16 && y > world.getMinHeight() + 8) world.getBlockAt(x, y, z).setType(Material.CLAY, false);
        else if (roll < 20 && y < seaLevel - 10) world.getBlockAt(x, y, z).setType(Material.MAGMA_BLOCK, false);
    }

    private void seagrass(World world, int x, int y, int z, Random random) {
        if (world.getBlockAt(x, y, z).getType() != Material.WATER) return;
        if (random.nextInt(100) < 28 && world.getBlockAt(x, y + 1, z).getType() == Material.WATER) {
            world.getBlockAt(x, y, z).setType(Material.TALL_SEAGRASS, false);
        } else world.getBlockAt(x, y, z).setType(Material.SEAGRASS, false);
    }

    private void kelp(World world, int x, int y, int z, Random random) {
        int height = 3 + random.nextInt(8);
        if (world.getBlockAt(x, y, z).getType() != Material.WATER) return;
        for (int i = 0; i < height && y + i < seaLevel - 1; i++) {
            if (world.getBlockAt(x, y + i, z).getType() != Material.WATER) break;
            world.getBlockAt(x, y + i, z).setType(i == height - 1 ? Material.KELP : Material.KELP_PLANT, false);
        }
    }

    private void coralPatch(World world, int x, int y, int z, Random random) {
        Material[] corals = {Material.TUBE_CORAL_BLOCK, Material.BRAIN_CORAL_BLOCK, Material.BUBBLE_CORAL_BLOCK, Material.FIRE_CORAL_BLOCK, Material.HORN_CORAL_BLOCK};
        Material coral = corals[random.nextInt(corals.length)];
        int radius = 1 + random.nextInt(2);
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz > radius * radius + 1) continue;
            int py = world.getHighestBlockYAt(x + dx, z + dz);
            if (py < seaLevel - 22 || py >= seaLevel - 1) continue;
            if (world.getBlockAt(x + dx, py + 1, z + dz).getType() != Material.WATER) continue;
            if (random.nextInt(100) < 70) world.getBlockAt(x + dx, py, z + dz).setType(coral, false);
            if (random.nextInt(100) < 25 && world.getBlockAt(x + dx, py + 1, z + dz).getType() == Material.WATER)
                world.getBlockAt(x + dx, py + 1, z + dz).setType(Material.SEA_PICKLE, false);
        }
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
