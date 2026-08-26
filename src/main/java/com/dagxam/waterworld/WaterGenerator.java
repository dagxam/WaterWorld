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

/** Clean terrain generator: sandy ocean, green islands and one compact mountain massif. */
public final class WaterGenerator extends ChunkGenerator {
    private final int seaLevel, oceanBaseHeight, oceanAmplitude;
    private final double terrainScale, islandNoiseScale;
    private final boolean islandEnabled, caveEnabled, mountainEnabled;
    private final int islandHeight, forestIslandHeight, tropicalIslandHeight, slopeRadius, mountainPeakHeight, snowLine;
    private final double islandVariation, mountainRadius, mountainStretchX, mountainStretchZ;
    private final int mountainX, mountainZ;
    private final IslandLayout layout;

    private long initializedSeed = Long.MIN_VALUE;
    private SimplexOctaveGenerator terrainGen, islandGen, shapeGen, mountainGen;

    public WaterGenerator(FileConfiguration config) {
        seaLevel = config.getInt("sea-level", 62);
        oceanBaseHeight = config.getInt("ocean.base-height", 34);
        oceanAmplitude = Math.max(1, config.getInt("ocean.height-amplitude", 5));
        terrainScale = config.getDouble("ocean.terrain-scale", 0.004D);
        caveEnabled = config.getBoolean("caves.enabled", false);
        islandEnabled = config.getBoolean("island.enabled", true);
        islandHeight = Math.max(3, config.getInt("island.height", 18));
        forestIslandHeight = Math.max(3, config.getInt("additional-islands.forest.height", 9));
        tropicalIslandHeight = Math.max(3, config.getInt("additional-islands.tropical.height", 8));
        slopeRadius = Math.max(12, config.getInt("island.slope-radius", 24));
        islandVariation = config.getDouble("island.variation", 2.0D);
        islandNoiseScale = config.getDouble("island.noise-scale", 0.022D);
        int cx = config.getInt("island.center-x", 0);
        int cz = config.getInt("island.center-z", 0);
        mountainEnabled = config.getBoolean("island.mountain.enabled", true);
        mountainX = cx + config.getInt("island.mountain.offset-x", 0);
        mountainZ = cz + config.getInt("island.mountain.offset-z", -18);
        mountainPeakHeight = Math.max(seaLevel + islandHeight + 10, config.getInt("island.mountain.peak-height", 140));
        snowLine = Math.max(seaLevel + 25, config.getInt("island.mountain.snow-line", 118));
        mountainRadius = Math.max(18.0D, config.getDouble("island.mountain.radius", 42.0D));
        mountainStretchX = Math.max(0.6D, config.getDouble("island.mountain.stretch-x", 1.28D));
        mountainStretchZ = Math.max(0.6D, config.getDouble("island.mountain.stretch-z", 0.78D));
        layout = new IslandLayout(config);
    }

    public IslandLayout layout() { return layout; }
    public int seaLevel() { return seaLevel; }
    public int snowLine() { return snowLine; }

