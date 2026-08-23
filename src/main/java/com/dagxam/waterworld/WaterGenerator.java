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

/** Генерирует рельеф WaterWorld без собственной генерации руд. */
public final class WaterGenerator extends ChunkGenerator {
    private final int seaLevel, oceanBaseHeight, oceanHeightAmplitude, caveRoof;
    private final double terrainScale, caveScale, caveThreshold;
    private final boolean islandEnabled;
    private final int islandHeight;
    private final double islandVariation, islandNoiseScale;
    private final int centralHillHeight;
    private final double centralHillRadius;
    private final IslandLayout layout;
    private long initializedSeed = Long.MIN_VALUE;
    private SimplexOctaveGenerator terrainGen, caveGen, islandGen;

    public WaterGenerator(FileConfiguration config) {
        seaLevel = config.getInt("sea-level", 63);
        oceanBaseHeight = config.getInt("ocean.base-height", 35);
        oceanHeightAmplitude = config.getInt("ocean.height-amplitude", 12);
        terrainScale = config.getDouble("ocean.terrain-scale", 0.005D);
        caveScale = config.getDouble("ocean.cave-scale", 0.015D);
        caveThreshold = config.getDouble("ocean.cave-threshold", 0.64D);
        caveRoof = config.getInt("ocean.cave-roof", 8);
        islandEnabled = config.getBoolean("island.enabled", true);
        islandHeight = Math.max(2, config.getInt("island.height", 9));
        islandVariation = config.getDouble("island.variation", 1.6D);
        islandNoiseScale = config.getDouble("island.noise-scale", 0.07D);
        centralHillHeight = Math.max(0, config.getInt("island.central-hill.height", 6));
        centralHillRadius = Math.max(4.0D, config.getDouble("island.central-hill.radius", 24.0D));
        layout = new IslandLayout(config);
    }

