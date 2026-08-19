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

/**
 * WaterWorld terrain generator.
 *
 * Important design points:
 * - Noise generators are initialized once per world seed, not once per chunk.
 * - The ocean and island are generated in separate, readable stages.
 * - The island has a PLAINS biome, grass/dirt layers and is above sea level.
 * - Vanilla decorations and mob generation remain enabled, allowing plains
 *   trees/grass and passive animals to spawn naturally.
 */
public final class WaterGenerator extends ChunkGenerator {

    private final int seaLevel;

    private final int oceanBaseHeight;
    private final int oceanHeightAmplitude;
    private final double terrainScale;
    private final double caveScale;
    private final double caveThreshold;
    private final int caveRoof;

    private final boolean islandEnabled;
    private final int islandCenterX;
    private final int islandCenterZ;
    private final int islandRadius;
    private final int islandHeight;
    private final double islandVariation;
    private final double islandNoiseScale;
    private final double shorelineRadius;

    private long initializedSeed = Long.MIN_VALUE;
    private SimplexOctaveGenerator terrainGen;
    private SimplexOctaveGenerator caveGen;
    private SimplexOctaveGenerator islandGen;

    public WaterGenerator(FileConfiguration config) {
        seaLevel = config.getInt("sea-level", 63);

        oceanBaseHeight = config.getInt("ocean.base-height", 35);
        oceanHeightAmplitude = config.getInt("ocean.height-amplitude", 12);
        terrainScale = config.getDouble("ocean.terrain-scale", 0.005D);
        caveScale = config.getDouble("ocean.cave-scale", 0.015D);
        caveThreshold = config.getDouble("ocean.cave-threshold", 0.64D);
        caveRoof = config.getInt("ocean.cave-roof", 8);

        islandEnabled = config.getBoolean("island.enabled", true);
        islandCenterX = config.getInt("island.center-x", 0);
        islandCenterZ = config.getInt("island.center-z", 0);
        islandRadius = Math.max(8, config.getInt("island.radius", 17));
        islandHeight = Math.max(2, config.getInt("island.height", 7));
        islandVariation = config.getDouble("island.variation", 1.5D);
        islandNoiseScale = config.getDouble("island.noise-scale", 0.09D);
        shorelineRadius = Math.max(0.5D, config.getDouble("island.shoreline-radius", 2.5D));
    }

    private synchronized void ensureGenerators(long seed) {
        if (initializedSeed == seed && terrainGen != null) {
            return;
        }

        initializedSeed = seed;

        terrainGen = new SimplexOctaveGenerator(new Random(seed), 4);
        terrainGen.setScale(terrainScale);

        caveGen = new SimplexOctaveGenerator(new Random(seed + 1L), 3);
        caveGen.setScale(caveScale);

        islandGen = new SimplexOctaveGenerator(new Random(seed + 2L), 2);
        islandGen.setScale(islandNoiseScale);
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        ensureGenerators(worldInfo.getSeed());

        int minHeight = worldInfo.getMinHeight();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = chunkX * 16 + localX;
                int z = chunkZ * 16 + localZ;

                double islandDistance = distanceToIsland(x, z);
                boolean island = islandEnabled && islandDistance <= islandRadius;

                double terrainNoise = terrainGen.noise(x, z, 0.5D, 0.5D, true);
                int oceanFloor = clamp(
                        oceanBaseHeight + (int) Math.round(terrainNoise * oceanHeightAmplitude),
                        minHeight + 2,
                        seaLevel - 1
                );

                int surface = island ? getIslandSurface(x, z, islandDistance) : oceanFloor;

                chunkData.setBlock(localX, minHeight, localZ, Material.BEDROCK);

                for (int y = minHeight + 1; y <= seaLevel; y++) {
                    if (island) {
                        setIslandBlock(chunkData, localX, localZ, x, z, y, surface, islandDistance);
                    } else {
                        setOceanBlock(chunkData, localX, localZ, x, z, y, oceanFloor);
                    }
                }
            }
        }
    }

    private void setOceanBlock(
            ChunkData data,
            int localX,
            int localZ,
            int x,
            int z,
            int y,
            int floor
    ) {
        if (y > floor) {
            data.setBlock(localX, y, localZ, Material.WATER);
            return;
        }

        if (y < floor - caveRoof && isCave(x, y, z)) {
            data.setBlock(localX, y, localZ, Material.CAVE_AIR);
            return;
        }

        if (y <= 0) {
            data.setBlock(localX, y, localZ, Material.DEEPSLATE);
        } else if (y < floor - 4) {
            data.setBlock(localX, y, localZ, Material.STONE);
        } else if (y < floor - 1) {
            data.setBlock(localX, y, localZ, Material.SANDSTONE);
        } else {
            data.setBlock(localX, y, localZ, Material.SAND);
        }
    }

    private void setIslandBlock(
            ChunkData data,
            int localX,
            int localZ,
            int x,
            int z,
            int y,
            int surface,
            double distance
    ) {
        if (y > surface) {
            data.setBlock(localX, y, localZ, Material.AIR);
            return;
        }

        if (y <= 0) {
            data.setBlock(localX, y, localZ, Material.DEEPSLATE);
        } else if (y < surface - 5) {
            data.setBlock(localX, y, localZ, Material.STONE);
        } else if (y < surface - 1) {
            data.setBlock(localX, y, localZ, Material.DIRT);
        } else {
            boolean shoreline =
                    distance >= islandRadius - shorelineRadius ||
                    surface <= seaLevel + 1;

            data.setBlock(
                    localX,
                    y,
                    localZ,
                    shoreline ? Material.SAND : Material.GRASS_BLOCK
            );
        }
    }

    private boolean isCave(int x, int y, int z) {
        return caveGen.noise(x, y, z, 0.5D, 0.5D, true) > caveThreshold;
    }

    private int getIslandSurface(int x, int z, double distance) {
        double edgeFactor = 1.0D - distance / islandRadius;
        edgeFactor = Math.max(0.0D, Math.min(1.0D, edgeFactor));

        double variation = islandGen.noise(x, z, 0.5D, 0.5D, true) * islandVariation;

        double height =
                seaLevel + 1.0D +
                Math.pow(edgeFactor, 1.35D) * islandHeight +
                variation;

        return clamp(
                (int) Math.round(height),
                seaLevel + 1,
                seaLevel + islandHeight + 2
        );
    }

    private double distanceToIsland(int x, int z) {
        double dx = x - islandCenterX;
        double dz = z - islandCenterZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                if (islandEnabled && distanceToIsland(x, z) <= islandRadius) {
                    return Biome.PLAINS;
                }
                return Biome.WARM_OCEAN;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo info) {
                return List.of(Biome.WARM_OCEAN, Biome.PLAINS);
            }
        };
    }

    @Override
    public boolean shouldGenerateDecorations() {
        // PLAINS decorations provide grass/flowers/trees; WARM_OCEAN provides
        // the normal underwater vegetation.
        return true;
    }

    @Override
    public boolean shouldGenerateCaves() {
        // Custom cave noise is used for ocean terrain.
        return false;
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        // Lets the normal Minecraft spawning system use the PLAINS biome on
        // the island, including passive animals such as cows and sheep.
        return true;
    }
}
