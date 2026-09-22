package com.dagxam.waterworld;

import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Размещает малые острова автономно по сетке.
 *
 * Главный остров остаётся фиксированным.
 * Малые острова не создаются одним общим списком заранее:
 * каждая ячейка пространства имеет собственное детерминированное решение.
 *
 * Благодаря координатам ячейки + seed мира результат:
 * - одинаков после перезапуска;
 * - одинаков после выгрузки/загрузки чанков;
 * - не зависит от порядка загрузки чанков;
 * - не требует записи координат каждого острова в отдельный файл.
 */
public final class IslandLayout {
    public record Island(
            int x,
            int z,
            int radius,
            int height,
            double variation,
            boolean main,
            String flora,
            Biome biome
    ) {}

    private final int mainX;
    private final int mainZ;
    private final int mainRadius;
    private final int mainHeight;
    private final double mainVariation;

    private final boolean additionalEnabled;
    private final int cellSizeChunks;
    private final double spawnChance;
    private final int minDistance;
    private final int minDistanceFromMain;
    private final int minRadius;
    private final int maxRadius;
    private final int minHeight;
    private final int maxHeight;
    private final double extraVariation;
    private final int neighborCells;
    private final List<BiomeOption> biomeOptions;

    public IslandLayout(FileConfiguration config) {
        mainX = config.getInt("island.center-x", 0);
        mainZ = config.getInt("island.center-z", 0);
        mainRadius = Math.max(16, config.getInt("island.radius", 100));
        mainHeight = Math.max(2, config.getInt("island.height", 9));
        mainVariation = Math.max(0.0D, config.getDouble("island.variation", 1.2D));

        additionalEnabled = config.getBoolean("additional-islands.enabled", true);
        cellSizeChunks = Math.max(4, config.getInt("additional-islands.cell-size-chunks", 16));
        spawnChance = clamp(config.getDouble("additional-islands.spawn-chance", 0.12D), 0.0D, 1.0D);
        minDistance = Math.max(
                mainRadius + 64,
                config.getInt("additional-islands.min-distance", 700)
        );
        minDistanceFromMain = Math.max(
                mainRadius + 64,
                config.getInt("additional-islands.min-distance-from-main", minDistance)
        );
        minRadius = Math.max(8, config.getInt("additional-islands.radius-min", 15));
        maxRadius = Math.max(minRadius, config.getInt("additional-islands.radius-max", 24));
        minHeight = Math.max(2, config.getInt("additional-islands.height-min", 3));
        maxHeight = Math.max(minHeight, config.getInt("additional-islands.height-max", 5));
        extraVariation = Math.max(0.0D, config.getDouble("additional-islands.variation", 0.7D));
        neighborCells = Math.max(1, config.getInt("additional-islands.neighbor-cells", 3));

        biomeOptions = loadBiomeOptions(config);
    }

    public List<Island> get(long worldSeed) {
        List<Island> result = new ArrayList<>(1);
        result.add(createMainIsland());
        return List.copyOf(result);
    }

    /**
     * Возвращает остров, влияющий на конкретную точку.
     * Для каждой точки рассматриваются только несколько ближайших ячеек.
     */
    public Island getIslandAt(long worldSeed, int x, int z) {
        Island main = createMainIsland();
        if (isInsideMain(main, x, z)) {
            return main;
        }

        if (!additionalEnabled || biomeOptions.isEmpty()) {
            return null;
        }

        int cellX = Math.floorDiv(x, cellSizeChunks * 16);
        int cellZ = Math.floorDiv(z, cellSizeChunks * 16);

        Candidate best = null;

        for (int dx = -neighborCells; dx <= neighborCells; dx++) {
            for (int dz = -neighborCells; dz <= neighborCells; dz++) {
                Candidate candidate = generateCandidate(worldSeed, cellX + dx, cellZ + dz);
                if (candidate == null) {
                    continue;
                }

                long distanceSquared = distanceSquared(
                        x, z,
                        candidate.island.x(), candidate.island.z()
                );
                int influence = getInfluenceRadius(candidate.island.radius());

                if (distanceSquared > (long) influence * influence) {
                    continue;
                }

                if (best == null || distanceSquared < best.distanceSquared) {
                    best = new Candidate(candidate.island, distanceSquared);
                }
            }
        }

        return best == null ? null : best.island;
    }

