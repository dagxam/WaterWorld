package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;

/** Небольшой естественный холм на главном острове. */
public final class MountainDecorator {

    private final int seaLevel;
    private final int centerX;
    private final int centerZ;
    private final int radius;
    private final int peakHeight;
    private final int snowLine;
    private final boolean secondaryPeaks;

    public MountainDecorator(
            int seaLevel,
            int centerX,
            int centerZ,
            int radius,
            int peakHeight,
            int snowLine,
            boolean secondaryPeaks
    ) {
        this.seaLevel = seaLevel;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = Math.max(14, radius);
        this.peakHeight = Math.max(seaLevel + 8, peakHeight);
        this.snowLine = Math.max(peakHeight + 1, snowLine);
        this.secondaryPeaks = secondaryPeaks;
    }

    /**
     * Формирует только рельеф и поверхность холма.
     * Руды здесь намеренно не генерируются: их полностью создаёт Ore-Plugin
     * после формирования рельефа WaterWorld.
     */
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

                int baseY = world.getHighestBlockYAt(x, z);
                if (baseY <= seaLevel) continue;

                int targetY = getMountainHeight(x, z, distance);
                if (targetY <= baseY) continue;

                for (int y = baseY + 1; y <= targetY; y++) {
                    world.getBlockAt(x, y, z).setType(Material.STONE, false);
                }

                buildNaturalSurface(world, x, z, targetY);
            }
        }
    }

    private int getMountainHeight(int x, int z, double distance) {
        double factor = Math.max(0.0D, 1.0D - distance / radius);
        // Очень плавное основание и мягкая вершина: это холм, а не гора.
        double main = Math.pow(factor, 1.75D) * (peakHeight - seaLevel - 1);

        double secondary = 0.0D;
        if (secondaryPeaks) {
            secondary += gaussian(x, z, centerX - 13, centerZ + 5, 5.0D, 13.0D);
            secondary += gaussian(x, z, centerX + 15, centerZ - 5, 4.0D, 12.0D);
        }

        double gentleNoise = Math.sin(x * 0.075D + z * 0.021D) * 1.0D
                + Math.cos(z * 0.081D - x * 0.017D) * 0.8D;

        return Math.max(
                seaLevel + 1,
                Math.min(peakHeight, seaLevel + 1 + (int) Math.round(main + secondary + gentleNoise))
        );
    }

    private double gaussian(int x, int z, int peakX, int peakZ, double height, double width) {
        double dx = x - peakX;
        double dz = z - peakZ;
        return height * Math.exp(-(dx * dx + dz * dz) / (2.0D * width * width));
    }

    private void buildNaturalSurface(World world, int x, int z, int targetY) {
        int top = world.getHighestBlockYAt(x, z);
        if (top <= seaLevel) return;

        // Весь сухой верхний слой — дерн. Снег оставляем только при реально
        // заданной снеговой линии выше вершины холма.
        if (top >= snowLine) {
            world.getBlockAt(x, top, z).setType(Material.SNOW_BLOCK, false);
            if (world.getBlockAt(x, top + 1, z).isEmpty()) {
                world.getBlockAt(x, top + 1, z).setType(Material.SNOW, false);
            }
        } else {
            world.getBlockAt(x, top, z).setType(Material.GRASS_BLOCK, false);
            if (world.getBlockAt(x, top + 1, z).isEmpty()
                    && ((x * 31L + z * 17L) & 3L) == 0L) {
                world.getBlockAt(x, top + 1, z).setType(Material.GRASS, false);
            }
        }
    }
}
