package com.dagxam.waterworld;

import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Автономная детерминированная раскладка островов.
 *
 * Малые острова НЕ собираются заранее в один большой список.
 * Для каждого загружаемого чанка вычисляются только ближайшие ячейки
 * пространственной сетки. Благодаря этому мир может содержать острова
 * на практически неограниченной дальности.
 *
 * Позиция острова определяется только seed + координатами ячейки.
 * Поэтому после перезапуска/выгрузки чанка остров не "переезжает".
 */
public final class IslandLayout {
    public record Island(int x, int z, int radius, int height,
                         double variation, boolean main, String flora, Biome biome) {}

    private record BiomeDefinition(Biome biome, String flora) {}

    private static final BiomeDefinition[] SMALL_BIOMES = {
            new BiomeDefinition(Biome.DESERT, "desert"),
            new BiomeDefinition(Biome.FOREST, "forest"),
            new BiomeDefinition(Biome.BIRCH_FOREST, "birch"),
            new BiomeDefinition(Biome.TAIGA, "taiga"),
            new BiomeDefinition(Biome.SAVANNA, "savanna"),
            new BiomeDefinition(Biome.JUNGLE, "jungle"),
            new BiomeDefinition(Biome.FLOWER_FOREST, "flower"),
            new BiomeDefinition(Biome.SWAMP, "swamp"),
            new BiomeDefinition(Biome.DARK_FOREST, "dark_forest"),
            new BiomeDefinition(Biome.MUSHROOM_FIELDS, "mushroom"),
            new BiomeDefinition(Biome.CHERRY_GROVE, "cherry"),
            new BiomeDefinition(Biome.BADLANDS, "badlands")
    };

    private final int mainX;
    private final int mainZ;
    private final int mainRadius;
    private final int mainHeight;
    private final double mainVariation;

    private final boolean enabled;
    private final int chancePercent;
    private final int minDistance;
    private final int cellSize;
    private final int jitter;
    private final int minRadius;
    private final int maxRadius;
    private final int minHeight;
    private final int maxHeight;
    private final double extraVariation;

    public IslandLayout(FileConfiguration config) {
        mainX = config.getInt("island.center-x", 0);
        mainZ = config.getInt("island.center-z", 0);
        mainRadius = Math.max(16, config.getInt("island.radius", 100));
        mainHeight = Math.max(2, config.getInt("island.height", 9));
        mainVariation = Math.max(0.0D, config.getDouble("island.variation", 1.2D));

        enabled = config.getBoolean("additional-islands.enabled", true);
        chancePercent = clampPercent(config.getInt("additional-islands.chance-percent", 100));
        minDistance = Math.max(mainRadius + 80,
                config.getInt("additional-islands.min-distance", 500));

        int configuredCellSize = Math.max(256,
                config.getInt("additional-islands.cell-size", 650));
        minRadius = Math.max(8,
                config.getInt("additional-islands.radius-min", 15));
        maxRadius = Math.max(minRadius,
                config.getInt("additional-islands.radius-max", 24));
        minHeight = Math.max(2,
                config.getInt("additional-islands.height-min", 3));
        maxHeight = Math.max(minHeight,
                config.getInt("additional-islands.height-max", 5));
        extraVariation = Math.max(0.0D,
                config.getDouble("additional-islands.variation", 0.7D));

        /*
         * Один кандидат приходится на каждую пространственную ячейку
         * (если chance-percent = 100).
         *
         * Раньше размер ячейки ошибочно рассчитывался примерно как
         * 2 * min-distance, из-за чего реальные острова оказывались
         * примерно через 1100 блоков и могли долго не встречаться.
         *
         * Теперь достаточно гарантировать:
         * cellSize - 2 * jitter > minDistance.
         * Это позволяет держать острова достаточно разнесёнными,
         * но при этом сделать их заметно плотнее.
         */
        int configuredJitter = Math.min(64, Math.max(1, minDistance / 8));
        jitter = Math.min(configuredJitter, Math.max(1, configuredCellSize / 10));

        long safeCellSize = Math.max(
                configuredCellSize,
                (long) minDistance + (long) jitter * 2L + 16L
        );
        cellSize = (int) Math.min(Integer.MAX_VALUE - 1024L, safeCellSize);
    }

    /** Главный остров остаётся единственным фиксированным островом в центре. */
    public Island getMainIsland() {
        return new Island(mainX, mainZ, mainRadius, mainHeight,
                mainVariation, true, "main", Biome.PLAINS);
    }

    /**
     * Возвращает только острова, которые могут пересекать текущий чанк.
     * Никакого списка всех островов мира здесь нет.
     */
    public List<Island> getForChunk(long worldSeed, int chunkX, int chunkZ) {
        int chunkMinX = chunkX * 16;
        int chunkMinZ = chunkZ * 16;
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;

        List<Island> result = new ArrayList<>(2);
        Island main = getMainIsland();
        if (intersectsChunk(main, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ, true)) {
            result.add(main);
        }

        if (!enabled || chancePercent <= 0) return result;

        /*
         * Остров с радиусом до maxRadius может находиться в соседней ячейке,
         * поэтому берём небольшой запас с обеих сторон.
         */
        long minCellX = Math.floorDiv((long) chunkMinX - maxRadius - jitter, (long) cellSize);
        long maxCellX = Math.floorDiv((long) chunkMaxX + maxRadius + jitter, (long) cellSize);
        long minCellZ = Math.floorDiv((long) chunkMinZ - maxRadius - jitter, (long) cellSize);
        long maxCellZ = Math.floorDiv((long) chunkMaxZ + maxRadius + jitter, (long) cellSize);

        for (long cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (long cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                Island candidate = createCandidate(worldSeed, cellX, cellZ);
                if (candidate == null) continue;
                if (!intersectsChunk(candidate, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ, false)) continue;
                result.add(candidate);
            }
        }

        return result;
    }

