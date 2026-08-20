package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.Random;

/** Живая растительность главного острова: дубы, берёзы, ели, трава и цветы. */
public final class NaturalIslandDecorator {
    private final int seaLevel, centerX, centerZ, radius;

    public NaturalIslandDecorator(int seaLevel, int centerX, int centerZ, int radius) {
        this.seaLevel = seaLevel;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
    }

    public void decorate(World world, int chunkX, int chunkZ) {
        long seed = world.getSeed() ^ ((long) chunkX * 341873128712L)
                ^ ((long) chunkZ * 132897987541L) ^ 0x6A09E667F3BCC909L;
        Random random = new Random(seed);

        for (int i = 0; i < 16; i++) {
            int x = chunkX * 16 + 1 + random.nextInt(14);
            int z = chunkZ * 16 + 1 + random.nextInt(14);
            double dx = x - centerX, dz = z - centerZ;
            if (dx * dx + dz * dz > (double) (radius - 8) * (radius - 8)) continue;

            int y = world.getHighestBlockYAt(x, z);
            if (y <= seaLevel || world.getBlockAt(x, y, z).getType() != Material.GRASS_BLOCK) continue;

            int type = random.nextInt(100);
            if (type < 22) placeTree(world, x, y + 1, z, random);
            else if (type < 60) placeFlower(world, x, y + 1, z, random);
            else placeGrass(world, x, y + 1, z);
        }
    }

    private void placeGrass(World world, int x, int y, int z) {
        if (world.getBlockAt(x, y, z).isEmpty()) world.getBlockAt(x, y, z).setType(Material.GRASS);
    }

    private void placeFlower(World world, int x, int y, int z, Random random) {
        if (!world.getBlockAt(x, y, z).isEmpty()) return;
        Material flower;
        switch (random.nextInt(8)) {
            case 0: flower = Material.DANDELION; break;
            case 1: flower = Material.POPPY; break;
            case 2: flower = Material.AZURE_BLUET; break;
            case 3: flower = Material.OXEYE_DAISY; break;
            case 4: flower = Material.CORNFLOWER; break;
            case 5: flower = Material.ALLIUM; break;
            case 6: flower = Material.BLUE_ORCHID; break;
            default: flower = Material.RED_TULIP; break;
        }
        world.getBlockAt(x, y, z).setType(flower);
    }

    private void placeTree(World world, int x, int y, int z, Random random) {
        int roll = random.nextInt(100);
        Material log, leaves;
        int height;
        if (roll < 60) {
            log = Material.OAK_LOG; leaves = Material.OAK_LEAVES; height = 5 + random.nextInt(3);
        } else if (roll < 85) {
            log = Material.BIRCH_LOG; leaves = Material.BIRCH_LEAVES; height = 5 + random.nextInt(3);
        } else {
            log = Material.SPRUCE_LOG; leaves = Material.SPRUCE_LEAVES; height = 6 + random.nextInt(3);
        }

        for (int i = 0; i < height + 3; i++) if (!world.getBlockAt(x, y + i, z).isEmpty()) return;
        for (int i = 0; i < height; i++) world.getBlockAt(x, y + i, z).setType(log);

        int top = y + height - 1;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    int d = Math.abs(dx) + Math.abs(dz);
                    if (d >= 4 && dy != 0) continue;
                    if (dy == 2 && d > 1) continue;
                    if (world.getBlockAt(x + dx, top + dy, z + dz).isEmpty())
                        world.getBlockAt(x + dx, top + dy, z + dz).setType(leaves);
                }
            }
        }
    }
}