    private synchronized void ensureGenerators(long seed) {
        if (initializedSeed == seed && terrainGen != null) return;
        initializedSeed = seed;
        terrainGen = new SimplexOctaveGenerator(new Random(seed), 4);
        terrainGen.setScale(terrainScale);
        caveGen = new SimplexOctaveGenerator(new Random(seed + 1L), 3);
        caveGen.setScale(caveScale);
        islandGen = new SimplexOctaveGenerator(new Random(seed + 2L), 2);
        islandGen.setScale(islandNoiseScale);
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        ensureGenerators(info.getSeed());
        List<IslandLayout.Island> islands = layout.get(info.getSeed());
        int minHeight = info.getMinHeight();
        int maxHeight = info.getMaxHeight() - 1;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = chunkX * 16 + lx;
                int z = chunkZ * 16 + lz;
                IslandLayout.Island island = islandEnabled ? nearestIsland(islands, x, z) : null;
                double distance = island == null ? Double.MAX_VALUE : distance(x, z, island.x(), island.z());
                int floor = getOceanFloor(info, x, z);
                int surface = island == null ? floor : getIslandSurface(x, z, distance, island);
                int top = Math.max(surface, seaLevel);

                for (int y = minHeight + 1; y <= Math.min(maxHeight, seaLevel + islandHeight + centralHillHeight + 6); y++) {
                    if (island != null && distance <= getSlopeRadius(island.radius())) {
                        setIslandTerrain(data, lx, lz, y, surface);
                    } else {
                        setOceanTerrain(data, lx, lz, y, floor);
                    }
                }
                // Ensure all higher layers remain air outside terrain.
                for (int y = Math.min(maxHeight, seaLevel + islandHeight + centralHillHeight + 7); y <= maxHeight; y++) {
                    data.setBlock(lx, y, lz, Material.AIR);
                }
                data.setBlock(lx, minHeight, lz, Material.BEDROCK);
            }
        }
    }

    private IslandLayout.Island nearestIsland(List<IslandLayout.Island> islands, int x, int z) {
        IslandLayout.Island best = null;
        double bestDistance = Double.MAX_VALUE;
        for (IslandLayout.Island island : islands) {
            double d = distance(x, z, island.x(), island.z());
            if (d < bestDistance) {
                bestDistance = d;
                best = island;
            }
        }
        return best;
    }

    private int getSlopeRadius(int radius) {
        return radius + Math.max(24, radius / 2);
    }

    private int getOceanFloor(WorldInfo info, int x, int z) {
        return clamp(oceanBaseHeight
                        + (int) Math.round(terrainGen.noise(x, z, 0.5D, 0.5D, true) * oceanHeightAmplitude),
                info.getMinHeight() + 2, seaLevel - 1);
    }

    private void setOceanTerrain(ChunkData data, int lx, int lz, int y, int floor) {
        if (y > seaLevel) data.setBlock(lx, y, lz, Material.AIR);
        else if (y > floor) data.setBlock(lx, y, lz, Material.WATER);
        else if (y < floor - caveRoof) data.setBlock(lx, y, lz, Material.STONE);
        else if (y <= 0) data.setBlock(lx, y, lz, Material.DEEPSLATE);
        else if (y < floor - 4) data.setBlock(lx, y, lz, Material.STONE);
        else if (y < floor - 1) data.setBlock(lx, y, lz, Material.SANDSTONE);
        else data.setBlock(lx, y, lz, Material.SAND);
    }

    private void setIslandTerrain(ChunkData data, int lx, int lz, int y, int surface) {
        if (y > surface) {
            data.setBlock(lx, y, lz, y <= seaLevel ? Material.WATER : Material.AIR);
            return;
        }
        if (y <= 0) {
            data.setBlock(lx, y, lz, Material.DEEPSLATE);
            return;
        }
        if (surface <= seaLevel) {
            data.setBlock(lx, y, lz,
                    y < surface - 4 ? Material.STONE :
                            y < surface - 1 ? Material.SANDSTONE : Material.SAND);
            return;
        }
        data.setBlock(lx, y, lz,
                y < surface - 5 ? Material.STONE :
                        y < surface - 1 ? Material.DIRT : Material.GRASS_BLOCK);
    }

    private int getIslandSurface(int x, int z, double distance, IslandLayout.Island island) {
        int radius = island.radius();
        int slopeRadius = getSlopeRadius(radius);
        double ocean = getLocalOceanHeight(x, z);

        if (distance >= slopeRadius) return (int) Math.round(ocean);

        if (distance <= radius) {
            double edgeFactor = 1.0D - distance / radius;
            double coastFactor = Math.pow(Math.max(0.0D, edgeFactor), 1.6D);
            double broadNoise = islandGen.noise(x, z, 0.5D, 0.5D, true) * islandVariation;
            double base = seaLevel + 2.0D + coastFactor * islandHeight + broadNoise;

            // Только на главном острове: один небольшой центральный холм.
            if (island.x() == 0 && island.z() == 0 && centralHillHeight > 0) {
                double hillDistance = Math.sqrt((double) x * x + (double) z * z);
                if (hillDistance < centralHillRadius) {
                    double hillFactor = 1.0D - hillDistance / centralHillRadius;
                    base += Math.pow(Math.max(0.0D, hillFactor), 2.0D) * centralHillHeight;
                }
            }
            return clamp((int) Math.round(base), seaLevel + 1, seaLevel + islandHeight + centralHillHeight + 2);
        }

        // Очень широкий плавный переход к воде без резкого обрыва.
        double t = (distance - radius) / (double) (slopeRadius - radius);
        t = t * t * (3.0D - 2.0D * t);
        double edge = seaLevel + 0.5D;
        return (int) Math.round(edge + (ocean - edge) * t);
    }

    private double getLocalOceanHeight(int x, int z) {
        return clampDouble(oceanBaseHeight
                        + terrainGen.noise(x, z, 0.5D, 0.5D, true) * oceanHeightAmplitude,
                24.0D, seaLevel - 1.0D);
    }

    private static double distance(int x, int z, int cx, int cz) {
        double dx = x - cx;
        double dz = z - cz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                for (IslandLayout.Island island : layout.get(worldInfo.getSeed())) {
                    int radius = island.radius();
                    if (distance(x, z, island.x(), island.z()) <= radius) return Biome.PLAINS;
                }
                return Biome.WARM_OCEAN;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(Biome.WARM_OCEAN, Biome.PLAINS);
            }
        };
    }

    @Override public boolean shouldGenerateNoise() { return true; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return true; }
    @Override public boolean shouldGenerateMobs() { return true; }
}