    /**
     * Ищет биом/остров по мировым координатам.
     * Используется генератором биомов, поэтому при загрузке чанка
     * Minecraft всегда получает тот же остров и тот же биом.
     */
    public Island findIslandAt(long worldSeed, int x, int z) {
        Island main = getMainIsland();
        if (insideInfluence(main, x, z, true)) return main;

        if (!enabled || chancePercent <= 0) return null;

        long cellX = Math.floorDiv((long) x, (long) cellSize);
        long cellZ = Math.floorDiv((long) z, (long) cellSize);

        Island nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (long cx = cellX - 1; cx <= cellX + 1; cx++) {
            for (long cz = cellZ - 1; cz <= cellZ + 1; cz++) {
                Island candidate = createCandidate(worldSeed, cx, cz);
                if (candidate == null || !insideInfluence(candidate, x, z, false)) continue;

                double dx = x - candidate.x();
                double dz = z - candidate.z();
                double distance = dx * dx + dz * dz;
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = candidate;
                }
            }
        }

        return nearest;
    }

    /** Все биомы, которые реально может вернуть этот генератор. */
    public List<Biome> getBiomes() {
        List<Biome> biomes = new ArrayList<>(SMALL_BIOMES.length + 2);
        biomes.add(Biome.WARM_OCEAN);
        biomes.add(Biome.PLAINS);
        for (BiomeDefinition definition : SMALL_BIOMES) {
            if (!biomes.contains(definition.biome())) biomes.add(definition.biome());
        }
        return List.copyOf(biomes);
    }

    /**
     * Оставлен для совместимости с внешним кодом проекта.
     * Новая генерация малых островов не использует глобальный список.
     */
    @Deprecated
    public List<Island> get(long worldSeed) {
        return List.of(getMainIsland());
    }

    private Island createCandidate(long worldSeed, long cellX, long cellZ) {
        long seed = mixSeed(worldSeed, cellX, cellZ);

        if (Math.floorMod(seed, 100) >= chancePercent) return null;

        int offsetX = randomOffset(seed ^ 0x13579BDF2468ACE1L);
        int offsetZ = randomOffset(seed ^ 0x2468ACE13579BDFL);

        long centerX = cellX * (long) cellSize + cellSize / 2L + offsetX;
        long centerZ = cellZ * (long) cellSize + cellSize / 2L + offsetZ;

        if (centerX < Integer.MIN_VALUE || centerX > Integer.MAX_VALUE
                || centerZ < Integer.MIN_VALUE || centerZ > Integer.MAX_VALUE) {
            return null;
        }

        int radius = minRadius + boundedInt(seed ^ 0x55AA55AA55AA55AAL,
                maxRadius - minRadius + 1);
        int height = minHeight + boundedInt(seed ^ 0xAA55AA55AA55AA55L,
                maxHeight - minHeight + 1);

        int x = (int) centerX;
        int z = (int) centerZ;

        /*
         * У главного острова есть отдельная буферная зона.
         * Даже если ближайшая ячейка случайно дала координаты рядом,
         * такой кандидат отбрасывается.
         */
        long dx = (long) x - mainX;
        long dz = (long) z - mainZ;
        long mainSafeDistance = (long) mainRadius + radius + minDistance;
        if (dx * dx + dz * dz < mainSafeDistance * mainSafeDistance) return null;

        int biomeIndex = Math.floorMod(
                (int) Math.floorMod(cellX, SMALL_BIOMES.length)
                        + 2 * (int) Math.floorMod(cellZ, SMALL_BIOMES.length)
                        + (int) Math.floorMod(worldSeed ^ (worldSeed >>> 32), SMALL_BIOMES.length),
                SMALL_BIOMES.length
        );
        BiomeDefinition biome = SMALL_BIOMES[biomeIndex];

        return new Island(x, z, radius, height, extraVariation,
                false, biome.flora(), biome.biome());
    }

    private boolean intersectsChunk(Island island, int minX, int maxX,
                                    int minZ, int maxZ, boolean main) {
        int influence = getSlopeRadius(island.radius(), main);
        long closestX = clampLong(island.x(), minX, maxX);
        long closestZ = clampLong(island.z(), minZ, maxZ);
        long dx = (long) island.x() - closestX;
        long dz = (long) island.z() - closestZ;
        return dx * dx + dz * dz <= (long) influence * influence;
    }

    private boolean insideInfluence(Island island, int x, int z, boolean main) {
        int influence = getSlopeRadius(island.radius(), main);
        long dx = (long) x - island.x();
        long dz = (long) z - island.z();
        return dx * dx + dz * dz <= (long) influence * influence;
    }

    private int getSlopeRadius(int radius, boolean main) {
        return radius + Math.max(14, radius / 3);
    }

    private int randomOffset(long seed) {
        return (int) Math.floorMod(seed, (long) jitter * 2L + 1L) - jitter;
    }

    private static int boundedInt(long seed, int bound) {
        if (bound <= 1) return 0;
        return (int) Math.floorMod(mix(seed), bound);
    }

    private static long mixSeed(long worldSeed, long cellX, long cellZ) {
        long value = worldSeed;
        value ^= mix(cellX * 341873128712L);
        value ^= mix(cellZ * 132897987541L);
        value ^= 0x9E3779B97F4A7C15L;
        return mix(value);
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ (value >>> 33);
    }

    private static long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
