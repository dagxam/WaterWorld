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
 * The island is deliberately larger than the visible land area:
 * the outer part is a shallow underwater shelf, so the coast does not
 * end with a vertical wall.
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

    // Inner radius = actual land. Outer radius = underwater slope/shelf.
    private final int islandRadius;
    private final int islandSlopeRadius;
    private final int islandHeight;
    private final double islandVariation;
    private final double islandNoiseScale;

    private final boolean oresEnabled;
    private final int oreAttemptsPerChunk;

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

        islandRadius = Math.max(8, config.getInt("island.radius", 18));
        islandSlopeRadius = Math.max(
                islandRadius + 4,
                config.getInt("island.slope-radius", 32)
        );

        islandHeight = Math.max(2, config.getInt("island.height", 9));
        islandVariation = config.getDouble("island.variation", 1.6D);
        islandNoiseScale = config.getDouble("island.noise-scale", 0.07D);

        oresEnabled = config.getBoolean("ores.enabled", true);
        oreAttemptsPerChunk = Math.max(1, config.getInt("ores.attempts-per-chunk", 64));
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
    public void generateNoise(
            WorldInfo worldInfo,
            Random random,
            int chunkX,
            int chunkZ,
            ChunkData chunkData
    ) {
        ensureGenerators(worldInfo.getSeed());

        int minHeight = worldInfo.getMinHeight();
        int maxIslandY = seaLevel + islandHeight + 3;

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {

                int x = chunkX * 16 + localX;
                int z = chunkZ * 16 + localZ;

                double distance = distanceToIsland(x, z);

                int oceanFloor = getOceanFloor(worldInfo, x, z);

                /*
                 * Generate high enough to actually create the grass surface.
                 * The previous version stopped at sea level, which is why the
                 * island looked like exposed dirt instead of a real grassy island.
                 */
                for (int y = minHeight + 1; y <= maxIslandY; y++) {
                    if (islandEnabled && distance <= islandSlopeRadius) {
                        int islandSurface = getIslandSurface(x, z, distance);

                        if (distance <= islandSlopeRadius) {
                            setIslandTerrain(
                                    chunkData,
                                    localX,
                                    localZ,
                                    x,
                                    z,
                                    y,
                                    islandSurface,
                                    distance,
                                    oceanFloor
                            );
                            continue;
                        }
                    }

                    setOceanTerrain(
                            chunkData,
                            localX,
                            localZ,
                            x,
                            z,
                            y,
                            oceanFloor,
                            minHeight
                    );
                }

                chunkData.setBlock(localX, minHeight, localZ, Material.BEDROCK);
            }
        }

        // The vanilla generator is disabled for this custom world, so ores
        // must be generated explicitly. This keeps normal-looking ore veins
        // both under the ocean and inside the island's stone.
        if (oresEnabled) {
            generateOres(worldInfo, random, chunkX, chunkZ, chunkData);
        }
    }

    /**
     * Lightweight vanilla-like ore generation for our custom ChunkGenerator.
     *
     * The standard Minecraft ore step cannot run because this generator
     * deliberately disables vanilla noise/surface generation. We therefore
     * create deterministic small veins in STONE/DEEPSLATE ourselves.
     */
    private void generateOres(
            WorldInfo worldInfo,
            Random chunkRandom,
            int chunkX,
            int chunkZ,
            ChunkData data
    ) {
        long seed = worldInfo.getSeed()
                ^ ((long) chunkX * 341873128712L)
                ^ ((long) chunkZ * 132897987541L)
                ^ 0x6A09E667F3BCC909L;
        Random oreRandom = new Random(seed);

        // A few more attempts than the minimum because the custom world has
        // less stone near the surface than a vanilla Overworld.
        for (int i = 0; i < oreAttemptsPerChunk; i++) {
            int x = oreRandom.nextInt(16);
            int z = oreRandom.nextInt(16);
            int y = chooseOreY(oreRandom, worldInfo.getMinHeight(), seaLevel);

            OreDefinition ore = chooseOre(oreRandom, y);
            if (ore == null) {
                continue;
            }

            generateVein(data, oreRandom, x, y, z, ore);
        }
    }

    private int chooseOreY(Random random, int minHeight, int seaLevel) {
        // Weighted height selection roughly following the 1.20 Overworld
        // idea: common ores are spread broadly, rare ores prefer depth.
        int roll = random.nextInt(100);
        if (roll < 25) {
            return randomBetween(random, -16, 96);       // coal / iron / copper
        }
        if (roll < 50) {
            return randomBetween(random, -32, 48);       // iron / copper / gold
        }
        if (roll < 72) {
            return randomBetween(random, -64, 32);       // coal / iron / gold
        }
        if (roll < 90) {
            return randomBetween(random, -64, 16);       // redstone / lapis / diamond
        }
        return randomBetween(random, -64, 0);            // rare deep ores
    }

    private OreDefinition chooseOre(Random random, int y) {
        /*
         * Approximate vanilla-style distribution. The exact 1.20 ore engine
         * is data-driven and cannot be invoked after disabling vanilla noise,
         * so we keep the same idea: common ores are frequent, rare ores are
         * genuinely rare and deep ores use deepslate variants.
         */
        int roll = random.nextInt(10_000);

        // Emerald is intentionally very rare.
        if (roll < 8 && y >= -16 && y <= 256) {
            return new OreDefinition(Material.EMERALD_ORE, 2, -16, 256);
        }

        if (y <= 16) {
            if (roll < 1200) return new OreDefinition(Material.DIAMOND_ORE, 3, -64, 16);
            if (roll < 2500) return new OreDefinition(Material.REDSTONE_ORE, 6, -64, 16);
            if (roll < 3300) return new OreDefinition(Material.LAPIS_ORE, 6, -64, 64);
            if (roll < 4300) return new OreDefinition(Material.GOLD_ORE, 6, -64, 32);
        }

        if (y <= 64) {
            if (roll < 5000) return new OreDefinition(Material.IRON_ORE, 8, -64, 256);
            if (roll < 6500) return new OreDefinition(Material.COAL_ORE, 10, 0, 192);
            if (roll < 7900) return new OreDefinition(Material.COPPER_ORE, 10, 0, 96);
        } else {
            // Above Y64, keep the distribution close to normal overworld
            // surface mining: mostly coal and iron, with copper lower down.
            if (roll < 6200) return new OreDefinition(Material.COAL_ORE, 10, 0, 192);
            if (roll < 9000) return new OreDefinition(Material.IRON_ORE, 8, -64, 256);
            if (roll < 9700 && y <= 96) {
                return new OreDefinition(Material.COPPER_ORE, 10, 0, 96);
            }
        }

        return null;
    }

    private void generateVein(
            ChunkData data,
            Random random,
            int centerX,
            int centerY,
            int centerZ,
            OreDefinition ore
    ) {
        if (centerY < ore.minY || centerY > ore.maxY) {
            return;
        }

        int size = Math.max(1, ore.veinSize);
        int placed = 0;

        for (int i = 0; i < size * 3; i++) {
            int x = centerX + random.nextInt(5) - 2;
            int y = centerY + random.nextInt(5) - 2;
            int z = centerZ + random.nextInt(5) - 2;

            if (x < 0 || x >= 16 || z < 0 || z >= 16) {
                continue;
            }

            if (y < ore.minY || y > ore.maxY) {
                continue;
            }

            Material current = data.getType(x, y, z);
            if (current != Material.STONE && current != Material.DEEPSLATE) {
                continue;
            }

            Material replacement = current == Material.DEEPSLATE
                    ? toDeepslateOre(ore.material)
                    : ore.material;

            data.setBlock(x, y, z, replacement);
            placed++;

            if (placed >= size) {
                break;
            }
        }
    }

    private Material toDeepslateOre(Material material) {
        switch (material) {
            case COAL_ORE: return Material.DEEPSLATE_COAL_ORE;
            case IRON_ORE: return Material.DEEPSLATE_IRON_ORE;
            case COPPER_ORE: return Material.DEEPSLATE_COPPER_ORE;
            case GOLD_ORE: return Material.DEEPSLATE_GOLD_ORE;
            case REDSTONE_ORE: return Material.DEEPSLATE_REDSTONE_ORE;
            case LAPIS_ORE: return Material.DEEPSLATE_LAPIS_ORE;
            case DIAMOND_ORE: return Material.DEEPSLATE_DIAMOND_ORE;
            case EMERALD_ORE: return Material.DEEPSLATE_EMERALD_ORE;
            default: return material;
        }
    }

    private int randomBetween(Random random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private static final class OreDefinition {
        private final Material material;
        private final int veinSize;
        private final int minY;
        private final int maxY;

        private OreDefinition(Material material, int veinSize, int minY, int maxY) {
            this.material = material;
            this.veinSize = veinSize;
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    private int getOceanFloor(WorldInfo info, int x, int z) {
        double noise = terrainGen.noise(x, z, 0.5D, 0.5D, true);

        return clamp(
                oceanBaseHeight + (int) Math.round(noise * oceanHeightAmplitude),
                info.getMinHeight() + 2,
                seaLevel - 1
        );
    }

    private void setOceanTerrain(
            ChunkData data,
            int localX,
            int localZ,
            int x,
            int z,
            int y,
            int floor,
            int minHeight
    ) {
        if (y > seaLevel) {
            data.setBlock(localX, y, localZ, Material.AIR);
            return;
        }

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

    private void setIslandTerrain(
            ChunkData data,
            int localX,
            int localZ,
            int x,
            int z,
            int y,
            int surface,
            double distance,
            int oceanFloor
    ) {
        /*
         * Land in the inner radius, underwater slope in the outer radius.
         * The surface smoothly goes from ~Y70 down toward the ocean floor.
         */
        if (y > surface) {
            if (y <= seaLevel && surface < seaLevel) {
                data.setBlock(localX, y, localZ, Material.WATER);
            } else {
                data.setBlock(localX, y, localZ, Material.AIR);
            }
            return;
        }

        if (y <= 0) {
            data.setBlock(localX, y, localZ, Material.DEEPSLATE);
            return;
        }

        /*
         * The underwater part remains mostly stone/sand, while actual land
         * gets a natural dirt/grass top.
         */
        if (surface <= seaLevel) {
            if (y < surface - 4) {
                data.setBlock(localX, y, localZ, Material.STONE);
            } else if (y < surface - 1) {
                data.setBlock(localX, y, localZ, Material.SANDSTONE);
            } else {
                data.setBlock(localX, y, localZ, Material.SAND);
            }
            return;
        }

        if (y < surface - 5) {
            data.setBlock(localX, y, localZ, Material.STONE);
        } else if (y < surface - 1) {
            data.setBlock(localX, y, localZ, Material.DIRT);
        } else {
            /*
             * Real grass block at the actual top.
             * The old generator never reached this Y because it stopped at
             * sea level, causing the screenshot's dirt-only surface.
             */
            data.setBlock(localX, y, localZ, Material.GRASS_BLOCK);
        }
    }

    private boolean isCave(int x, int y, int z) {
        return caveGen.noise(x, y, z, 0.5D, 0.5D, true) > caveThreshold;
    }

    private int getIslandSurface(int x, int z, double distance) {
        double inner = islandRadius;
        double outer = islandSlopeRadius;

        if (distance <= inner) {
            double factor = 1.0D - distance / inner;
            double variation =
                    islandGen.noise(x, z, 0.5D, 0.5D, true) * islandVariation;

            return clamp(
                    seaLevel + 1 + (int) Math.round(
                            Math.pow(Math.max(0, factor), 1.25D) * islandHeight
                                    + variation
                    ),
                    seaLevel + 1,
                    seaLevel + islandHeight + 2
            );
        }

        /*
         * Underwater shelf:
         * at the edge of the real island it is around sea level,
         * then it gradually descends to the normal ocean floor.
         */
        double t = (distance - inner) / (outer - inner);
        t = Math.max(0.0D, Math.min(1.0D, t));

        double edgeHeight = seaLevel - 1.0D;
        double oceanHeight = getLocalOceanHeight(x, z);

        // Smoothstep gives a much softer coast than linear interpolation.
        double smooth = t * t * (3.0D - 2.0D * t);

        return (int) Math.round(edgeHeight + (oceanHeight - edgeHeight) * smooth);
    }

    private double getLocalOceanHeight(int x, int z) {
        double noise = terrainGen.noise(x, z, 0.5D, 0.5D, true);
        return clampDouble(
                oceanBaseHeight + noise * oceanHeightAmplitude,
                24.0D,
                seaLevel - 1.0D
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

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                double distance = distanceToIsland(x, z);

                /*
                 * Only the actual dry land is PLAINS.
                 * The underwater slope remains WARM_OCEAN so underwater
                 * vegetation and ocean behavior stay natural.
                 */
                if (islandEnabled && distance <= islandRadius) {
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
        return true;
    }

    @Override
    public boolean shouldGenerateCaves() {
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
        return true;
    }
}
