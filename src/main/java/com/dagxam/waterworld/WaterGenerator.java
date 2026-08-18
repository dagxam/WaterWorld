package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Collections;
import java.util.List;
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

    // --- НОВОЕ: Заставляем весь мир быть теплым океаном без льда ---
    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                return Biome.WARM_OCEAN; 
            }

            @Override
            public List<Biome> getBiomes(WorldInfo worldInfo) {
                return Collections.singletonList(Biome.WARM_OCEAN);
            }
        };
    }
    
    @Override
    public boolean shouldGenerateDecorations() {
        return true; // Оставляем включенным для генерации руд
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
