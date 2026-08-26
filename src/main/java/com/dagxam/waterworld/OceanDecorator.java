package com.dagxam.waterworld;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

/** Живое дно океана: растения, коралловые рифы, руины, корабли и сокровища. */
public final class OceanDecorator {
    private final JavaPlugin plugin;
    private final int seaLevel;
    private final int islandRadius;
    private final int oceanRadius;
    private final NamespacedKey decoratedKey;

    public OceanDecorator(JavaPlugin plugin, int seaLevel, int islandRadius, int oceanRadius) {
        this.plugin = plugin;
        this.seaLevel = seaLevel;
        this.islandRadius = islandRadius;
        this.oceanRadius = oceanRadius;
        this.decoratedKey = new NamespacedKey(plugin, "ocean-decor-v1");
    }

    public void decorateOnce(Chunk chunk) {
        if (chunk.getPersistentDataContainer().has(decoratedKey, PersistentDataType.BYTE)) return;
        World world = chunk.getWorld();
        if (!world.getName().equals(plugin.getServer().getWorlds().get(0).getName()) && !(world.getGenerator() instanceof WaterGenerator)) return;

        long seed = world.getSeed()
                ^ ((long) chunk.getX() * 341873128712L)
                ^ ((long) chunk.getZ() * 132897987541L)
                ^ 0x4F4345414E444543L;
        Random random = new Random(seed);
        int cx = chunk.getX() * 16 + 8;
        int cz = chunk.getZ() * 16 + 8;
        if (!isOcean(cx, cz)) {
            chunk.getPersistentDataContainer().set(decoratedKey, PersistentDataType.BYTE, (byte) 1);
            return;
        }

        // Основная жизнь дна: трава, ламинария, морские огурцы и небольшие коралловые группы.
        for (int i = 0; i < 20; i++) placePlant(world, chunk, random);
        if (random.nextInt(100) < 34) placeCoralReef(world, chunk, random);
        if (random.nextInt(100) < 7) placeOceanRuin(world, chunk, random);
        if (random.nextInt(100) < 3) placeShipwreck(world, chunk, random);

        chunk.getPersistentDataContainer().set(decoratedKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void placePlant(World world, Chunk chunk, Random random) {
        int x = chunk.getX() * 16 + 1 + random.nextInt(14);
        int z = chunk.getZ() * 16 + 1 + random.nextInt(14);
        int y = world.getHighestBlockYAt(x, z);
        Block floor = world.getBlockAt(x, y, z);
        Block water = world.getBlockAt(x, y + 1, z);
        if (!isOcean(x, z) || y >= seaLevel || !floor.getType().isSolid() || water.getType() != Material.WATER) return;

        int roll = random.nextInt(100);
        if (roll < 48) {
            water.setType(Material.SEAGRASS);
        } else if (roll < 76) {
            int height = 2 + random.nextInt(5);
            for (int dy = 1; dy <= height && y + dy < seaLevel; dy++) {
                Block block = world.getBlockAt(x, y + dy, z);
                if (block.getType() != Material.WATER) break;
                block.setType(Material.KELP);
            }
        } else if (roll < 90) {
            water.setType(Material.SEA_PICKLE);
        } else {
            Material coral = coralPlant(random);
            water.setType(coral);
        }
    }

    private void placeCoralReef(World world, Chunk chunk, Random random) {
        int baseX = chunk.getX() * 16 + 3 + random.nextInt(10);
        int baseZ = chunk.getZ() * 16 + 3 + random.nextInt(10);
        int baseY = world.getHighestBlockYAt(baseX, baseZ);
        if (baseY >= seaLevel - 2 || !isOcean(baseX, baseZ)) return;

        Material blockCoral = coralBlock(random);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5 || random.nextInt(100) < 25) continue;
                int x = baseX + dx, z = baseZ + dz;
                if (!insideChunk(chunk, x, z) || !isOcean(x, z)) continue;
                int y = world.getHighestBlockYAt(x, z);
                if (y >= seaLevel - 2) continue;
                Block top = world.getBlockAt(x, y + 1, z);
                if (top.getType() != Material.WATER) continue;
                top.setType(blockCoral);
                if (random.nextBoolean() && y + 2 < seaLevel && world.getBlockAt(x, y + 2, z).getType() == Material.WATER) {
                    world.getBlockAt(x, y + 2, z).setType(coralPlant(random));
                }
            }
        }
    }

    private void placeOceanRuin(World world, Chunk chunk, Random random) {
        int x = chunk.getX() * 16 + 4 + random.nextInt(8);
        int z = chunk.getZ() * 16 + 4 + random.nextInt(8);
        int y = world.getHighestBlockYAt(x, z) + 1;
        if (y >= seaLevel - 3) return;
        Material stone = random.nextBoolean() ? Material.PRISMARINE : Material.MOSSY_STONE_BRICKS;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!insideChunk(chunk, x + dx, z + dz) || Math.abs(dx) == 2 && Math.abs(dz) == 2 || random.nextInt(100) < 28) continue;
                int py = world.getHighestBlockYAt(x + dx, z + dz) + 1;
                if (py >= seaLevel - 1 || world.getBlockAt(x + dx, py, z + dz).getType() != Material.WATER) continue;
                world.getBlockAt(x + dx, py, z + dz).setType(stone);
                if (random.nextInt(100) < 35 && py + 1 < seaLevel && world.getBlockAt(x + dx, py + 1, z + dz).getType() == Material.WATER) {
                    world.getBlockAt(x + dx, py + 1, z + dz).setType(Material.MOSSY_STONE_BRICK_WALL);
                }
            }
        }
        if (random.nextBoolean()) placeTreasureBarrel(world, x, y, z, random);
    }

    private void placeShipwreck(World world, Chunk chunk, Random random) {
        int x = chunk.getX() * 16 + 2 + random.nextInt(4);
        int z = chunk.getZ() * 16 + 5 + random.nextInt(5);
        int y = world.getHighestBlockYAt(x + 4, z) + 1;
        if (y >= seaLevel - 4) return;

        // Небольшой утонувший корпус полностью внутри одного чанка.
        for (int dx = 0; dx < 10; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dz) == 1 && (dx == 0 || dx == 9)) continue;
                int px = x + dx, pz = z + dz;
                if (!insideChunk(chunk, px, pz)) continue;
                int py = world.getHighestBlockYAt(px, pz) + 1;
                if (py >= seaLevel - 1 || world.getBlockAt(px, py, pz).getType() != Material.WATER) continue;
                world.getBlockAt(px, py, pz).setType(Material.DARK_OAK_PLANKS);
                if (random.nextInt(100) < 65 && py + 1 < seaLevel && world.getBlockAt(px, py + 1, pz).getType() == Material.WATER) {
                    world.getBlockAt(px, py + 1, pz).setType(Material.DARK_OAK_FENCE);
                }
            }
        }
        if (random.nextBoolean()) placeTreasureBarrel(world, x + 4, y, z, random);
    }

    private void placeTreasureBarrel(World world, int x, int suggestedY, int z, Random random) {
        int y = Math.max(suggestedY, world.getHighestBlockYAt(x, z) + 1);
        if (y >= seaLevel || world.getBlockAt(x, y, z).getType() != Material.WATER) return;
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.BARREL);
        if (block.getState() instanceof Barrel barrel) {
            if (random.nextInt(100) < 80) barrel.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 2 + random.nextInt(5)));
            if (random.nextInt(100) < 55) barrel.getInventory().addItem(new ItemStack(Material.EMERALD, 1 + random.nextInt(4)));
            if (random.nextInt(100) < 30) barrel.getInventory().addItem(new ItemStack(Material.DIAMOND, 1 + random.nextInt(2)));
            if (random.nextBoolean()) barrel.getInventory().addItem(new ItemStack(Material.MAP));
            barrel.update();
        }
    }

    private boolean isOcean(int x, int z) {
        double d = Math.sqrt((double) x * x + (double) z * z);
        return d >= islandRadius + 24 && d <= oceanRadius;
    }

    private boolean insideChunk(Chunk chunk, int x, int z) {
        int minX = chunk.getX() * 16, minZ = chunk.getZ() * 16;
        return x >= minX && x < minX + 16 && z >= minZ && z < minZ + 16;
    }

    private Material coralBlock(Random random) {
        return switch (random.nextInt(5)) {
            case 0 -> Material.TUBE_CORAL_BLOCK;
            case 1 -> Material.BRAIN_CORAL_BLOCK;
            case 2 -> Material.BUBBLE_CORAL_BLOCK;
            case 3 -> Material.FIRE_CORAL_BLOCK;
            default -> Material.HORN_CORAL_BLOCK;
        };
    }

    private Material coralPlant(Random random) {
        return switch (random.nextInt(5)) {
            case 0 -> Material.TUBE_CORAL;
            case 1 -> Material.BRAIN_CORAL;
            case 2 -> Material.BUBBLE_CORAL;
            case 3 -> Material.FIRE_CORAL;
            default -> Material.HORN_CORAL;
        };
    }
}
