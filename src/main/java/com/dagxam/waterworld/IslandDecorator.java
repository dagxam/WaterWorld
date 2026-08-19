package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import java.util.Random;

/**
 * Lightweight deterministic decoration for the single island.
 *
 * This avoids relying entirely on vanilla decoration, which is not guaranteed
 * to decorate a custom ChunkGenerator the way a normal terrain generator does.
 */
public final class IslandDecorator {

    private final int seaLevel;
    private final int centerX;
    private final int centerZ;
    private final int radius;

    public IslandDecorator(int seaLevel, int centerX, int centerZ, int radius) {
        this.seaLevel = seaLevel;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
    }

    public void decorate(World world, int chunkX, int chunkZ) {
        long seed = world.getSeed()
                ^ ((long) chunkX * 341873128712L)
                ^ ((long) chunkZ * 132897987541L);

        Random random = new Random(seed);

        for (int i = 0; i < 4; i++) {
            int x = chunkX * 16 + 2 + random.nextInt(12);
            int z = chunkZ * 16 + 2 + random.nextInt(12);

            if (!insideIsland(x, z, 4)) {
                continue;
            }

            int y = world.getHighestBlockYAt(x, z);

            if (y <= seaLevel || world.getBlockAt(x, y, z).getType() != Material.GRASS_BLOCK) {
                continue;
            }

            int type = random.nextInt(100);

            if (type < 12) {
                placeTree(world, x, y + 1, z, random);
            } else if (type < 35) {
                placeFlower(world, x, y + 1, z, random);
            } else {
                placeGrass(world, x, y + 1, z);
            }
        }
    }

    private boolean insideIsland(int x, int z, int margin) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return Math.sqrt(dx * dx + dz * dz) <= radius - margin;
    }

    private void placeGrass(World world, int x, int y, int z) {
        if (world.getBlockAt(x, y, z).isEmpty()) {
            world.getBlockAt(x, y, z).setType(Material.GRASS);
        }
    }

    private void placeFlower(World world, int x, int y, int z, Random random) {
        if (!world.getBlockAt(x, y, z).isEmpty()) {
            return;
        }

        Material flower = switch (random.nextInt(4)) {
            case 0 -> Material.DANDELION;
            case 1 -> Material.POPPY;
            case 2 -> Material.AZURE_BLUET;
            default -> Material.OXEYE_DAISY;
        };

        world.getBlockAt(x, y, z).setType(flower);
    }

    private void placeTree(World world, int x, int y, int z, Random random) {
        int height = 4 + random.nextInt(2);

        for (int i = 0; i < height; i++) {
            if (!world.getBlockAt(x, y + i, z).isEmpty()) {
                return;
            }
        }

        for (int i = 0; i < height; i++) {
            world.getBlockAt(x, y + i, z).setType(Material.OAK_LOG);
        }

        int top = y + height;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && dy != 0) {
                        continue;
                    }

                    int bx = x + dx;
                    int by = top + dy;
                    int bz = z + dz;

                    if (world.getBlockAt(bx, by, bz).isEmpty()) {
                        world.getBlockAt(bx, by, bz).setType(Material.OAK_LEAVES);
                    }
                }
            }
        }
    }
}
