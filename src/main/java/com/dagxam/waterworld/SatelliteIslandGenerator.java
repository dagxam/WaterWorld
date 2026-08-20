package com.dagxam.waterworld;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.structure.Structure;

import java.util.Random;

/**
 * Создаёт пять полноценных островов вокруг главного.
 *
 * Каждый остров имеет цельный грунт, плавный подъём из океана,
 * песчаный берег, траву, цветы и деревья. Сухая часть использует PLAINS,
 * поэтому животные и враждебные мобы остаются под контролем ванильного
 * спавнера Minecraft.
 */
public final class SatelliteIslandGenerator {

    private final int seaLevel;
    private final int centerX;
    private final int centerZ;
    private final int count;
    private final int distance;
    private final int minRadius;
    private final int maxRadius;
    private final int slopeRadius;
    private final int islandHeight;

    public SatelliteIslandGenerator(int seaLevel, int centerX, int centerZ, int count,
                                    int distance, int minRadius, int maxRadius,
                                    int slopeRadius, int islandHeight) {
        this.seaLevel = seaLevel;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.count = Math.max(0, Math.min(5, count));
        this.distance = Math.max(70, distance);
        this.minRadius = Math.max(7, minRadius);
        this.maxRadius = Math.max(this.minRadius, maxRadius);
        this.slopeRadius = Math.max(this.maxRadius + 8, slopeRadius);
        this.islandHeight = Math.max(2, islandHeight);
    }

    public void generate(World world, int chunkX, int chunkZ) {
        if (count <= 0) return;

        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        for (int index = 0; index < count; index++) {
            Island island = getIsland(index);
            if (intersectsChunk(island, minX, minZ, maxX, maxZ)) {
                buildIsland(world, island, minX, minZ, maxX, maxZ);
            }
        }
    }

    private Island getIsland(int index) {
        double angle = Math.toRadians(-90.0D + index * (360.0D / 5.0D));
        int x = centerX + (int) Math.round(Math.cos(angle) * distance);
        int z = centerZ + (int) Math.round(Math.sin(angle) * distance);

        Random random = new Random(
                0x5DEECE66DL
                        ^ ((long) index * 341873128712L)
                        ^ ((long) centerX * 132897987541L)
                        ^ ((long) centerZ * 42317861L)
        );

        int radius = minRadius + random.nextInt(maxRadius - minRadius + 1);
        int height = islandHeight + random.nextInt(3);
        return new Island(x, z, radius, height, random.nextLong());
    }

    private boolean intersectsChunk(Island island, int minX, int minZ, int maxX, int maxZ) {
        int nearestX = clamp(island.x, minX, maxX);
        int nearestZ = clamp(island.z, minZ, maxZ);
        double dx = nearestX - island.x;
        double dz = nearestZ - island.z;
        return dx * dx + dz * dz <= (double) slopeRadius * slopeRadius;
    }

    private void buildIsland(World world, Island island, int minX, int minZ, int maxX, int maxZ) {
        int outer = Math.max(island.radius + 8, slopeRadius);
        int startX = Math.max(minX, island.x - outer);
        int endX = Math.min(maxX, island.x + outer);
        int startZ = Math.max(minZ, island.z - outer);
        int endZ = Math.min(maxZ, island.z + outer);

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                double dx = x - island.x;
                double dz = z - island.z;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > outer) continue;

                int surface = getSurface(island, x, z, d, outer);
                int bottom = Math.max(1, Math.min(surface - 7, 30));

                // Полностью цельный остров/шельф. Пустот внутри больше нет.
                for (int y = bottom; y <= surface; y++) {
                    Material block;

                    if (surface <= seaLevel) {
                        if (y >= surface - 2) {
                            block = Material.SAND;
                        } else if (y >= surface - 5) {
                            block = Material.SANDSTONE;
                        } else {
                            block = Material.STONE;
                        }
                    } else if (y == surface) {
                        // Песчаная полоса берега, трава внутри.
                        block = d >= island.radius - 2 ? Material.SAND : Material.GRASS_BLOCK;
                    } else if (y >= surface - 3) {
                        block = Material.DIRT;
                    } else {
                        block = Material.STONE;
                    }

                    world.getBlockAt(x, y, z).setType(block, false);
                }

