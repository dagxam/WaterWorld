package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

public class WaterGenerator extends ChunkGenerator {

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        int minHeight = worldInfo.getMinHeight(); 
        int seaLevel = 63; 
        int groundLevel = 30; 
        int deepslateLevel = 0; 

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                
                chunkData.setBlock(x, minHeight, z, Material.BEDROCK);
                
                for (int y = minHeight + 1; y <= deepslateLevel; y++) {
                    chunkData.setBlock(x, y, z, Material.DEEPSLATE);
                }

                for (int y = deepslateLevel + 1; y <= groundLevel - 3; y++) {
                    chunkData.setBlock(x, y, z, Material.STONE);
                }

                for (int y = groundLevel - 2; y <= groundLevel; y++) {
                    chunkData.setBlock(x, y, z, Material.DIRT);
                }

                for (int y = groundLevel + 1; y <= seaLevel; y++) {
                    chunkData.setBlock(x, y, z, Material.WATER);
                }
            }
        }
    }
    
    @Override
    public boolean shouldGenerateDecorations() {
        return true; // Включаем, чтобы Ore-Plugin мог генерировать руды
    }

    @Override
    public boolean shouldGenerateCaves() {
        return true; 
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
