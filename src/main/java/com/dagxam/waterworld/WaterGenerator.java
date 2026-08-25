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

/** Optimized terrain generator: ocean, islands, mountain and caves are generated in one pass. */
public final class WaterGenerator extends ChunkGenerator {
    private final int seaLevel, oceanBaseHeight, oceanAmplitude;
    private final double terrainScale, islandNoiseScale, caveScale, caveThreshold;
    private final boolean islandEnabled, caveEnabled, mountainEnabled;
    private final int islandHeight, slopeRadius, mountainPeakHeight, snowLine;
    private final double islandVariation, mountainRadius, mountainStretchX, mountainStretchZ;
    private final int mountainX, mountainZ;
    private final IslandLayout layout;

    private long initializedSeed = Long.MIN_VALUE;
    private SimplexOctaveGenerator terrainGen, islandGen, caveGen, shapeGen;

    public WaterGenerator(FileConfiguration config) {
        seaLevel = config.getInt("sea-level", 63);
        oceanBaseHeight = config.getInt("ocean.base-height", 35);
        oceanAmplitude = Math.max(1, config.getInt("ocean.height-amplitude", 12));
        terrainScale = config.getDouble("ocean.terrain-scale", 0.005D);
        caveScale = config.getDouble("ocean.cave-scale", 0.018D);
        caveThreshold = config.getDouble("ocean.cave-threshold", 0.70D);
        caveEnabled = config.getBoolean("caves.enabled", true);
        islandEnabled = config.getBoolean("island.enabled", true);
        islandHeight = Math.max(3, config.getInt("island.height", 14));
        slopeRadius = Math.max(16, config.getInt("island.slope-radius", 42));
        islandVariation = config.getDouble("island.variation", 2.8D);
        islandNoiseScale = config.getDouble("island.noise-scale", 0.035D);
        int cx = config.getInt("island.center-x", 0);
        int cz = config.getInt("island.center-z", 0);
        mountainEnabled = config.getBoolean("island.mountain.enabled", true);
        mountainX = cx + config.getInt("island.mountain.offset-x", 0);
        mountainZ = cz + config.getInt("island.mountain.offset-z", -22);
        mountainPeakHeight = Math.max(seaLevel + islandHeight + 4, config.getInt("island.mountain.peak-height", 118));
        snowLine = Math.max(seaLevel + 12, config.getInt("island.mountain.snow-line", 102));
        mountainRadius = Math.max(12.0D, config.getDouble("island.mountain.radius", 48.0D));
        mountainStretchX = Math.max(0.6D, config.getDouble("island.mountain.stretch-x", 1.55D));
        mountainStretchZ = Math.max(0.6D, config.getDouble("island.mountain.stretch-z", 0.85D));
        layout = new IslandLayout(config);
    }

    public IslandLayout layout() { return layout; }
    public int seaLevel() { return seaLevel; }
    public int snowLine() { return snowLine; }

