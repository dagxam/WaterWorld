package com.dagxam.waterworld;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

/**
 * Отдельная система живого океанского дна.
 * Важно: декорирование выполняется только после готовой генерации чанка и помечается версией v2,
 * поэтому старые пустые чанки могут быть заполнены заново после обновления плагина.
 */
public final class OceanDecorator {
    private final JavaPlugin plugin;
    private final int seaLevel;
    private final int centerX;
    private final int centerZ;
    private final int islandRadius;
    private final int oceanRadius;
    private final int plantAttempts;
    private final int reefChance;
    private final int ruinChance;
    private final int shipwreckChance;
    private final NamespacedKey decoratedKey;

    public OceanDecorator(JavaPlugin plugin, int seaLevel, int centerX, int centerZ,
                          int islandRadius, int oceanRadius, FileConfiguration config) {
        this.plugin = plugin;
        this.seaLevel = seaLevel;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.islandRadius = islandRadius;
        this.oceanRadius = oceanRadius;
        this.plantAttempts = Math.max(12, config.getInt("ocean-life.plant-attempts-per-chunk", 44));
        this.reefChance = clampPercent(config.getInt("ocean-life.reef-chance-percent", 42));
        this.ruinChance = clampPercent(config.getInt("ocean-life.ruin-chance-percent", 9));
        this.shipwreckChance = clampPercent(config.getInt("ocean-life.shipwreck-chance-percent", 4));
        this.decoratedKey = new NamespacedKey(plugin, "ocean-decor-v2");
    }

    public void decorateOnce(Chunk chunk) {
        if (chunk.getPersistentDataContainer().has(decoratedKey, PersistentDataType.BYTE)) return;
        World world = chunk.getWorld();
        if (!(world.getGenerator() instanceof WaterGenerator)) return;

        int cx = chunk.getX() * 16 + 8;
        int cz = chunk.getZ() * 16 + 8;
        if (!isOcean(cx, cz)) return;

        long seed = world.getSeed()
                ^ ((long) chunk.getX() * 341873128712L)
                ^ ((long) chunk.getZ() * 132897987541L)
                ^ 0x4F4345414E444543L;
        Random random = new Random(seed);

        int placed = 0;
        for (int i = 0; i < plantAttempts; i++) placed += placePlant(world, chunk, random) ? 1 : 0;
        if (random.nextInt(100) < reefChance) placed += placeCoralReef(world, chunk, random);
        if (random.nextInt(100) < ruinChance) placed += placeOceanRuin(world, chunk, random);
        if (random.nextInt(100) < shipwreckChance) placed += placeShipwreck(world, chunk, random);

        // Чанк отмечаем только после попыток декорирования на уже готовом дне.
        // Даже если конкретный случайно выбранный объект не подошёл, обычная растительность уже была обработана.
        chunk.getPersistentDataContainer().set(decoratedKey, PersistentDataType.BYTE, (byte) 1);
    }

    private boolean placePlant(World world, Chunk chunk, Random random) {
        int x = chunk.getX() * 16 + 1 + random.nextInt(14);
        int z = chunk.getZ() * 16 + 1 + random.nextInt(14);
        int floorY = findOceanFloor(world, x, z);
        if (floorY < world.getMinHeight() + 1 || !isOcean(x, z)) return false;

        Block water = world.getBlockAt(x, floorY + 1, z);
        if (water.getType() != Material.WATER) return false;

        int roll = random.nextInt(100);
        if (roll < 40) {
            water.setType(Material.SEAGRASS);
        } else if (roll < 70) {
            int height = 2 + random.nextInt(7);
            for (int dy = 1; dy <= height && floorY + dy < seaLevel; dy++) {
                Block block = world.getBlockAt(x, floorY + dy, z);
                if (block.getType() != Material.WATER) break;
                block.setType(Material.KELP);
            }
        } else if (roll < 82) {
            water.setType(Material.SEA_PICKLE);
        } else if (roll < 92) {
            water.setType(coralPlant(random));
        } else {
            // Небольшие одиночные коралловые образования между растениями.
            water.setType(coralBlock(random));
            if (floorY + 2 < seaLevel && world.getBlockAt(x, floorY + 2, z).getType() == Material.WATER) {
                world.getBlockAt(x, floorY + 2, z).setType(coralPlant(random));
            }
        }
        return true;
    }

