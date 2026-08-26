package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;
import java.util.Random;

/** Живая растительность для главного и всех дополнительных зелёных островов. */
public final class NaturalIslandDecorator {
    private final int seaLevel;
    private final IslandLayout layout;

    public NaturalIslandDecorator(int seaLevel, IslandLayout layout) {
        this.seaLevel = seaLevel;
        this.layout = layout;
    }

    public void decorate(World world, int chunkX, int chunkZ) {
        List<IslandLayout.Island> islands = layout.get(world.getSeed());
        Random random = new Random(world.getSeed()
                ^ ((long) chunkX * 341873128712L)
                ^ ((long) chunkZ * 132897987541L)
                ^ 0x6A09E667F3BCC909L);

        for (int i = 0; i < 22; i++) {
            int x = chunkX * 16 + 1 + random.nextInt(14);
            int z = chunkZ * 16 + 1 + random.nextInt(14);
            IslandLayout.Island island = islandAt(islands, x, z);
            if (island == null) continue;

            int margin = island.main() ? 8 : Math.max(4, island.radius() / 5);
            double dx = x - island.x();
            double dz = z - island.z();
            double usableRadius = Math.max(6, island.radius() - margin);
            if (dx * dx + dz * dz > usableRadius * usableRadius) continue;

            int y = world.getHighestBlockYAt(x, z);
            if (y <= seaLevel || world.getBlockAt(x, y, z).getType() != Material.GRASS_BLOCK) continue;

            int type = random.nextInt(100);
            if (type < 18) placeTree(world, x, y + 1, z, random);
            else if (type < 58) placeFlower(world, x, y + 1, z, random);
            else placeGrass(world, x, y + 1, z);
        }
    }

    private IslandLayout.Island islandAt(List<IslandLayout.Island> islands, int x, int z) {
        for (IslandLayout.Island island : islands) {
            double dx = x - island.x();
            double dz = z - island.z();
            if (dx * dx + dz * dz <= (double) island.radius() * island.radius()) return island;
        }
        return null;
    }

    private void placeGrass(World world, int x, int y, int z) {
        if (world.getBlockAt(x, y, z).isEmpty()) world.getBlockAt(x, y, z).setType(Material.SHORT_GRASS);
    }

    private void placeFlower(World world, int x, int y, int z, Random random) {
        if (!world.getBlockAt(x, y, z).isEmpty()) return;
        Material flower = switch (random.nextInt(8)) {
            case 0 -> Material.DANDELION;
            case 1 -> Material.POPPY;
            case 2 -> Material.AZURE_BLUET;
            case 3 -> Material.OXEYE_DAISY;
            case 4 -> Material.CORNFLOWER;
            case 5 -> Material.ALLIUM;
            case 6 -> Material.BLUE_ORCHID;
            default -> Material.RED_TULIP;
        };
        world.getBlockAt(x, y, z).setType(flower);
    }

    private void placeTree(World world, int x, int y, int z, Random random) {
        int roll = random.nextInt(100);
        int height;
        Material log;
        Material leaves;
        if (roll < 60) {
            log = Material.OAK_LOG;
            leaves = Material.OAK_LEAVES;
            height = 5 + random.nextInt(3);
        } else if (roll < 85) {
            log = Material.BIRCH_LOG;
            leaves = Material.BIRCH_LEAVES;
            height = 5 + random.nextInt(3);
        } else {
            log = Material.SPRUCE_LOG;
            leaves = Material.SPRUCE_LEAVES;
            height = 6 + random.nextInt(3);
        }

        for (int i = 0; i < height + 3; i++) {
            if (!world.getBlockAt(x, y + i, z).isEmpty()) return;
        }
        for (int i = 0; i < height; i++) world.getBlockAt(x, y + i, z).setType(log);

        int top = y + height - 1;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    int d = Math.abs(dx) + Math.abs(dz);
                    if (d >= 4 && dy != 0) continue;
                    if (dy == 2 && d > 1) continue;
                    if (world.getBlockAt(x + dx, top + dy, z + dz).isEmpty()) {
                        world.getBlockAt(x + dx, top + dy, z + dz).setType(leaves);
                    }
                }
            }
        }
    }
}
