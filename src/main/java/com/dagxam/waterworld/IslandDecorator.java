package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.Random;

/** Ручное оформление единственного острова. */
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
        long seed = world.getSeed() ^ ((long) chunkX * 341873128712L)
                ^ ((long) chunkZ * 132897987541L) ^ 0x6A09E667F3BCC909L;
        Random random = new Random(seed);
        for (int i = 0; i < 9; i++) {
            int x = chunkX * 16 + 3 + random.nextInt(10);
            int z = chunkZ * 16 + 1 + random.nextInt(14);
            if (!insideIsland(x, z, 5)) continue;
            int y = world.getHighestBlockYAt(x, z);
            if (y <= seaLevel || world.getBlockAt(x, y, z).getType() != Material.GRASS_BLOCK) continue;
            int type = random.nextInt(100);
            if (type < 16) placeTree(world, x, y + 1, z, random);
            else if (type < 48) placeFlower(world, x, y + 1, z, random);
            else placeGrass(world, x, y + 1, z);
        }
    }

    private boolean insideIsland(int x, int z, int margin) {
        double dx = x - centerX, dz = z - centerZ;
        return Math.sqrt(dx * dx + dz * dz) <= Math.max(0, radius - margin);
    }

    private void placeGrass(World world, int x, int y, int z) {
        if (world.getBlockAt(x, y, z).isEmpty()) {
            world.getBlockAt(x, y, z).setType(Material.SHORT_GRASS);
        }
    }

    private void placeFlower(World world, int x, int y, int z, Random random) {
        if (!world.getBlockAt(x, y, z).isEmpty()) return;
        Material flower = switch (random.nextInt(6)) {
            case 0 -> Material.DANDELION;
            case 1 -> Material.POPPY;
            case 2 -> Material.AZURE_BLUET;
            case 3 -> Material.OXEYE_DAISY;
            case 4 -> Material.CORNFLOWER;
            default -> Material.ALLIUM;
        };
        world.getBlockAt(x, y, z).setType(flower);
    }

    private void placeTree(World world, int x, int y, int z, Random random) {
        int height = 5 + random.nextInt(3);
        for (int i = 0; i < height + 3; i++) if (!world.getBlockAt(x, y + i, z).isEmpty()) return;
        for (int i = 0; i < height; i++) world.getBlockAt(x, y + i, z).setType(Material.OAK_LOG);
        int top = y + height - 1;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -1; dy <= 2; dy++) {
            int distance = Math.abs(dx) + Math.abs(dz);
            if (distance >= 4 && dy != 0) continue;
            if (dy == 2 && distance > 1) continue;
            int bx = x + dx, by = top + dy, bz = z + dz;
            if (world.getBlockAt(bx, by, bz).isEmpty()) world.getBlockAt(bx, by, bz).setType(Material.OAK_LEAVES);
        }
    }
}
