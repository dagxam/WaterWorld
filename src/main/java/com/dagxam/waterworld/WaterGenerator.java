package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import java.util.List;
import java.util.Random;

/** Генерирует океан, один центральный зелёный остров и автономные малые острова. */
public final class WaterGenerator extends ChunkGenerator {
    private final int seaLevel, oceanBaseHeight, oceanHeightAmplitude;
    private final double terrainScale;
    private final boolean islandEnabled;
    private final int centralHillHeight, centralHillOffsetX, centralHillOffsetZ;
    private final double centralHillRadius;
    private final IslandLayout layout;
    private long initializedSeed = Long.MIN_VALUE;
    private SimplexOctaveGenerator terrainGen, islandGen;

    public WaterGenerator(FileConfiguration config) {
        seaLevel = config.getInt("sea-level", 63);
        oceanBaseHeight = config.getInt("ocean.base-height", 35);
        oceanHeightAmplitude = Math.max(0, config.getInt("ocean.height-amplitude", 3));
        terrainScale = config.getDouble("ocean.terrain-scale", 0.004D);
        islandEnabled = config.getBoolean("island.enabled", true);

        centralHillHeight = Math.max(0, config.getInt("island.central-hill.height", 7));
        centralHillRadius = Math.max(16.0D, config.getDouble("island.central-hill.radius", 48.0D));
        centralHillOffsetX = config.getInt("island.central-hill.offset-x", 0);
        centralHillOffsetZ = config.getInt("island.central-hill.offset-z", -12);
        layout = new IslandLayout(config);
    }

    private synchronized void ensureGenerators(long seed) {
        if (initializedSeed == seed && terrainGen != null) return;
        initializedSeed = seed;
        terrainGen = new SimplexOctaveGenerator(new Random(seed), 3);
        terrainGen.setScale(terrainScale);
        islandGen = new SimplexOctaveGenerator(new Random(seed + 2L), 2);
        islandGen.setScale(configuredIslandNoiseScale());
    }

