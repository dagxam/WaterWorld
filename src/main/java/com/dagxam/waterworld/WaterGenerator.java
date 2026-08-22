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
        layout = new IslandLayout(config);
    }

    private synchronized void ensureGenerators(long seed) {
        if (initializedSeed == seed && terrainGen != null) return;
        initializedSeed = seed;
        terrainGen = new SimplexOctaveGenerator(new Random(seed), 4); terrainGen.setScale(terrainScale);
        caveGen = new SimplexOctaveGenerator(new Random(seed + 1L), 3); caveGen.setScale(caveScale);
        islandGen = new SimplexOctaveGenerator(new Random(seed + 2L), 2); islandGen.setScale(islandNoiseScale);
    }

    @Override public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        ensureGenerators(info.getSeed());
        List<IslandLayout.Island> islands = layout.get(info.getSeed());
        int minHeight = info.getMinHeight(), maxY = seaLevel + islandHeight + 3;
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int x = chunkX * 16 + lx, z = chunkZ * 16 + lz;
            IslandLayout.Island island = nearestIsland(islands, x, z);
            double distance = island == null ? Double.MAX_VALUE : distance(x, z, island.x(), island.z());
            int slope = island == null ? 0 : island.radius() + Math.max(20, island.radius() / 3);
            int floor = getOceanFloor(info, x, z);
            for (int y = minHeight + 1; y <= maxY; y++) {
                if (islandEnabled && island != null && distance <= slope) {
                    setIslandTerrain(data, lx, lz, x, z, y, getIslandSurface(x, z, distance, island.radius(), slope), floor);
                } else setOceanTerrain(data, lx, lz, x, z, y, floor);
            }
            data.setBlock(lx, minHeight, lz, Material.BEDROCK);
        }
    }

    private IslandLayout.Island nearestIsland(List<IslandLayout.Island> islands, int x, int z) {
        IslandLayout.Island best = null; double bestDistance = Double.MAX_VALUE;
        for (IslandLayout.Island island : islands) {
            double d = distance(x, z, island.x(), island.z());
            if (d < bestDistance) { bestDistance = d; best = island; }
        }
        return best;
    }
    private int getOceanFloor(WorldInfo info, int x, int z) { return clamp(oceanBaseHeight + (int)Math.round(terrainGen.noise(x,z,0.5D,0.5D,true)*oceanHeightAmplitude), info.getMinHeight()+2, seaLevel-1); }
    private void setOceanTerrain(ChunkData d,int lx,int lz,int x,int z,int y,int floor) {
        if (y>seaLevel) d.setBlock(lx,y,lz,Material.AIR);
        else if (y>floor) d.setBlock(lx,y,lz,Material.WATER);
        else if (y<floor-caveRoof && isCave(x,y,z)) d.setBlock(lx,y,lz,Material.CAVE_AIR);
        else if (y<=0) d.setBlock(lx,y,lz,Material.DEEPSLATE);
        else if (y<floor-4) d.setBlock(lx,y,lz,Material.STONE);
        else if (y<floor-1) d.setBlock(lx,y,lz,Material.SANDSTONE);
        else d.setBlock(lx,y,lz,Material.SAND);
    }
    private void setIslandTerrain(ChunkData d,int lx,int lz,int x,int z,int y,int surface,int floor) {
        if (y>surface) { d.setBlock(lx,y,lz,y<=seaLevel&&surface<seaLevel?Material.WATER:Material.AIR); return; }
        if (y<=0) { d.setBlock(lx,y,lz,Material.DEEPSLATE); return; }
        if (surface<=seaLevel) { d.setBlock(lx,y,lz,y<surface-4?Material.STONE:y<surface-1?Material.SANDSTONE:Material.SAND); return; }
        d.setBlock(lx,y,lz,y<surface-5?Material.STONE:y<surface-1?Material.DIRT:Material.GRASS_BLOCK);
    }
    private boolean isCave(int x,int y,int z){return caveGen.noise(x,y,z,0.5D,0.5D,true)>caveThreshold;}
    private int getIslandSurface(int x,int z,double distance,int radius,int slopeRadius){
        if(distance<=radius){ double f=1D-distance/radius; double v=islandGen.noise(x,z,0.5D,0.5D,true)*islandVariation; return clamp(seaLevel+1+(int)Math.round(Math.pow(Math.max(0,f),1.25D)*islandHeight+v),seaLevel+1,seaLevel+islandHeight+2); }
        double t=Math.max(0D,Math.min(1D,(distance-radius)/(slopeRadius-radius))); t=t*t*(3D-2D*t);
        return (int)Math.round((seaLevel-1D)+(getLocalOceanHeight(x,z)-(seaLevel-1D))*t);
    }
    private double getLocalOceanHeight(int x,int z){return clampDouble(oceanBaseHeight+terrainGen.noise(x,z,0.5D,0.5D,true)*oceanHeightAmplitude,24D,seaLevel-1D);}
    private static double distance(int x,int z,int cx,int cz){double dx=x-cx,dz=z-cz;return Math.sqrt(dx*dx+dz*dz);}
    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    private static double clampDouble(double v,double min,double max){return Math.max(min,Math.min(max,v));}
    @Override public BiomeProvider getDefaultBiomeProvider(WorldInfo info){ return new BiomeProvider(){ public Biome getBiome(WorldInfo i,int x,int y,int z){ for(IslandLayout.Island island:layout.get(i.getSeed())) if(distance(x,z,island.x(),island.z())<=island.radius()) return Biome.PLAINS; return Biome.WARM_OCEAN;} public List<Biome> getBiomes(WorldInfo i){return List.of(Biome.WARM_OCEAN,Biome.PLAINS);}};}
    @Override public boolean shouldGenerateDecorations(){return true;}
    @Override public boolean shouldGenerateCaves(){return false;}
    @Override public boolean shouldGenerateNoise(){return false;}
    @Override public boolean shouldGenerateSurface(){return false;}
    @Override public boolean shouldGenerateMobs(){return true;}
}
