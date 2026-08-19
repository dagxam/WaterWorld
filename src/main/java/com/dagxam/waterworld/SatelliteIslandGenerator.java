package com.dagxam.waterworld;

import org.bukkit.Biome;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Random;

/**
 * Создаёт пять небольших островов вокруг главного острова.
 *
 * Острова находятся достаточно близко, чтобы их было видно с центрального
 * острова при обычной дистанции прорисовки, но между ними остаётся океан.
 * Они строятся после базовой генерации чанка, поэтому не требуют изменения
 * основного WaterGenerator.
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

    public SatelliteIslandGenerator(
            int seaLevel,
            int centerX,
            int centerZ,
            int count,
            int distance,
            int minRadius,
            int maxRadius,
            int slopeRadius,
            int islandHeight
    ) {
        this.seaLevel = seaLevel;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.count = Math.max(0, Math.min(5, count));
        this.distance = Math.max(70, distance);
        this.minRadius = Math.max(7, minRadius);
        this.maxRadius = Math.max(this.minRadius, maxRadius);
        this.slopeRadius = Math.max(this.maxRadius + 4, slopeRadius);
        this.islandHeight = Math.max(2, islandHeight);
    }

    public void generate(World world, int chunkX, int chunkZ) {
        if (count <= 0) {
            return;
        }

        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        for (int index = 0; index < count; index++) {
            Island island = getIsland(index);

            if (!intersectsChunk(island, minX, minZ, maxX, maxZ)) {
                continue;
            }

            buildIsland(world, island, minX, minZ, maxX, maxZ);
        }
    }

    private Island getIsland(int index) {
        // Равномерное кольцо из пяти островов вокруг главного.
        // Первый остров находится на севере, остальные распределены по кругу.
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

        return dx * dx + dz * dz <= (double) island.slopeRadius() * island.slopeRadius();
    }

    private void buildIsland(
            World world,
            Island island,
            int minX,
            int minZ,
            int maxX,
            int maxZ
    ) {
        int startX = Math.max(minX, island.x - island.slopeRadius());
        int endX = Math.min(maxX, island.x + island.slopeRadius());
        int startZ = Math.max(minZ, island.z - island.slopeRadius());
        int endZ = Math.min(maxZ, island.z + island.slopeRadius());

        Random random = new Random(island.seed);

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                double dx = x - island.x;
                double dz = z - island.z;
                double distanceFromCenter = Math.sqrt(dx * dx + dz * dz);

                if (distanceFromCenter > island.slopeRadius()) {
                    continue;
                }

                int surface = getSurface(island, x, z, distanceFromCenter);

                // Небольшие острова не должны подниматься выше главного.
                if (surface <= seaLevel) {
                    // Это подводный склон: создаём камень/песок поверх океана.
                    for (int y = Math.max(1, surface - 4); y <= surface; y++) {
                        Material block = y >= surface - 1
                                ? Material.SAND
                                : Material.STONE;
                        world.getBlockAt(x, y, z).setType(block, false);
                    }
                    continue;
                }

                // Основной массив острова.
                int bottom = Math.max(1, surface - 7);
                for (int y = bottom; y <= surface; y++) {
                    Material block;

                    if (y == surface) {
                        block = distanceFromCenter >= island.radius - 2
                                ? Material.SAND
                                : Material.GRASS_BLOCK;
                    } else if (y >= surface - 3) {
                        block = Material.DIRT;
                    } else {
                        block = Material.STONE;
                    }

                    world.getBlockAt(x, y, z).setType(block, false);
                }

                // Очищаем пространство над островом, если там осталась вода.
                for (int y = surface + 1; y <= seaLevel; y++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.WATER) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }

                // Небольшая неровность, но без случайного обрыва берега.
                if (random.nextInt(100) < 4 && surface > seaLevel + 1) {
                    world.setBiome(x, z, Biome.PLAINS);
                }
            }
        }

        // Биом задаём на всей сухой части после формирования блоков.
        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                double dx = x - island.x;
                double dz = z - island.z;
                if (dx * dx + dz * dz <= (double) island.radius * island.radius) {
                    world.setBiome(x, z, Biome.PLAINS);
                }
            }
        }
    }

    private int getSurface(Island island, int x, int z, double distanceFromCenter) {
        double noise = island.noise(x, z);

        if (distanceFromCenter <= island.radius) {
            double factor = 1.0D - distanceFromCenter / island.radius;
            return seaLevel + 1 + (int) Math.round(
                    Math.pow(Math.max(0.0D, factor), 1.25D) * island.height
                            + noise * 1.2D
            );
        }

        double t = (distanceFromCenter - island.radius)
                / (island.slopeRadius() - island.radius);
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
        private final int x;
        private final int z;
        private final int radius;
        private final int height;
        private final long seed;

        private Island(int x, int z, int radius, int height, long seed) {
            this.x = x;
            this.z = z;
            this.radius = radius;
            this.height = height;
            this.seed = seed;
        }

        private int slopeRadius() {
            return Math.max(radius + 4, radius + 12);
        }

        private double noise(int x, int z) {
            long n = seed
                    ^ ((long) x * 341873128712L)
                    ^ ((long) z * 132897987541L);
            n ^= (n >>> 33);
            n *= 0xff51afd7ed558ccdl;
            n ^= (n >>> 33);
            n *= 0xc4ceb9fe1a85ec53l;
            n ^= (n >>> 33);
            return ((n & 0xFFFFL) / 32767.5D) - 1.0D;
        }
    }
}