    private int placeCoralReef(World world, Chunk chunk, Random random) {
        int baseX = chunk.getX() * 16 + 4 + random.nextInt(8);
        int baseZ = chunk.getZ() * 16 + 4 + random.nextInt(8);
        int baseY = findOceanFloor(world, baseX, baseZ);
        if (baseY < world.getMinHeight() + 1 || baseY >= seaLevel - 4 || !isOcean(baseX, baseZ)) return 0;

        int placed = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > 9 || random.nextInt(100) < 18) continue;
                int x = baseX + dx;
                int z = baseZ + dz;
                if (!insideChunk(chunk, x, z) || !isOcean(x, z)) continue;
                int y = findOceanFloor(world, x, z);
                if (y < world.getMinHeight() + 1 || y >= seaLevel - 3) continue;
                if (Math.abs(y - baseY) > 4) continue;

                Block coralBase = world.getBlockAt(x, y + 1, z);
                if (coralBase.getType() != Material.WATER) continue;
                coralBase.setType(coralBlock(random));
                placed++;

                if (random.nextInt(100) < 70 && y + 2 < seaLevel
                        && world.getBlockAt(x, y + 2, z).getType() == Material.WATER) {
                    world.getBlockAt(x, y + 2, z).setType(coralPlant(random));
                }
                if (random.nextInt(100) < 28 && y + 2 < seaLevel
                        && world.getBlockAt(x + (random.nextBoolean() ? 1 : -1), y + 1, z).getType() == Material.WATER) {
                    // Небольшая цветная ветка рифа, но только внутри чанка.
                    int bx = x + (random.nextBoolean() ? 1 : -1);
                    if (insideChunk(chunk, bx, z)) world.getBlockAt(bx, y + 1, z).setType(coralPlant(random));
                }
            }
        }
        return placed;
    }

    private int placeOceanRuin(World world, Chunk chunk, Random random) {
        int x = chunk.getX() * 16 + 5 + random.nextInt(6);
        int z = chunk.getZ() * 16 + 5 + random.nextInt(6);
        int y = findOceanFloor(world, x, z);
        if (y < world.getMinHeight() + 1 || y >= seaLevel - 4) return 0;

        Material[] stones = {Material.PRISMARINE, Material.PRISMARINE_BRICKS, Material.MOSSY_STONE_BRICKS};
        int placed = 0;
        // Разрушенный фундамент.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                if (random.nextInt(100) < 20) continue;
                int px = x + dx, pz = z + dz;
                if (!insideChunk(chunk, px, pz)) continue;
                int py = findOceanFloor(world, px, pz);
                if (py < world.getMinHeight() + 1 || py >= seaLevel - 2) continue;
                Block target = world.getBlockAt(px, py + 1, pz);
                if (target.getType() != Material.WATER) continue;
                target.setType(stones[random.nextInt(stones.length)]);
                placed++;
            }
        }
        // Несколько сломанных колонн.
        for (int i = 0; i < 3; i++) {
            int px = x + (random.nextBoolean() ? 2 : -2);
            int pz = z - 1 + random.nextInt(3);
            if (!insideChunk(chunk, px, pz)) continue;
            int py = findOceanFloor(world, px, pz);
            int height = 1 + random.nextInt(3);
            for (int dy = 1; dy <= height && py + dy < seaLevel; dy++) {
                Block target = world.getBlockAt(px, py + dy, pz);
                if (target.getType() != Material.WATER) break;
                target.setType(dy == 1 ? Material.PRISMARINE_BRICKS : Material.MOSSY_STONE_BRICK_WALL);
                placed++;
            }
        }
        if (placed > 4 && random.nextInt(100) < 75) placeTreasureBarrel(world, x, z, random);
        return placed;
    }

    private int placeShipwreck(World world, Chunk chunk, Random random) {
        boolean alongX = random.nextBoolean();
        int x = chunk.getX() * 16 + 3 + random.nextInt(5);
        int z = chunk.getZ() * 16 + 3 + random.nextInt(5);
        int y = findOceanFloor(world, x + (alongX ? 4 : 0), z + (alongX ? 0 : 4));
        if (y < world.getMinHeight() + 1 || y >= seaLevel - 5) return 0;

        int placed = 0;
        for (int length = 0; length < 8; length++) {
            for (int width = -1; width <= 1; width++) {
                int px = alongX ? x + length : x + width;
                int pz = alongX ? z + width : z + length;
                if (!insideChunk(chunk, px, pz)) continue;
                int py = findOceanFloor(world, px, pz);
                if (py < world.getMinHeight() + 1 || py >= seaLevel - 2 || Math.abs(py - y) > 3) continue;
                Block target = world.getBlockAt(px, py + 1, pz);
                if (target.getType() != Material.WATER) continue;
                target.setType((length == 0 || length == 7) && width == 0 ? Material.DARK_OAK_LOG : Material.DARK_OAK_PLANKS);
                placed++;
                if (width == 0 && random.nextInt(100) < 45 && py + 2 < seaLevel
                        && world.getBlockAt(px, py + 2, pz).getType() == Material.WATER) {
                    world.getBlockAt(px, py + 2, pz).setType(Material.DARK_OAK_FENCE);
                    placed++;
                }
            }
        }
        if (placed >= 8) {
            int bx = alongX ? x + 4 : x;
            int bz = alongX ? z : z + 4;
            placeTreasureBarrel(world, bx, bz, random);
        }
        return placed;
    }

    private void placeTreasureBarrel(World world, int x, int z, Random random) {
        int floorY = findOceanFloor(world, x, z);
        if (floorY < world.getMinHeight() + 1 || floorY + 1 >= seaLevel) return;
        Block block = world.getBlockAt(x, floorY + 1, z);
        if (block.getType() != Material.WATER) return;

        block.setType(Material.BARREL);
        if (block.getState() instanceof Barrel barrel) {
            if (random.nextInt(100) < 88) barrel.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 2 + random.nextInt(6)));
            if (random.nextInt(100) < 68) barrel.getInventory().addItem(new ItemStack(Material.EMERALD, 1 + random.nextInt(5)));
            if (random.nextInt(100) < 38) barrel.getInventory().addItem(new ItemStack(Material.DIAMOND, 1 + random.nextInt(3)));
            if (random.nextInt(100) < 55) barrel.getInventory().addItem(new ItemStack(Material.FILLED_MAP));
            if (random.nextInt(100) < 25) barrel.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE));
            barrel.update();
        }
    }

    /** Находит верхний твёрдый блок именно под толщей воды, а не верх растения/коралла. */
    private int findOceanFloor(World world, int x, int z) {
        int start = Math.min(seaLevel - 1, world.getMaxHeight() - 2);
        for (int y = start; y > world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            if (!block.getType().isSolid()) continue;
            if (world.getBlockAt(x, y + 1, z).getType() == Material.WATER) return y;
        }
        return world.getMinHeight();
    }

    private boolean isOcean(int x, int z) {
        double dx = x - centerX;
        double dz = z - centerZ;
        double d = Math.sqrt(dx * dx + dz * dz);
        return d >= islandRadius + 18 && d <= oceanRadius;
    }

    private boolean insideChunk(Chunk chunk, int x, int z) {
        int minX = chunk.getX() * 16;
        int minZ = chunk.getZ() * 16;
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

    private static int clampPercent(int value) { return Math.max(0, Math.min(100, value)); }
}