    /**
     * Возвращает сгенерированный остров только для указанной ячейки.
     * Одна ячейка имеет максимум один остров.
     *
     * Важное условие: решение зависит только от seed + координат ячейки,
     * поэтому загрузка чанков в любом порядке даёт одинаковый мир.
     */
    private Candidate generateCandidate(long worldSeed, int cellX, int cellZ) {
        Random random = new Random(cellSeed(worldSeed, cellX, cellZ));

        if (random.nextDouble() >= spawnChance) {
            return null;
        }

        int cellSizeBlocks = cellSizeChunks * 16;
        int cellMinX = cellX * cellSizeBlocks;
        int cellMinZ = cellZ * cellSizeBlocks;

        /*
         * Точка центра должна находиться внутри ячейки, но не прямо на границе.
         * Это не влияет на сохранность: координаты полностью детерминированы.
         */
        int margin = Math.max(maxRadius + 4, Math.min(cellSizeBlocks / 3, 96));

        int centerX = cellMinX + margin + random.nextInt(
                Math.max(1, cellSizeBlocks - margin * 2)
        );
        int centerZ = cellMinZ + margin + random.nextInt(
                Math.max(1, cellSizeBlocks - margin * 2)
        );

        int radius = minRadius + random.nextInt(maxRadius - minRadius + 1);
        int height = minHeight + random.nextInt(maxHeight - minHeight + 1);

        BiomeOption biome = biomeOptions.get(random.nextInt(biomeOptions.size()));

        Island island = new Island(
                centerX,
                centerZ,
                radius,
                height,
                extraVariation,
                false,
                biome.flora(),
                biome.biome()
        );

        if (distanceSquared(centerX, centerZ, mainX, mainZ)
                < (long) minDistanceFromMain * minDistanceFromMain) {
            return null;
        }

        /*
         * Соседние ячейки могут быть предложены независимо.
         * При запросе острова мы дополнительно проверяем минимальную дистанцию.
         * Поэтому близкие кандидаты не перекрываются.
         */
        return new Candidate(island, 0L);
    }

    /**
     * Проверяет остров в точке с учётом минимальной дистанции до остальных
     * детерминированных кандидатов.
     *
     * Если рядом есть несколько кандидатов, выбирается ближайший допустимый.
     */
    public Island getAcceptedIslandAt(long worldSeed, int x, int z) {
        Island main = createMainIsland();
        if (isInsideMain(main, x, z)) {
            return main;
        }

        if (!additionalEnabled || biomeOptions.isEmpty()) {
            return null;
        }

        int cellX = Math.floorDiv(x, cellSizeChunks * 16);
        int cellZ = Math.floorDiv(z, cellSizeChunks * 16);

        Candidate best = null;

        for (int dx = -neighborCells; dx <= neighborCells; dx++) {
            for (int dz = -neighborCells; dz <= neighborCells; dz++) {
                int checkX = cellX + dx;
                int checkZ = cellZ + dz;

                Candidate candidate = generateAcceptedCandidate(worldSeed, checkX, checkZ);
                if (candidate == null) continue;

                long distanceSquared = distanceSquared(
                        x, z,
                        candidate.island.x(), candidate.island.z()
                );

                int influence = getInfluenceRadius(candidate.island.radius());
                if (distanceSquared > (long) influence * influence) continue;

                if (best == null || distanceSquared < best.distanceSquared) {
                    best = new Candidate(candidate.island, distanceSquared);
                }
            }
        }

        return best == null ? null : best.island;
    }

