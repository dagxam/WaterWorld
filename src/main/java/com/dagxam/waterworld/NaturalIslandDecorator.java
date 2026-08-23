package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;
import java.util.Random;

/** Живая растительность островов: дубы, берёзы, ели, трава и цветы. */
public final class NaturalIslandDecorator {
    private final int seaLevel, centerX, centerZ, radius;
    public NaturalIslandDecorator(int seaLevel, int centerX, int centerZ, int radius) { this.seaLevel = seaLevel; this.centerX = centerX; this.centerZ = centerZ; this.radius = radius; }

    public void decorate(World world, int chunkX, int chunkZ) {
        Random random = new Random(world.getSeed() ^ ((long) chunkX * 341873128712L) ^ ((long) chunkZ * 132897987541L) ^ 0x6A09E667F3BCC909L);
        for (int i = 0; i < 16; i++) {
            int x = chunkX * 16 + 1 + random.nextInt(14), z = chunkZ * 16 + 1 + random.nextInt(14);
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

    private void placeGrass(World world, int x, int y, int z) { if (world.getBlockAt(x, y, z).isEmpty()) world.getBlockAt(x, y, z).setType(Material.SHORT_GRASS); }
    private void placeFlower(World world, int x, int y, int z, Random random) {
        if (!world.getBlockAt(x, y, z).isEmpty()) return;
        Material flower = switch (random.nextInt(8)) {
            case 0 -> Material.DANDELION; case 1 -> Material.POPPY; case 2 -> Material.AZURE_BLUET; case 3 -> Material.OXEYE_DAISY;
            case 4 -> Material.CORNFLOWER; case 5 -> Material.ALLIUM; case 6 -> Material.BLUE_ORCHID; default -> Material.RED_TULIP;
        };
        world.getBlockAt(x, y, z).setType(flower);
    }

    private void placeTree(World world, int x, int y, int z, Random random) {
        int roll = random.nextInt(100), height; Material log, leaves;
        if (roll < 60) { log = Material.OAK_LOG; leaves = Material.OAK_LEAVES; height = 5 + random.nextInt(3); }
        else if (roll < 85) { log = Material.BIRCH_LOG; leaves = Material.BIRCH_LEAVES; height = 5 + random.nextInt(3); }
        else { log = Material.SPRUCE_LOG; leaves = Material.SPRUCE_LEAVES; height = 6 + random.nextInt(3); }
        for (int i = 0; i < height + 3; i++) if (!world.getBlockAt(x, y + i, z).isEmpty()) return;
        for (int i = 0; i < height; i++) world.getBlockAt(x, y + i, z).setType(log);
        int top = y + height - 1;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -1; dy <= 2; dy++) {
            int d = Math.abs(dx) + Math.abs(dz);
            if (d >= 4 && dy != 0) continue;
            if (dy == 2 && d > 1) continue;
            if (world.getBlockAt(x + dx, top + dy, z + dz).isEmpty()) world.getBlockAt(x + dx, top + dy, z + dz).setType(leaves);
        }
    }
}