                // Вода только там, где склон ниже уровня моря.
                if (surface < seaLevel) {
                    for (int y = surface + 1; y <= seaLevel; y++) {
                        world.getBlockAt(x, y, z).setType(Material.WATER, false);
                    }
                } else {
                    // На сухой части не должно оставаться воды.
                    for (int y = surface + 1; y <= seaLevel; y++) {
                        if (world.getBlockAt(x, y, z).getType() == Material.WATER) {
                            world.getBlockAt(x, y, z).setType(Material.AIR, false);
                        }
                    }
                }
            }
        }

        // Сухая часть — равнина. Ванильный mob spawning будет работать сам.
        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                double dx = x - island.x;
                double dz = z - island.z;
                double d2 = dx * dx + dz * dz;
                if (d2 <= (double) island.radius * island.radius
                        && world.getHighestBlockYAt(x, z) > seaLevel) {
                    world.setBiome(x, z, Biome.PLAINS);
                }
            }
        }

        decorateIsland(world, island, startX, startZ, endX, endZ);
    }

    private void decorateIsland(World world, Island island, int startX, int startZ, int endX, int endZ) {
        Random random = new Random(
                island.seed
                        ^ ((long) startX * 341873128712L)
                        ^ ((long) startZ * 132897987541L)
        );

        // Достаточно плотная растительность, но без заполнения каждого блока.
        int attempts = Math.max(12, island.radius * 2);
        for (int i = 0; i < attempts; i++) {
            int x = island.x - island.radius + random.nextInt(island.radius * 2 + 1);
            int z = island.z - island.radius + random.nextInt(island.radius * 2 + 1);

            if (!insideLand(island, x, z)) continue;

            int y = world.getHighestBlockYAt(x, z);
            if (world.getBlockAt(x, y, z).getType() != Material.GRASS_BLOCK) continue;
            if (!world.getBlockAt(x, y + 1, z).isEmpty()) continue;

            int roll = random.nextInt(100);
            if (roll < 14) {
                placeTree(world, x, y + 1, z, random);
            } else if (roll < 55) {
                placeFlower(world, x, y + 1, z, random);
            } else {
                world.getBlockAt(x, y + 1, z).setType(Material.GRASS, false);
            }
        }
    }

    private boolean insideLand(Island island, int x, int z) {
        double dx = x - island.x;
        double dz = z - island.z;
        return dx * dx + dz * dz <= Math.pow(Math.max(2, island.radius - 3), 2);
    }

    private void placeFlower(World world, int x, int y, int z, Random random) {
        Material flower;
        switch (random.nextInt(6)) {
            case 0: flower = Material.DANDELION; break;
            case 1: flower = Material.POPPY; break;
            case 2: flower = Material.AZURE_BLUET; break;
            case 3: flower = Material.OXEYE_DAISY; break;
            case 4: flower = Material.CORNFLOWER; break;
            default: flower = Material.ALLIUM; break;
        }
        if (world.getBlockAt(x, y, z).isEmpty()) {
            world.getBlockAt(x, y, z).setType(flower, false);
        }
    }

    private void placeTree(World world, int x, int y, int z, Random random) {
        // Маленькие дубы, похожие на обычные равнинные деревья.
        int height = 4 + random.nextInt(3);

        for (int i = 0; i < height + 3; i++) {
            if (!world.getBlockAt(x, y + i, z).isEmpty()) return;
        }

        for (int i = 0; i < height; i++) {
            world.getBlockAt(x, y + i, z).setType(Material.OAK_LOG, false);
        }

        int top = y + height - 1;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    int distance = Math.abs(dx) + Math.abs(dz);
                    if (distance >= 4 && dy != 0) continue;
                    if (dy == 2 && distance > 1) continue;

                    int bx = x + dx;
                    int by = top + dy;
                    int bz = z + dz;
                    if (world.getBlockAt(bx, by, bz).isEmpty()) {
                        world.getBlockAt(bx, by, bz).setType(Material.OAK_LEAVES, false);
                    }
                }
            }
        }
    }

    private int getSurface(Island island, int x, int z, double d, int outer) {
        double noise = island.noise(x, z);

        if (d <= island.radius) {
            // Плавный подъём от береговой линии к центру.
            double factor = 1.0D - d / island.radius;
            double smooth = factor * factor * (3.0D - 2.0D * factor);
            return seaLevel + 1 + (int) Math.round(smooth * island.height + noise * 1.4D);
        }

        // Плавный спуск под воду от песчаного берега до глубины океана.
        double t = (d - island.radius) / (outer - island.radius);
        t = Math.max(0.0D, Math.min(1.0D, t));
        double smooth = t * t * (3.0D - 2.0D * t);
        double edge = seaLevel - 1.0D;
        double floor = 35.0D + noise * 5.0D;
        return (int) Math.round(edge + (floor - edge) * smooth);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Island {
        private final int x, z, radius, height;
        private final long seed;

        private Island(int x, int z, int radius, int height, long seed) {
            this.x = x;
            this.z = z;
            this.radius = radius;
            this.height = height;
            this.seed = seed;
        }

        private double noise(int x, int z) {
            long n = seed ^ ((long) x * 341873128712L) ^ ((long) z * 132897987541L);
            n ^= (n >>> 33);
            n *= 0xff51afd7ed558ccdl;
            n ^= (n >>> 33);
            n *= 0xc4ceb9fe1a85ec53l;
            n ^= (n >>> 33);
            return ((n & 0xFFFFL) / 32767.5D) - 1.0D;
        }
    }
}
