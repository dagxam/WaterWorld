package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Random;

/**
 * Dense deterministic ocean decoration. The ocean is split naturally into
 * sand/gravel/clay fields, seagrass meadows, kelp forests and shallow coral gardens.
 * Decoration is bounded per chunk and never creates large stone structures.
 */
public final class OceanDecorator {
    private final IslandLayout layout;
    private final int seaLevel;
    private final int attempts;
    private final int plantChance;
    private final int kelpChance;
    private final int coralChance;
    private final int meadowChance;
    private final int ventChance;

    public OceanDecorator(FileConfiguration config, IslandLayout layout) {
        this.layout = layout;
        this.seaLevel = config.getInt("sea-level", 62);
        this.attempts = Math.max(0, config.getInt("ocean-life.attempts-per-chunk", 32));
        this.plantChance = clamp(config.getInt("ocean-life.seagrass-chance-percent", 58), 0, 100);
        this.kelpChance = clamp(config.getInt("ocean-life.kelp-chance-percent", 24), 0, 100);
        this.coralChance = clamp(config.getInt("ocean-life.coral-chance-percent", 18), 0, 100);
        this.meadowChance = clamp(config.getInt("ocean-life.meadow-chance-percent", 18), 0, 100);
        this.ventChance = clamp(config.getInt("ocean-life.magma-vent-chance-percent", 2), 0, 100);
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
            if (floorY >= seaLevel - 20 && roll < coralChance) {
                coralGarden(world, x, floorY, z, random);
            } else if (roll < coralChance + kelpChance) {
                kelpCluster(world, x, floorY + 1, z, random);
            } else if (roll < coralChance + kelpChance + meadowChance) {
                seagrassMeadow(world, x, floorY + 1, z, random);
            } else if (roll < coralChance + kelpChance + meadowChance + plantChance) {
                seagrass(world, x, floorY + 1, z, random);
            }
        }
    }

    private boolean isOceanChunk(World world, int chunkX, int chunkZ) {
        int x = chunkX * 16 + 8;
        int z = chunkZ * 16 + 8;
        long dx = x, dz = z, radius = 900L;
        return dx * dx + dz * dz <= radius * radius && !insideIsland(world, x, z);
    }

    private boolean insideIsland(World world, int x, int z) {
        for (IslandLayout.Island island : layout.get(world.getSeed())) {
            long dx = (long) x - island.x();
            long dz = (long) z - island.z();
            long r = island.radius() + 10L;
            if (dx * dx + dz * dz <= r * r) return true;
        }
        return false;
    }

    private void decorateFloor(World world, int x, int y, int z, Random random) {
        Material current = world.getBlockAt(x, y, z).getType();
        if (current != Material.SAND && current != Material.GRAVEL && current != Material.CLAY) return;
        int roll = random.nextInt(100);
        if (roll < 12) world.getBlockAt(x, y, z).setType(Material.GRAVEL, false);
        else if (roll < 24) world.getBlockAt(x, y, z).setType(Material.CLAY, false);
        else if (roll < 28) world.getBlockAt(x, y, z).setType(Material.SAND, false);
        else if (roll < 28 + ventChance && y < seaLevel - 12) world.getBlockAt(x, y, z).setType(Material.MAGMA_BLOCK, false);
    }

    private void seagrassMeadow(World world, int x, int y, int z, Random random) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5 || random.nextInt(100) >= 78) continue;
                int px = x + dx, pz = z + dz;
                int floor = world.getHighestBlockYAt(px, pz);
                if (floor >= seaLevel - 1 || world.getBlockAt(px, floor + 1, pz).getType() != Material.WATER) continue;
                seagrass(world, px, floor + 1, pz, random);
            }
        }
    }

    private void seagrass(World world, int x, int y, int z, Random random) {
        if (world.getBlockAt(x, y, z).getType() != Material.WATER) return;
        if (random.nextInt(100) < 32 && y + 1 < seaLevel && world.getBlockAt(x, y + 1, z).getType() == Material.WATER) {
            world.getBlockAt(x, y, z).setType(Material.TALL_SEAGRASS, false);
        } else {
            world.getBlockAt(x, y, z).setType(Material.SEAGRASS, false);
        }
    }

    private void kelpCluster(World world, int x, int y, int z, Random random) {
        int count = 2 + random.nextInt(4);
        for (int i = 0; i < count; i++) {
            int px = x + random.nextInt(5) - 2;
            int pz = z + random.nextInt(5) - 2;
            int floor = world.getHighestBlockYAt(px, pz);
            if (floor >= seaLevel - 2 || floor < world.getMinHeight() + 2) continue;
            kelp(world, px, floor + 1, pz, random);
        }
    }

    private void kelp(World world, int x, int y, int z, Random random) {
        int height = 3 + random.nextInt(8);
        if (world.getBlockAt(x, y, z).getType() != Material.WATER) return;
        for (int i = 0; i < height && y + i < seaLevel - 1; i++) {
            if (world.getBlockAt(x, y + i, z).getType() != Material.WATER) break;
            world.getBlockAt(x, y + i, z).setType(i == height - 1 ? Material.KELP : Material.KELP_PLANT, false);
        }
    }

    private void coralGarden(World world, int x, int y, int z, Random random) {
        Material[] corals = {
                Material.TUBE_CORAL_BLOCK, Material.BRAIN_CORAL_BLOCK, Material.BUBBLE_CORAL_BLOCK,
                Material.FIRE_CORAL_BLOCK, Material.HORN_CORAL_BLOCK
        };
        int radius = 2 + random.nextInt(3);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius || random.nextInt(100) >= 72) continue;
                int px = x + dx, pz = z + dz;
                int floor = world.getHighestBlockYAt(px, pz);
                if (floor < seaLevel - 24 || floor >= seaLevel - 1) continue;
                if (world.getBlockAt(px, floor + 1, pz).getType() != Material.WATER) continue;

                Material coral = corals[random.nextInt(corals.length)];
                world.getBlockAt(px, floor, pz).setType(coral, false);
                if (random.nextInt(100) < 38 && world.getBlockAt(px, floor + 1, pz).getType() == Material.WATER) {
                    world.getBlockAt(px, floor + 1, pz).setType(Material.SEA_PICKLE, false);
                }
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
