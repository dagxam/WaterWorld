package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;

/**
 * Создаёт одну большую естественную гору на главном острове.
 * Основание плавно переходит в равнину, вершина покрывается снегом.
 */
public final class MountainDecorator {

    private final int seaLevel;
    private final int centerX;
    private final int centerZ;
    private final int radius;
    private final int peakHeight;

    public MountainDecorator(int seaLevel, int centerX, int centerZ, int radius, int peakHeight) {
        this.seaLevel = seaLevel;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = Math.max(12, radius);
        this.peakHeight = Math.max(seaLevel + 20, peakHeight);
    }

    public void generate(World world, int chunkX, int chunkZ) {
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        int startX = Math.max(minX, centerX - radius);
        int endX = Math.min(maxX, centerX + radius);
        int startZ = Math.max(minZ, centerZ - radius);
        int endZ = Math.min(maxZ, centerZ + radius);

        if (startX > endX || startZ > endZ) return;

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                double dx = x - centerX;
                double dz = z - centerZ;
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > radius) continue;

                double factor = 1.0D - distance / radius;
                double smooth = factor * factor * (3.0D - 2.0D * factor);

                // Небольшая асимметрия делает гору менее искусственной.
                double ridge = 1.0D + 0.06D * Math.sin(x * 0.085D) * Math.cos(z * 0.071D);
                int baseY = world.getHighestBlockYAt(x, z);
                int targetY = seaLevel + 1 + (int) Math.round((peakHeight - seaLevel) * smooth * ridge);
                targetY = Math.max(baseY, targetY);

                for (int y = baseY + 1; y <= targetY; y++) {
                    Material block;
                    double heightRatio = targetY <= seaLevel + 1
                            ? 0.0D
                            : (double) (y - seaLevel) / (double) (targetY - seaLevel);

                    if (heightRatio >= 0.78D) {
                        block = Material.SNOW_BLOCK;
                    } else if (heightRatio >= 0.58D) {
                        block = Material.STONE;
                    } else if (heightRatio >= 0.30D) {
                        block = Material.STONE;
                    } else {
                        block = Material.DIRT;
                    }

                    world.getBlockAt(x, y, z).setType(block, false);
                }

                // Реалистичная поверхность: трава до снеговой зоны,
                // снеговые блоки на холодной верхней части.
                int top = world.getHighestBlockYAt(x, z);
                if (top > seaLevel) {
                    double topRatio = (double) (top - seaLevel) / Math.max(1, peakHeight - seaLevel);
                    Material surface = topRatio >= 0.72D ? Material.SNOW_BLOCK : Material.GRASS_BLOCK;
                    world.getBlockAt(x, top, z).setType(surface, false);

                    // Слой снега поверх снежной вершины.
                    if (topRatio >= 0.78D && world.getBlockAt(x, top + 1, z).isEmpty()) {
                        world.getBlockAt(x, top + 1, z).setType(Material.SNOW, false);
                    }
                }
            }
        }
    }
}