    private synchronized void ensureGenerators(long seed) {
        if (initializedSeed == seed) return;
        initializedSeed = seed;
        terrainGen = new SimplexOctaveGenerator(new Random(seed), 3);
        terrainGen.setScale(terrainScale);
        islandGen = new SimplexOctaveGenerator(new Random(seed + 1L), 2);
        islandGen.setScale(islandNoiseScale);
        shapeGen = new SimplexOctaveGenerator(new Random(seed + 3L), 2);
        shapeGen.setScale(0.012D);
        mountainGen = new SimplexOctaveGenerator(new Random(seed + 7L), 3);
        mountainGen.setScale(0.030D);
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        ensureGenerators(info.getSeed());
        List<IslandLayout.Island> islands = layout.get(info.getSeed());
        int minY = info.getMinHeight();
        int maxY = info.getMaxHeight() - 1;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = chunkX * 16 + lx;
                int z = chunkZ * 16 + lz;
                int oceanFloor = oceanFloor(info, x, z);
                IslandLayout.Island island = islandEnabled ? nearestIsland(islands, x, z) : null;
                int surface = island == null ? oceanFloor : islandSurface(x, z, oceanFloor, island);
                boolean islandColumn = island != null && surface > seaLevel;
                int top = Math.min(maxY, Math.max(surface, seaLevel));

                data.setBlock(lx, minY, lz, Material.BEDROCK);
                for (int y = minY + 1; y <= top; y++) {
                    Material block;
                    if (y > surface) {
                        block = y <= seaLevel ? Material.WATER : Material.AIR;
                    } else if (islandColumn) {
                        block = islandMaterial(y, surface, island);
                    } else {
                        block = oceanMaterial(y, oceanFloor);
                    }
                    if (caveEnabled && islandColumn && y > minY + 8 && y < surface - 8 && isLandCave(x, y, z, island)) {
                        block = Material.AIR;
                    }
                    data.setBlock(lx, y, lz, block);
                }
            }
        }
    }

    private IslandLayout.Island nearestIsland(List<IslandLayout.Island> islands, int x, int z) {
        IslandLayout.Island best = null;
        long bestSq = Long.MAX_VALUE;
        for (IslandLayout.Island island : islands) {
            long dx = (long) x - island.x();
            long dz = (long) z - island.z();
            long sq = dx * dx + dz * dz;
            long outer = (long) island.radius() + slopeRadius;
            if (sq <= outer * outer && sq < bestSq) {
                best = island;
                bestSq = sq;
            }
        }
        return best;
    }

    private int oceanFloor(WorldInfo info, int x, int z) {
        double broad = terrainGen.noise(x, z, 0.5D, 0.5D, true);
        double detail = shapeGen.noise(x, z, 0.5D, 0.5D, true) * 2.0D;
        return clamp((int) Math.round(oceanBaseHeight + broad * oceanAmplitude + detail), info.getMinHeight() + 5, seaLevel - 8);
    }

    private int islandSurface(int x, int z, int oceanFloor, IslandLayout.Island island) {
        double dx = x - island.x();
        double dz = z - island.z();
        double distanceSq = dx * dx + dz * dz;
        double outer = island.radius() + slopeRadius;
        if (distanceSq >= outer * outer) return oceanFloor;

        double distance = Math.sqrt(distanceSq);
        if (distance > island.radius()) {
            double t = (distance - island.radius()) / slopeRadius;
            t = smoothstep(t);
            return (int) Math.round((seaLevel + 2.0D) * (1.0D - t) + oceanFloor * t);
        }

        int localHeight = islandHeightFor(island);
        double variationMultiplier = island.main() ? 1.0D : 0.45D;
        double edge = 1.0D - distance / island.radius();
        double broad = edge * edge * (3.0D - 2.0D * edge);
        double edgeNoise = islandGen.noise(x, z, 0.5D, 0.5D, true) * islandVariation * variationMultiplier * (0.25D + broad);
        double base = seaLevel + 2.0D + broad * localHeight + edgeNoise;
        if (island.main() && mountainEnabled) base += mountainContribution(x, z);
        int maxSurface = island.main() ? mountainPeakHeight : seaLevel + localHeight + 2;
        return clamp((int) Math.round(base), seaLevel + 1, maxSurface);
    }

    private int islandHeightFor(IslandLayout.Island island) {
        return switch (island.type()) {
            case MAIN -> islandHeight;
            case FOREST -> forestIslandHeight;
            case TROPICAL -> tropicalIslandHeight;
        };
    }

    private double mountainContribution(int x, int z) {
        double dx = (x - mountainX) / (mountainRadius * mountainStretchX);
        double dz = (z - mountainZ) / (mountainRadius * mountainStretchZ);
        double q = dx * dx + dz * dz;
        if (q >= 1.0D) return 0.0D;

        double dome = 1.0D - smoothstep(q);
        double ridge = Math.max(0.0D, 1.0D - Math.abs(dz) * 0.55D);
        double noise = mountainGen.noise(x, z, 0.5D, 0.5D, true) * 6.0D * dome;
        double base = seaLevel + islandHeight;
        return Math.max(0.0D, (mountainPeakHeight - base) * dome * (0.82D + 0.18D * ridge) + noise);
    }

    private boolean isLandCave(int x, int y, int z, IslandLayout.Island island) {
        if (!island.main()) return false;
        double a = mountainGen.noise(x, y, z, 0.5D, 0.5D, true);
        double b = mountainGen.noise(x + 71, y - 29, z + 43, 0.5D, 0.5D, true);
        return a + b * 0.30D > 0.82D;
    }

    private Material oceanMaterial(int y, int floor) {
        if (y <= 0) return Material.DEEPSLATE;
        if (y < floor - 6) return Material.STONE;
        if (y < floor - 2) return Material.SANDSTONE;
        if (floor <= seaLevel - 15 && y == floor) return Material.GRAVEL;
        return Material.SAND;
    }

    private Material islandMaterial(int y, int surface, IslandLayout.Island island) {
        if (y <= 0) return Material.DEEPSLATE;
        if (surface <= seaLevel + 3) return y < surface - 2 ? Material.SANDSTONE : Material.SAND;
        if (y == surface) {
            if (island.main() && surface >= snowLine) return Material.SNOW_BLOCK;
            if (island.main() && surface >= snowLine - 8) return Material.STONE;
            return Material.GRASS_BLOCK;
        }
        if (y >= surface - 6) return Material.DIRT;
        return Material.STONE;
    }

    private static double smoothstep(double value) {
        double t = Math.max(0.0D, Math.min(1.0D, value));
        return t * t * (3.0D - 2.0D * t);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                for (IslandLayout.Island island : layout.get(worldInfo.getSeed())) {
                    long dx = (long) x - island.x();
                    long dz = (long) z - island.z();
                    long r = island.radius();
                    if (dx * dx + dz * dz <= r * r) {
                        return switch (island.type()) {
                            case MAIN -> Biome.PLAINS;
                            case FOREST -> Biome.FOREST;
                            case TROPICAL -> Biome.JUNGLE;
                        };
                    }
                }
                return Biome.WARM_OCEAN;
            }
            @Override public List<Biome> getBiomes(WorldInfo worldInfo) {
                return List.of(Biome.WARM_OCEAN, Biome.PLAINS, Biome.FOREST, Biome.JUNGLE);
            }
        };
    }

    @Override public boolean shouldGenerateNoise() { return true; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return true; }
}
