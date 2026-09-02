package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Random;

/** Разная естественная флора для главного и малых островов. */
public final class NaturalIslandDecorator {
    private final int seaLevel;
    private final IslandLayout layout;

    public NaturalIslandDecorator(int seaLevel, IslandLayout layout) {
        this.seaLevel = seaLevel;
        this.layout = layout;
        JavaPlugin providing = JavaPlugin.getProvidingPlugin(NaturalIslandDecorator.class);
        if (providing instanceof WaterWorldPlugin plugin) {
            RestoreManager restoreManager = new RestoreManager(plugin);
            plugin.getServer().getPluginManager().registerEvents(restoreManager, plugin);
            if (plugin.getCommand("wwrestore") != null) {
                plugin.getCommand("wwrestore").setExecutor(restoreManager);
                plugin.getCommand("wwrestore").setTabCompleter(restoreManager);
            }
        }
    }

    public void decorate(World world, int chunkX, int chunkZ) {
        List<IslandLayout.Island> islands = layout.get(world.getSeed());
        Random random = new Random(world.getSeed()
                ^ ((long) chunkX * 341873128712L)
                ^ ((long) chunkZ * 132897987541L)
                ^ 0x6A09E667F3BCC909L);

        for (int i = 0; i < 24; i++) {
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
            placeFlora(world, x, y + 1, z, island.flora(), random);
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

    private void placeFlora(World world, int x, int y, int z, String flora, Random random) {
        if (!world.getBlockAt(x, y, z).isEmpty()) return;
        switch (flora) {
            case "jungle" -> {
                if (random.nextInt(100) < 28) placeTree(world, x, y, z, random, Material.JUNGLE_LOG, Material.JUNGLE_LEAVES, 7, 10);
                else if (random.nextInt(100) < 55) placeFlower(world, x, y, z, random, Material.LILY_PAD, Material.FERN, Material.LARGE_FERN);
                else placeGrass(world, x, y, z, Material.TALL_GRASS);
            }
            case "birch" -> {
                if (random.nextInt(100) < 30) placeTree(world, x, y, z, random, Material.BIRCH_LOG, Material.BIRCH_LEAVES, 5, 8);
                else if (random.nextBoolean()) placeFlower(world, x, y, z, random, Material.AZURE_BLUET, Material.OXEYE_DAISY, Material.CORNFLOWER);
                else placeGrass(world, x, y, z, Material.SHORT_GRASS);
            }
            case "taiga" -> {
                if (random.nextInt(100) < 34) placeTree(world, x, y, z, random, Material.SPRUCE_LOG, Material.SPRUCE_LEAVES, 6, 9);
                else placeGrass(world, x, y, z, random.nextBoolean() ? Material.FERN : Material.SWEET_BERRY_BUSH);
            }
            case "savanna" -> {
                if (random.nextInt(100) < 12) placeTree(world, x, y, z, random, Material.ACACIA_LOG, Material.ACACIA_LEAVES, 5, 7);
                else placeGrass(world, x, y, z, Material.TALL_GRASS);
            }
            case "flower" -> {
                Material[] flowers = {Material.POPPY, Material.DANDELION, Material.ALLIUM, Material.BLUE_ORCHID, Material.AZURE_BLUET, Material.OXEYE_DAISY, Material.CORNFLOWER, Material.RED_TULIP, Material.PINK_TULIP};
                if (random.nextInt(100) < 72) placeGrass(world, x, y, z, flowers[random.nextInt(flowers.length)]);
                else placeGrass(world, x, y, z, Material.SHORT_GRASS);
            }
            case "swamp" -> {
                if (random.nextInt(100) < 18) placeTree(world, x, y, z, random, Material.OAK_LOG, Material.OAK_LEAVES, 4, 6);
                else if (random.nextInt(100) < 25) placeGrass(world, x, y, z, Material.SUGAR_CANE);
                else placeGrass(world, x, y, z, Material.FERN);
            }
            case "dark_forest" -> {
                if (random.nextInt(100) < 42) placeTree(world, x, y, z, random, Material.DARK_OAK_LOG, Material.DARK_OAK_LEAVES, 5, 8);
                else placeGrass(world, x, y, z, Material.FERN);
            }
            case "mushroom" -> {
                if (random.nextInt(100) < 12) placeGrass(world, x, y, z, random.nextBoolean() ? Material.RED_MUSHROOM : Material.BROWN_MUSHROOM);
                else placeGrass(world, x, y, z, Material.SHORT_GRASS);
            }
            default -> {
                if (random.nextInt(100) < 18) placeTree(world, x, y, z, random, Material.OAK_LOG, Material.OAK_LEAVES, 5, 8);
                else if (random.nextInt(100) < 58) placeFlower(world, x, y, z, random, Material.DANDELION, Material.POPPY, Material.AZURE_BLUET);
                else placeGrass(world, x, y, z, Material.SHORT_GRASS);
            }
        }
    }

    private void placeGrass(World world, int x, int y, int z, Material material) {
        if (world.getBlockAt(x, y, z).isEmpty()) world.getBlockAt(x, y, z).setType(material);
    }

    private void placeFlower(World world, int x, int y, int z, Random random, Material... choices) {
        placeGrass(world, x, y, z, choices[random.nextInt(choices.length)]);
    }

    private void placeTree(World world, int x, int y, int z, Random random, Material log, Material leaves, int minHeight, int maxHeight) {
        int height = minHeight + random.nextInt(maxHeight - minHeight + 1);
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
