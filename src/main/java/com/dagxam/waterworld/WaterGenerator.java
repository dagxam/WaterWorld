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

        // 1. Плавный генератор рельефа дна
        // Масштаб 0.005D делает холмы очень широкими и гладкими
        SimplexOctaveGenerator terrainGen = new SimplexOctaveGenerator(new Random(worldInfo.getSeed()), 4);
        terrainGen.setScale(0.005D);

        // 2. Генератор пещер (извилистые подземные тоннели)
        SimplexOctaveGenerator caveGen = new SimplexOctaveGenerator(new Random(worldInfo.getSeed() + 1), 3);
        caveGen.setScale(0.015D);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int realX = chunkX * 16 + x;
                int realZ = chunkZ * 16 + z;

                // Получаем значение шума. Параметр "true" в конце включает идеальное сглаживание
                double terrainNoise = terrainGen.noise(realX, realZ, 0.5D, 0.5D, true);
                
                // Высота морского дна плавно колеблется от 23 до 47
                int groundLevel = (int) (35 + (terrainNoise * 12));

                // Бедрок в самом низу
                chunkData.setBlock(x, minHeight, z, Material.BEDROCK);

                for (int y = minHeight + 1; y <= seaLevel; y++) {
                    boolean isCave = false;

                    // Пещеры генерируются ТОЛЬКО если над ними есть минимум 6 блоков прочной породы
                    if (y < groundLevel - 6) {
                        double caveNoise = caveGen.noise(realX, y, realZ, 0.5D, 0.5D, true);
                        if (caveNoise > 0.60D) {
                            isCave = true;
                        }
                    }

                    if (y <= groundLevel) {
                        if (isCave) {
                            chunkData.setBlock(x, y, z, Material.CAVE_AIR);
                        } else {
                            // Распределяем слои пород как в ванильном океане
                            if (y <= deepslateLevel) {
                                chunkData.setBlock(x, y, z, Material.DEEPSLATE);
                            } else if (y < groundLevel - 3) {
                                chunkData.setBlock(x, y, z, Material.STONE);
                            } else if (y < groundLevel) {
                                // Обязательный слой песчаника, чтобы песок не проваливался под землю
                                chunkData.setBlock(x, y, z, Material.SANDSTONE);
                            } else {
                                // Само дно океана теперь состоит из ПЕСКА
                                chunkData.setBlock(x, y, z, Material.SAND);
                            }
                        }
                    } else {
                        // Чистая сплошная вода
                        chunkData.setBlock(x, y, z, Material.WATER);
                    }
                }
            }
        }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
                // Теплый океан обеспечивает красивый цвет воды и спавн рыбок
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
        return true; // Включает водоросли на песке и работу плагинов на руды
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false; // Отключаем ломаные ванильные пещеры
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
