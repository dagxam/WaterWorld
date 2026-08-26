package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Random;

/** Lightweight deterministic vegetation. Only runs for chunks intersecting an island. */
public final class NaturalIslandDecorator {
    private final int seaLevel, attempts, treeChance, flowerChance;
    private final IslandLayout layout;

    public NaturalIslandDecorator(FileConfiguration config, IslandLayout layout) {
        this.seaLevel = config.getInt("sea-level", 63);
        this.attempts = Math.max(0, config.getInt("vegetation.attempts-per-chunk", 10));
        this.treeChance = clamp(config.getInt("vegetation.tree-chance-percent", 18), 0, 100);
        this.flowerChance = clamp(config.getInt("vegetation.flower-chance-percent", 32), 0, 100 - treeChance);
        this.layout = layout;
    }

    public boolean affectsChunk(int chunkX, int chunkZ, long seed) {
        double x = chunkX * 16.0D + 8.0D, z = chunkZ * 16.0D + 8.0D;
        for (IslandLayout.Island island : layout.get(seed)) {
            double max = island.radius() + 16.0D;
            double dx = x - island.x(), dz = z - island.z();
            if (dx * dx + dz * dz <= max * max) return true;
        }
        return false;
    }

    public void decorate(World world, int chunkX, int chunkZ) {
        if (attempts == 0 || !affectsChunk(chunkX, chunkZ, world.getSeed())) return;
        Random random = new Random(world.getSeed() ^ ((long) chunkX * 341873128712L) ^ ((long) chunkZ * 132897987541L));
        List<IslandLayout.Island> islands = layout.get(world.getSeed());
        for (int i = 0; i < attempts; i++) {
            int x = chunkX * 16 + random.nextInt(16), z = chunkZ * 16 + random.nextInt(16);
            IslandLayout.Island island = containingIsland(islands, x, z);
            if (island == null) continue;
            int y = world.getHighestBlockYAt(x, z);
            if (y <= seaLevel || world.getBlockAt(x, y, z).getType() != Material.GRASS_BLOCK) continue;
            int roll = random.nextInt(100);
            if (roll < treeChance) placeTree(world, x, y + 1, z, random, island.type());
            else if (roll < treeChance + flowerChance) placeFlower(world, x, y + 1, z, random);
            else if (world.getBlockAt(x, y + 1, z).isEmpty()) world.getBlockAt(x, y + 1, z).setType(Material.SHORT_GRASS, false);
        }
    }

    private IslandLayout.Island containingIsland(List<IslandLayout.Island> islands, int x, int z) {
        IslandLayout.Island best = null; long bestSq = Long.MAX_VALUE;
        for (IslandLayout.Island island : islands) {
            long dx = (long) x - island.x(), dz = (long) z - island.z();
            long sq = dx * dx + dz * dz, inner = Math.max(1L, (long) island.radius() - 7L);
            if (sq <= inner * inner && sq < bestSq) { bestSq = sq; best = island; }
        }
        return best;
    }

    private void placeFlower(World world, int x, int y, int z, Random random) {
        if (!world.getBlockAt(x, y, z).isEmpty()) return;
        Material flower = switch (random.nextInt(8)) {
            case 0 -> Material.DANDELION; case 1 -> Material.POPPY; case 2 -> Material.AZURE_BLUET; case 3 -> Material.OXEYE_DAISY;
            case 4 -> Material.CORNFLOWER; case 5 -> Material.ALLIUM; case 6 -> Material.BLUE_ORCHID; default -> Material.RED_TULIP;
        };
        world.getBlockAt(x, y, z).setType(flower, false);
    }

    private void placeTree(World world, int x, int y, int z, Random random, IslandLayout.IslandType islandType) {
        Material log = islandType == IslandLayout.IslandType.FOREST && random.nextBoolean() ? Material.BIRCH_LOG :
                islandType == IslandLayout.IslandType.TROPICAL ? Material.JUNGLE_LOG : Material.OAK_LOG;
        Material leaves = log == Material.BIRCH_LOG ? Material.BIRCH_LEAVES : log == Material.JUNGLE_LOG ? Material.JUNGLE_LEAVES : Material.OAK_LEAVES;
        int height = 5 + random.nextInt(3);
        for (int i = 0; i < height + 3; i++) if (!world.getBlockAt(x, y + i, z).isEmpty()) return;
        for (int i = 0; i < height; i++) world.getBlockAt(x, y + i, z).setType(log, false);
        int top = y + height - 1;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -1; dy <= 2; dy++) {
            int d = Math.abs(dx) + Math.abs(dz);
            if (d >= 4 && dy != 0) continue;
            if (dy == 2 && d > 1) continue;
            if (world.getBlockAt(x + dx, top + dy, z + dz).isEmpty()) world.getBlockAt(x + dx, top + dy, z + dz).setType(leaves, false);
        }
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
