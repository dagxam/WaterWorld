package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class WaterGenerator extends ChunkGenerator {

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        int minHeight = worldInfo.getMinHeight(); 
        int seaLevel = 63; 
        int deepslateLevel = 0; 

        // 1. Генератор для неровного дна (холмы и впадины)
        SimplexOctaveGenerator terrainGen = new SimplexOctaveGenerator(new Random(worldInfo.getSeed()), 8);
        terrainGen.setScale(0.008D);

        // 2. НОВОЕ: Генератор для пещер (Трехмерный шум)
        SimplexOctaveGenerator caveGen = new SimplexOctaveGenerator(new Random(worldInfo.getSeed()), 4);
        caveGen.setScale(0.03D); // Масштаб пещер (уменьшите число, чтобы сделать пещеры шире)

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int realX = chunkX * 16 + x;
                int realZ = chunkZ * 16 + z;

                // Вычисляем высоту морского дна в этой координате
                double terrainNoise = terrainGen.noise(realX, realZ, 0.5D, 0.5D);
                int groundLevel = (int) (30 + (terrainNoise * 15));

                // Устанавливаем бедрок в самом низу
                chunkData.setBlock(x, minHeight, z, Material.BEDROCK);

                // Заполняем столб блоков снизу вверх
                for (int y = minHeight + 1; y <= seaLevel; y++) {
                    
                    boolean isCave = false;
                    
                    // Пещеры могут генерироваться ТОЛЬКО глубоко под землей
                    // (минимум 4 блока сплошного камня/земли отделяют пещеру от воды)
                    if (y < groundLevel - 4) {
                        // Генерируем 3D-шум для текущего блока
                        double caveNoise = caveGen.noise(realX, y, realZ, 0.5D, 0.5D);
                        
                        // Если значение шума выше порога (0.55), создаем пустоту
                        if (caveNoise > 0.55D) {
                            isCave = true;
                        }
                    }

                    // Распределяем материалы
                    if (y <= deepslateLevel) {
                        // Глубинный сланец (с пещерами)
                        if (isCave) chunkData.setBlock(x, y, z, Material.CAVE_AIR);
                        else chunkData.setBlock(x, y, z, Material.DEEPSLATE);
                    } 
                    else if (y <= groundLevel - 3) {
                        // Обычный камень (с пещерами)
                        if (isCave) chunkData.setBlock(x, y, z, Material.CAVE_AIR);
                        else chunkData.setBlock(x, y, z, Material.STONE);
                    } 
                    else if (y <= groundLevel) {
                        // Поверхность дна (земля). Всегда сплошная, чтобы вода не утекла в пещеры.
                        chunkData.setBlock(x, y, z, Material.DIRT);
                    } 
                    else if (y <= seaLevel) {
                        // Вода. Всегда сплошная.
                        chunkData.setBlock(x, y, z, Material.WATER);
                    }
                }
            }
        }
    }

    // Теплый океан для предотвращения спавна льда и айсбергов
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
        // Обязательно true, чтобы Ore-Plugin наполнил камень рудами
        return true; 
    }

    @Override
    public boolean shouldGenerateCaves() {
        // Обязательно FALSE, ванильные пещеры нам больше не нужны, у нас теперь свои
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