    private Candidate generateAcceptedCandidate(long worldSeed, int cellX, int cellZ) {
        Candidate candidate = generateCandidate(worldSeed, cellX, cellZ);
        if (candidate == null) return null;

        int cellSizeBlocks = cellSizeChunks * 16;

        for (int dx = -neighborCells; dx <= neighborCells; dx++) {
            for (int dz = -neighborCells; dz <= neighborCells; dz++) {
                if (dx == 0 && dz == 0) continue;

                Candidate other = generateCandidate(worldSeed, cellX + dx, cellZ + dz);
                if (other == null) continue;

                /*
                 * Только кандидат с меньшим стабильным идентификатором занимает
                 * конфликтующую область. При равенстве побеждает меньший cellKey.
                 * Это делает результат независимым от порядка ChunkLoadEvent.
                 */
                long distance = distanceSquared(
                        candidate.island.x(), candidate.island.z(),
                        other.island.x(), other.island.z()
                );
                long min = (long) candidate.island.radius()
                        + other.island.radius()
                        + minDistance;

                if (distance < min * min) {
                    long mine = cellKey(cellX, cellZ);
                    long theirs = cellKey(cellX + dx, cellZ + dz);
                    if (mine > theirs) {
                        return null;
                    }
                    if (mine == theirs && cellSizeBlocks < 0) {
                        return null;
                    }
                }
            }
        }

        return candidate;
    }

    private boolean isInsideMain(Island island, int x, int z) {
        return distanceSquared(x, z, island.x(), island.z())
                <= (long) getInfluenceRadius(island.radius()) * getInfluenceRadius(island.radius());
    }

    private int getInfluenceRadius(int radius) {
        return radius + Math.max(14, radius / 3);
    }

    private Island createMainIsland() {
        return new Island(
                mainX,
                mainZ,
                mainRadius,
                mainHeight,
                mainVariation,
                true,
                "main",
                Biome.PLAINS
        );
    }

    private List<BiomeOption> loadBiomeOptions(FileConfiguration config) {
        List<BiomeOption> result = new ArrayList<>();
        List<String> configured = config.getStringList("additional-islands.biomes");

        for (String raw : configured) {
            try {
                Biome biome = Biome.valueOf(raw.trim().toUpperCase());
                result.add(new BiomeOption(biome, floraForBiome(biome)));
            } catch (IllegalArgumentException ignored) {
                // Неверный биом просто пропускается.
            }
        }

        if (result.isEmpty()) {
            result.add(new BiomeOption(Biome.FOREST, "forest"));
            result.add(new BiomeOption(Biome.BIRCH_FOREST, "birch"));
            result.add(new BiomeOption(Biome.TAIGA, "taiga"));
            result.add(new BiomeOption(Biome.JUNGLE, "jungle"));
            result.add(new BiomeOption(Biome.SAVANNA, "savanna"));
            result.add(new BiomeOption(Biome.DARK_FOREST, "dark_forest"));
            result.add(new BiomeOption(Biome.SWAMP, "swamp"));
            result.add(new BiomeOption(Biome.FLOWER_FOREST, "flower"));
        }

        return List.copyOf(result);
    }

    private String floraForBiome(Biome biome) {
        String name = biome.name();

        if (name.contains("BIRCH")) return "birch";
        if (name.contains("TAIGA")) return "taiga";
        if (name.contains("JUNGLE")) return "jungle";
        if (name.contains("SAVANNA")) return "savanna";
        if (name.contains("DARK_FOREST")) return "dark_forest";
        if (name.contains("SWAMP")) return "swamp";
        if (name.contains("FLOWER")) return "flower";
        if (name.contains("PLAINS")) return "main";

        return "forest";
    }

    private static long cellSeed(long worldSeed, int cellX, int cellZ) {
        long value = worldSeed;
        value ^= (long) cellX * 341873128712L;
        value ^= (long) cellZ * 132897987541L;
        value ^= 0x6A09E667F3BCC909L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ (value >>> 33);
    }

    private static long cellKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static long distanceSquared(int x1, int z1, int x2, int z2) {
        long dx = (long) x1 - x2;
        long dz = (long) z1 - z2;
        return dx * dx + dz * dz;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Candidate(Island island, long distanceSquared) {}
    private record BiomeOption(Biome biome, String flora) {}
}
