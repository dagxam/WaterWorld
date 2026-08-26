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

/** Генерирует ровный океан, зелёные острова и один плавный центральный холм. */
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
        oceanHeightAmplitude = config.getInt("ocean.height-amplitude", 8);
        terrainScale = config.getDouble("ocean.terrain-scale", 0.005D);
        islandEnabled = config.getBoolean("island.enabled", true);

        // Единственный источник настроек холма. Старые mountain/peak-height больше не используются.
        centralHillHeight = Math.max(0, config.getInt("island.central-hill.height", 9));
        centralHillRadius = Math.max(8.0D, config.getDouble("island.central-hill.radius", 42.0D));
        centralHillOffsetX = config.getInt("island.central-hill.offset-x", 0);
        centralHillOffsetZ = config.getInt("island.central-hill.offset-z", -12);
        layout = new IslandLayout(config);
    }

    private synchronized void ensureGenerators(long seed) {
        if (initializedSeed == seed && terrainGen != null) return;
        initializedSeed = seed;
        terrainGen = new SimplexOctaveGenerator(new Random(seed), 4);
        terrainGen.setScale(terrainScale);
        islandGen = new SimplexOctaveGenerator(new Random(seed + 2L), 2);
        islandGen.setScale(0.045D);
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
                double distanceSq = island == null ? Double.MAX_VALUE : distanceSquared(x, z, island.x(), island.z());
                int floor = getOceanFloor(info, x, z);
                int surface = island == null ? floor : getIslandSurface(x, z, distanceSq, island);
                int fillTop = Math.max(surface, seaLevel);

                // ChunkData уже AIR по умолчанию: не выполняем дорогой ручной цикл очистки AIR.
                for (int y = minHeight + 1; y <= Math.min(maxHeight, fillTop); y++) {
                    if (island != null && distanceSq <= slopeRadiusSquared(island.radius())) {
                        setIslandTerrain(data, lx, lz, y, surface);
                    } else {
                        setOceanTerrain(data, lx, lz, y, floor);
                    }
                }
                data.setBlock(lx, minHeight, lz, Material.BEDROCK);
            }
        }
    }

    private IslandLayout.Island nearestIsland(List<IslandLayout.Island> islands, int x, int z) {
        IslandLayout.Island best = null;
        double bestDistance = Double.MAX_VALUE;
        for (IslandLayout.Island island : islands) {
            double d = distanceSquared(x, z, island.x(), island.z());
            if (d < bestDistance) { bestDistance = d; best = island; }
        }
        return best;
    }

    private int getSlopeRadius(int radius) { return radius + Math.max(18, radius / 2); }
    private double slopeRadiusSquared(int radius) { double r = getSlopeRadius(radius); return r * r; }

    private int getOceanFloor(WorldInfo info, int x, int z) {
        return clamp(oceanBaseHeight + (int) Math.round(terrainGen.noise(x, z, 0.5D, 0.5D, true) * oceanHeightAmplitude),
                info.getMinHeight() + 2, seaLevel - 1);
    }

    private void setOceanTerrain(ChunkData data, int lx, int lz, int y, int floor) {
        if (y > floor) data.setBlock(lx, y, lz, Material.WATER);
        else if (y <= 0) data.setBlock(lx, y, lz, Material.DEEPSLATE);
        else if (y < floor - 5) data.setBlock(lx, y, lz, Material.STONE);
        else if (y < floor - 2) data.setBlock(lx, y, lz, Material.GRAVEL);
        else data.setBlock(lx, y, lz, Material.SAND);
    }

    private void setIslandTerrain(ChunkData data, int lx, int lz, int y, int surface) {
        if (y > surface) {
            data.setBlock(lx, y, lz, y <= seaLevel ? Material.WATER : Material.AIR);
            return;
        }
        if (y <= 0) { data.setBlock(lx, y, lz, Material.DEEPSLATE); return; }
        if (surface <= seaLevel) {
            data.setBlock(lx, y, lz, y < surface - 4 ? Material.STONE : y < surface - 1 ? Material.SANDSTONE : Material.SAND);
            return;
        }
        data.setBlock(lx, y, lz, y < surface - 5 ? Material.STONE : y < surface - 1 ? Material.DIRT : Material.GRASS_BLOCK);
    }

    private int getIslandSurface(int x, int z, double distanceSq, IslandLayout.Island island) {
        int radius = island.radius();
        int slopeRadius = getSlopeRadius(radius);
        double slopeRadiusSq = (double) slopeRadius * slopeRadius;
        double ocean = getLocalOceanHeight(x, z);
        if (distanceSq >= slopeRadiusSq) return (int) Math.round(ocean);

        double distance = Math.sqrt(distanceSq);
        if (distance <= radius) {
            double edgeFactor = 1.0D - distance / radius;
            double coastFactor = Math.pow(Math.max(0.0D, edgeFactor), 1.8D);
            double broadNoise = islandGen.noise(x, z, 0.5D, 0.5D, true) * island.variation();
            double base = seaLevel + 1.0D + coastFactor * island.height() + broadNoise;

            // Только главный остров получает широкий невысокий холм, без каменного пика.
            if (island.main() && centralHillHeight > 0) {
                int hx = island.x() + centralHillOffsetX;
                int hz = island.z() + centralHillOffsetZ;
                double dx = x - hx, dz = z - hz;
                double hillDistanceSq = dx * dx + dz * dz;
                if (hillDistanceSq < centralHillRadius * centralHillRadius) {
                    double hillFactor = 1.0D - Math.sqrt(hillDistanceSq) / centralHillRadius;
                    // Smooth dome: широкий холм, а не острый конус.
                    base += hillFactor * hillFactor * (3.0D - 2.0D * hillFactor) * centralHillHeight;
                }
            }
            return clamp((int) Math.round(base), seaLevel + 1, seaLevel + island.height() + centralHillHeight + 2);
        }

        // Плавная подводная отмель без обрыва.
        double t = (distance - radius) / (double) (slopeRadius - radius);
        t = t * t * (3.0D - 2.0D * t);
        return (int) Math.round((seaLevel + 0.25D) + (ocean - (seaLevel + 0.25D)) * t);
    }

    private double getLocalOceanHeight(int x, int z) {
        return clampDouble(oceanBaseHeight + terrainGen.noise(x, z, 0.5D, 0.5D, true) * oceanHeightAmplitude,
                24.0D, seaLevel - 1.0D);
    }

    private static double distanceSquared(int x, int z, int cx, int cz) {
        double dx = x - cx, dz = z - cz;
        return dx * dx + dz * dz;
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static double clampDouble(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                for (IslandLayout.Island island : layout.get(worldInfo.getSeed())) {
                    if (distanceSquared(x, z, island.x(), island.z()) <= (double) island.radius() * island.radius()) return Biome.PLAINS;
                }
                return Biome.WARM_OCEAN;
            }
            @Override public List<Biome> getBiomes(WorldInfo worldInfo) { return List.of(Biome.WARM_OCEAN, Biome.PLAINS); }
        };
    }

    @Override public boolean shouldGenerateNoise() { return true; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return true; }
    @Override public boolean shouldGenerateMobs() { return true; }
}