    private double configuredIslandNoiseScale() {
        return 0.035D;
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        ensureGenerators(info.getSeed());
        int minHeight = info.getMinHeight();
        int maxHeight = info.getMaxHeight() - 1;

        data.setRegion(0, minHeight, 0, 16, maxHeight + 1, 16, Material.AIR);

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = chunkX * 16 + lx;
                int z = chunkZ * 16 + lz;

                IslandLayout.Island island = islandEnabled
                        ? layout.getAcceptedIslandAt(info.getSeed(), x, z)
                        : null;

                int floor = getOceanFloor(info, x, z);
                int surface = island == null ? floor : getIslandSurface(x, z, island);

                boolean islandColumn = island != null;
                int top = Math.min(
                        maxHeight,
                        islandColumn ? Math.max(surface, seaLevel) : seaLevel
                );

                data.setBlock(lx, minHeight, lz, Material.BEDROCK);

                /*
                 * Это прежняя логика рельефа. Не меняем океанское дно,
                 * песок/гравий/камень/deepslate и форму существующего острова.
                 */
                for (int y = minHeight + 1; y <= top; y++) {
                    if (isBottomBedrock(info.getSeed(), x, y, z, minHeight)) {
                        data.setBlock(lx, y, lz, Material.BEDROCK);
                    } else if (islandColumn) {
                        setIslandTerrain(data, lx, lz, y, surface);
                    } else {
                        setOceanTerrain(data, lx, lz, y, floor);
                    }
                }
            }
        }

        // ВНИМАНИЕ: WaterWorld здесь больше не генерирует руды.
        // Ore Plugin делает свою генерацию самостоятельно на ChunkLoadEvent.
    }

    private boolean isBottomBedrock(long seed, int x, int y, int z, int minHeight) {
        int layer = y - minHeight;
        if (layer <= 0) return true;
        if (layer > 4) return false;
        int chance = 100 - layer * 20;
        long value = seed;
        value ^= (long) x * 341873128712L;
        value ^= (long) z * 132897987541L;
        value ^= (long) y * 42317861L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe4d37d7bdL;
        value ^= value >>> 33;
        return Math.floorMod(value, 100L) < chance;
    }

    private int getOceanFloor(WorldInfo info, int x, int z) {
        return clamp(
                oceanBaseHeight + (int) Math.round(
                        terrainGen.noise(x, z, 0.5D, 0.5D, true) * oceanHeightAmplitude
                ),
                info.getMinHeight() + 6,
                seaLevel - 10
        );
    }

    private void setOceanTerrain(
            ChunkData data,
            int lx,
            int lz,
            int y,
            int floor
    ) {
        if (y > floor) {
            data.setBlock(lx, y, lz, Material.WATER);
        } else if (y < floor - 6) {
            data.setBlock(lx, y, lz, Material.DEEPSLATE);
        } else if (y < floor - 3) {
            data.setBlock(lx, y, lz, Material.STONE);
        } else if (y < floor - 1) {
            data.setBlock(lx, y, lz, Material.GRAVEL);
        } else {
            data.setBlock(lx, y, lz, Material.SAND);
        }
    }

    private void setIslandTerrain(
            ChunkData data,
            int lx,
            int lz,
            int y,
            int surface
    ) {
        if (y > surface) {
            data.setBlock(lx, y, lz, y <= seaLevel ? Material.WATER : Material.AIR);
            return;
        }

        if (y < 0) {
            data.setBlock(lx, y, lz, Material.DEEPSLATE);
            return;
        }

        /*
         * Для малых островов сохраняем прежний рельеф:
         * stone + dirt + grass сверху.
         *
         * Отличие теперь только в определении того,
         * является ли колонка частью автономного острова.
         */
        if (surface <= seaLevel) {
            data.setBlock(
                    lx,
                    y,
                    lz,
                    y < surface - 4
                            ? Material.STONE
                            : y < surface - 1
                            ? Material.SANDSTONE
                            : Material.SAND
            );
            return;
        }

        data.setBlock(
                lx,
                y,
                lz,
                y < surface - 5
                        ? Material.STONE
                        : y < surface - 1
                        ? Material.DIRT
                        : Material.GRASS_BLOCK
        );
    }

    private int getIslandSurface(int x, int z, IslandLayout.Island island) {
        double distance = Math.sqrt(distanceSquared(
                x, z, island.x(), island.z()
        ));

        int radius = island.radius();
        int slopeRadius = getSlopeRadius(radius);

        double ocean = getLocalOceanHeight(x, z);

        if (distance >= slopeRadius) {
            return (int) Math.round(ocean);
        }

        if (distance <= radius) {
            double edge = Math.max(0.0D, 1.0D - distance / radius);
            double coast = Math.pow(edge, 1.45D);
            double noise = islandGen.noise(
                    x, z, 0.5D, 0.5D, true
            ) * island.variation();

            double base = seaLevel + 1.0D
                    + coast * island.height()
                    + noise;

            if (island.main() && centralHillHeight > 0) {
                double hx = island.x() + centralHillOffsetX;
                double hz = island.z() + centralHillOffsetZ;
                double dx = x - hx, dz = z - hz;
                double d = Math.sqrt(dx * dx + dz * dz);

                if (d < centralHillRadius) {
                    double t = 1.0D - d / centralHillRadius;
                    double smooth = t * t * (3.0D - 2.0D * t);
                    base += smooth * centralHillHeight;
                }
            }

            return clamp(
                    (int) Math.round(base),
                    seaLevel + 1,
                    seaLevel + island.height()
                            + centralHillHeight + 1
            );
        }

        double t = (distance - radius)
                / (double) (slopeRadius - radius);

        t = t * t * (3.0D - 2.0D * t);

        return (int) Math.round(
                (seaLevel + 0.5D)
                        + (ocean - (seaLevel + 0.5D)) * t
        );
    }

    private int getSlopeRadius(int radius) {
        return radius + Math.max(14, radius / 3);
    }

    private double getLocalOceanHeight(int x, int z) {
        return clampDouble(
                oceanBaseHeight
                        + terrainGen.noise(
                                x, z, 0.5D, 0.5D, true
                        ) * oceanHeightAmplitude,
                30.0D,
                seaLevel - 10.0D
        );
    }

    private static long distanceSquared(
            int x1, int z1,
            int x2, int z2
    ) {
        long dx = (long) x1 - x2;
        long dz = (long) z1 - z2;
        return dx * dx + dz * dz;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(
            double value,
            double min,
            double max
    ) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(
                    WorldInfo worldInfo,
                    int x,
                    int y,
                    int z
            ) {
                IslandLayout.Island island =
                        layout.getAcceptedIslandAt(
                                worldInfo.getSeed(),
                                x,
                                z
                        );

                if (island != null) {
                    return island.biome();
                }

                return Biome.WARM_OCEAN;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                List<Biome> biomes = new java.util.ArrayList<>();
                biomes.add(Biome.WARM_OCEAN);

                /*
                 * Добавляем только настроенные островные биомы.
                 * Это важно для клиента/движка биом-провайдера.
                 */
                for (String raw : java.util.Objects.requireNonNullElseGet(
                        getConfiguredBiomeNames(),
                        List::<String>of
                )) {
                    try {
                        biomes.add(Biome.valueOf(raw));
                    } catch (IllegalArgumentException ignored) {
                        // skip invalid
                    }
                }

                return List.copyOf(
                        new java.util.LinkedHashSet<>(biomes)
                );
            }

            private List<String> getConfiguredBiomeNames() {
                return List.of(
                        "PLAINS",
                        "FOREST",
                        "BIRCH_FOREST",
                        "TAIGA",
                        "JUNGLE",
                        "SAVANNA",
                        "DARK_FOREST",
                        "SWAMP",
                        "FLOWER_FOREST"
                );
            }
        };
    }

    @Override
    public boolean shouldGenerateNoise() {
        return true;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return true;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return true;
    }
}