    private synchronized void ensureGenerators(long seed) {
        if (initializedSeed == seed) return;
        initializedSeed = seed;
        terrainGen = new SimplexOctaveGenerator(new Random(seed), 4); terrainGen.setScale(terrainScale);
        islandGen = new SimplexOctaveGenerator(new Random(seed + 1L), 3); islandGen.setScale(islandNoiseScale);
        caveGen = new SimplexOctaveGenerator(new Random(seed + 2L), 3); caveGen.setScale(caveScale);
        shapeGen = new SimplexOctaveGenerator(new Random(seed + 3L), 2); shapeGen.setScale(0.018D);
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        ensureGenerators(info.getSeed());
        List<IslandLayout.Island> islands = layout.get(info.getSeed());
        int minY = info.getMinHeight();
        int maxY = info.getMaxHeight() - 1;

        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int x = chunkX * 16 + lx;
            int z = chunkZ * 16 + lz;
            int oceanFloor = oceanFloor(info, x, z);
            IslandLayout.Island island = islandEnabled ? nearestIsland(islands, x, z) : null;
            int surface = island == null ? oceanFloor : islandSurface(x, z, oceanFloor, island);
            boolean islandColumn = island != null && surface > oceanFloor;
            int top = Math.min(maxY, Math.max(surface, seaLevel));

            for (int y = minY + 1; y <= top; y++) {
                Material block;
                if (y > surface) block = y <= seaLevel ? Material.WATER : Material.AIR;
                else if (islandColumn) block = islandMaterial(x, y, z, surface, island);
                else block = oceanMaterial(y, oceanFloor);
                if (caveEnabled && y > minY + 4 && y < surface - 5 && isCave(x, y, z, island)) block = Material.AIR;
                data.setBlock(lx, y, lz, block);
            }
            data.setBlock(lx, minY, lz, Material.BEDROCK);
        }
    }

    private IslandLayout.Island nearestIsland(List<IslandLayout.Island> islands, int x, int z) {
        IslandLayout.Island best = null; long bestSq = Long.MAX_VALUE;
        for (IslandLayout.Island island : islands) {
            long dx = (long) x - island.x(), dz = (long) z - island.z();
            long sq = dx * dx + dz * dz;
            long max = (long) (island.radius() + slopeRadius) * (island.radius() + slopeRadius);
            if (sq <= max && sq < bestSq) { bestSq = sq; best = island; }
        }
        return best;
    }

    private int oceanFloor(WorldInfo info, int x, int z) {
        double n = terrainGen.noise(x, z, 0.5D, 0.5D, true);
        double detail = shapeGen.noise(x, z, 0.5D, 0.5D, true) * 4.0D;
        return clamp((int) Math.round(oceanBaseHeight + n * oceanAmplitude + detail), info.getMinHeight() + 3, seaLevel - 2);
    }

    private int islandSurface(int x, int z, int oceanFloor, IslandLayout.Island island) {
        double dx = x - island.x(), dz = z - island.z();
        double distanceSq = dx * dx + dz * dz;
        double outer = island.radius() + slopeRadius;
        if (distanceSq >= outer * outer) return oceanFloor;
        double distance = Math.sqrt(distanceSq);
        if (distance <= island.radius()) {
            double edge = 1.0D - distance / island.radius();
            double broad = Math.pow(Math.max(0.0D, edge), 1.35D);
            double shape = shapeGen.noise(x, z, 0.5D, 0.5D, true) * 5.0D * broad;
            double noise = islandGen.noise(x, z, 0.5D, 0.5D, true) * islandVariation;
            double base = seaLevel + 2.0D + broad * islandHeight + noise + shape;
            if (island.main() && mountainEnabled) base += mountainContribution(x, z);
            return clamp((int) Math.round(base), seaLevel + 1, mountainEnabled && island.main() ? mountainPeakHeight : seaLevel + islandHeight + 8);
        }
        double t = (distance - island.radius()) / slopeRadius;
        t = t * t * (3.0D - 2.0D * t);
        return (int) Math.round((seaLevel + 1.0D) * (1.0D - t) + oceanFloor * t);
    }

    private double mountainContribution(int x, int z) {
        double dx = (x - mountainX) / (mountainRadius * mountainStretchX);
        double dz = (z - mountainZ) / (mountainRadius * mountainStretchZ);
        double q = dx * dx + dz * dz;
        if (q >= 1.0D) return 0.0D;
        double factor = Math.pow(1.0D - q, 1.55D);
        double noise = shapeGen.noise(x * 2, z * 2, 0.5D, 0.5D, true) * 5.0D * factor;
        int base = seaLevel + islandHeight;
        return Math.max(0.0D, (mountainPeakHeight - base) * factor + noise);
    }

    private boolean isCave(int x, int y, int z, IslandLayout.Island island) {
        double n1 = caveGen.noise(x, y, z, 0.5D, 0.5D, true);
        double n2 = caveGen.noise(x + 91, y - 37, z + 53, 0.5D, 0.5D, true);
        double threshold = island != null && island.main() ? caveThreshold + 0.04D : caveThreshold;
        return (n1 + n2 * 0.35D) > threshold;
    }

    private Material oceanMaterial(int y, int floor) {
        if (y <= 0) return Material.DEEPSLATE;
        if (y < floor - 5) return Material.STONE;
        if (y < floor - 2) return Material.SANDSTONE;
        if (floor <= seaLevel - 12 && y == floor) return Material.GRAVEL;
        return Material.SAND;
    }

    private Material islandMaterial(int x, int y, int z, int surface, IslandLayout.Island island) {
        if (y <= 0) return Material.DEEPSLATE;
        if (y < surface - 5) return Material.STONE;
        if (surface <= seaLevel + 2) return y < surface - 1 ? Material.SANDSTONE : Material.SAND;
        if (y < surface - 1) return Material.DIRT;
        if (island.main() && surface >= snowLine) return Material.SNOW_BLOCK;
        return Material.GRASS_BLOCK;
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                for (IslandLayout.Island island : layout.get(worldInfo.getSeed())) {
                    long dx = (long) x - island.x(), dz = (long) z - island.z();
                    long r = island.radius();
                    if (dx * dx + dz * dz <= r * r) return switch (island.type()) {
                        case MAIN -> Biome.PLAINS;
                        case FOREST -> Biome.FOREST;
                        case TROPICAL -> Biome.JUNGLE;
                    };
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
