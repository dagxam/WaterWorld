package com.dagxam.waterworld;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Детерминированная раскладка главного и автономных малых островов.
 *
 * Каждый малый остров определяется только seed мира и координатами ячейки.
 * Поэтому результат не зависит от порядка загрузки чанков и сохраняется
 * после перезапуска сервера.
 *
 * Биомы разрешаются через data-driven registry Paper 26.3, а не через
 * устаревшие enum-методы Biome.
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
    private final Biome mainBiome;
    private final Biome oceanBiome;

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
        mainBiome = resolveBiomeOrThrow(config.getString("island.biome", "minecraft:plains"), "minecraft:plains");
        oceanBiome = resolveBiomeOrThrow("minecraft:warm_ocean", "minecraft:warm_ocean");

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
        return List.of(createMainIsland());
    }

    /**
     * Возвращает биомы, которые реально могут быть назначены малым островам.
     * Используется BiomeProvider для корректного списка возможных биомов.
     */
    public Biome getOceanBiome() {
        return oceanBiome;
    }

    public List<Biome> getConfiguredBiomes() {
        List<Biome> result = new ArrayList<>(biomeOptions.size() + 1);
        result.add(createMainIsland().biome());
        for (BiomeOption option : biomeOptions) {
            if (!result.contains(option.biome())) {
                result.add(option.biome());
            }
        }
        return List.copyOf(result);
    }

    public Island getIslandAt(long worldSeed, int x, int z) {
        Island main = createMainIsland();
        if (isInsideMain(main, x, z)) return main;

        if (!additionalEnabled || biomeOptions.isEmpty()) return null;

        int cellX = Math.floorDiv(x, cellSizeChunks * 16);
        int cellZ = Math.floorDiv(z, cellSizeChunks * 16);

        Candidate best = null;

        for (int dx = -neighborCells; dx <= neighborCells; dx++) {
            for (int dz = -neighborCells; dz <= neighborCells; dz++) {
                Candidate candidate = generateCandidate(worldSeed, cellX + dx, cellZ + dz);
                if (candidate == null) continue;

                long distanceSquared = distanceSquared(
                        x, z, candidate.island.x(), candidate.island.z()
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

    /**
     * Возвращает принятый остров с учётом минимального расстояния
     * до других детерминированных кандидатов.
     */
    public Island getAcceptedIslandAt(long worldSeed, int x, int z) {
        Island main = createMainIsland();
        if (isInsideMain(main, x, z)) return main;

        if (!additionalEnabled || biomeOptions.isEmpty()) return null;

        int cellX = Math.floorDiv(x, cellSizeChunks * 16);
        int cellZ = Math.floorDiv(z, cellSizeChunks * 16);

        Candidate best = null;

        for (int dx = -neighborCells; dx <= neighborCells; dx++) {
            for (int dz = -neighborCells; dz <= neighborCells; dz++) {
                Candidate candidate = generateAcceptedCandidate(
                        worldSeed, cellX + dx, cellZ + dz
                );
                if (candidate == null) continue;

                long distanceSquared = distanceSquared(
                        x, z, candidate.island.x(), candidate.island.z()
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

    /**
     * Находит остров, пересекающий указанный чанк.
     * Используется декораторами, чтобы не перебирать весь мир и не
     * зависеть от старого фиксированного списка островов.
     */
    public Island getAcceptedIslandForChunk(long worldSeed, int chunkX, int chunkZ) {
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        Island main = createMainIsland();
        if (circleIntersectsRectangle(main.x(), main.z(), getInfluenceRadius(main.radius()),
                minX, minZ, maxX, maxZ)) {
            return main;
        }

        if (!additionalEnabled || biomeOptions.isEmpty()) return null;

        int centerX = minX + 8;
        int centerZ = minZ + 8;
        int cellX = Math.floorDiv(centerX, cellSizeChunks * 16);
        int cellZ = Math.floorDiv(centerZ, cellSizeChunks * 16);

        Island best = null;
        long bestDistance = Long.MAX_VALUE;

        for (int dx = -neighborCells; dx <= neighborCells; dx++) {
            for (int dz = -neighborCells; dz <= neighborCells; dz++) {
                Candidate candidate = generateAcceptedCandidate(
                        worldSeed, cellX + dx, cellZ + dz
                );
                if (candidate == null) continue;

                Island island = candidate.island();
                int influence = getInfluenceRadius(island.radius());

                if (!circleIntersectsRectangle(
                        island.x(), island.z(), influence,
                        minX, minZ, maxX, maxZ
                )) {
                    continue;
                }

                long distance = distanceSquared(
                        centerX, centerZ, island.x(), island.z()
                );
                if (best == null || distance < bestDistance) {
                    best = island;
                    bestDistance = distance;
                }
            }
        }

        return best;
    }

    private static boolean circleIntersectsRectangle(
            int centerX, int centerZ, int radius,
            int minX, int minZ, int maxX, int maxZ
    ) {
        int nearestX = Math.max(minX, Math.min(centerX, maxX));
        int nearestZ = Math.max(minZ, Math.min(centerZ, maxZ));
        long dx = (long) centerX - nearestX;
        long dz = (long) centerZ - nearestZ;
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    private Candidate generateCandidate(long worldSeed, int cellX, int cellZ) {
        Random random = new Random(cellSeed(worldSeed, cellX, cellZ));

        if (random.nextDouble() >= spawnChance) return null;

        int cellSizeBlocks = cellSizeChunks * 16;
        int cellMinX = cellX * cellSizeBlocks;
        int cellMinZ = cellZ * cellSizeBlocks;

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

        return new Candidate(island, 0L);
    }

    private Candidate generateAcceptedCandidate(long worldSeed, int cellX, int cellZ) {
        Candidate candidate = generateCandidate(worldSeed, cellX, cellZ);
        if (candidate == null) return null;

        for (int dx = -neighborCells; dx <= neighborCells; dx++) {
            for (int dz = -neighborCells; dz <= neighborCells; dz++) {
                if (dx == 0 && dz == 0) continue;

                Candidate other = generateCandidate(
                        worldSeed, cellX + dx, cellZ + dz
                );
                if (other == null) continue;

                long distance = distanceSquared(
                        candidate.island.x(), candidate.island.z(),
                        other.island.x(), other.island.z()
                );

                long required = (long) candidate.island.radius()
                        + other.island.radius()
                        + minDistance;

                if (distance < required * required) {
                    long mine = cellKey(cellX, cellZ);
                    long theirs = cellKey(cellX + dx, cellZ + dz);

                    if (mine > theirs) return null;
                }
            }
        }

        return candidate;
    }

    private boolean isInsideMain(Island island, int x, int z) {
        int influence = getInfluenceRadius(island.radius());
        return distanceSquared(x, z, island.x(), island.z())
                <= (long) influence * influence;
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
                mainBiome
        );
    }

    private List<BiomeOption> loadBiomeOptions(FileConfiguration config) {
        List<BiomeOption> result = new ArrayList<>();
        List<String> configured = config.getStringList("additional-islands.biomes");

        for (String raw : configured) {
            String key = normalizeBiomeKey(raw);
            Biome biome = resolveBiome(key);
            if (biome == null) continue;

            result.add(new BiomeOption(biome, floraForBiome(key)));
        }

        if (result.isEmpty()) {
            String[] fallback = {
                    "minecraft:plains",
                    "minecraft:forest",
                    "minecraft:birch_forest",
                    "minecraft:old_growth_birch_forest",
                    "minecraft:flower_forest",
                    "minecraft:dappled_forest",
                    "minecraft:cherry_grove",
                    "minecraft:taiga",
                    "minecraft:old_growth_pine_taiga",
                    "minecraft:old_growth_spruce_taiga",
                    "minecraft:snowy_taiga",
                    "minecraft:jungle",
                    "minecraft:sparse_jungle",
                    "minecraft:bamboo_jungle",
                    "minecraft:savanna",
                    "minecraft:windswept_savanna",
                    "minecraft:swamp",
                    "minecraft:meadow",
                    "minecraft:dark_forest",
                    "minecraft:pale_garden",
                    "minecraft:windswept_forest",
                    "minecraft:wooded_badlands",
                    "minecraft:badlands"
            };

            for (String key : fallback) {
                Biome biome = resolveBiome(key);
                if (biome != null) {
                    result.add(new BiomeOption(biome, floraForBiome(key)));
                }
            }
        }

        return List.copyOf(result);
    }

    private Biome resolveBiomeOrThrow(String rawKey, String fallbackKey) {
        Biome biome = resolveBiome(rawKey);
        if (biome != null) return biome;

        biome = resolveBiome(fallbackKey);
        if (biome != null) return biome;

        throw new IllegalStateException(
                "WaterWorld: биом не найден в RegistryKey.BIOME: " + rawKey
        );
    }

    private Biome resolveBiome(String rawKey) {
        String key = normalizeBiomeKey(rawKey);
        NamespacedKey namespacedKey = NamespacedKey.fromString(key);
        if (namespacedKey == null) return null;

        try {
            Registry<Biome> registry =
                    RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
            return registry.get(namespacedKey);
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            return null;
        }
    }

    private String normalizeBiomeKey(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return "";
        return value.contains(":") ? value : "minecraft:" + value;
    }

    private String floraForBiome(String key) {
        String name = key.substring(key.indexOf(':') + 1);

        if (name.contains("dappled_forest")) return "dappled_forest";
        if (name.contains("pale_garden")) return "pale_garden";
        if (name.contains("cherry_grove")) return "cherry";
        if (name.contains("birch")) return "birch";
        if (name.contains("taiga")) return "taiga";
        if (name.contains("jungle")) return "jungle";
        if (name.contains("savanna")) return "savanna";
        if (name.contains("dark_forest")) return "dark_forest";
        if (name.contains("swamp")) return "swamp";
        if (name.contains("flower_forest")) return "flower";
        if (name.contains("meadow")) return "meadow";
        if (name.contains("badlands")) return "savanna";
        if (name.contains("plains")) return "main";
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
