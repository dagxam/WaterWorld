package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;

/** Декорации небольшого центрального холма: поверхность, снег и трава. */
public final class MountainDecorator {
    private final int seaLevel, centerX, centerZ, radius, peakHeight, snowLine;
    private final boolean secondaryPeaks;

    public MountainDecorator(int seaLevel, int centerX, int centerZ, int radius, int peakHeight, int snowLine, boolean secondaryPeaks) {
        this.seaLevel = seaLevel;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = Math.max(8, radius);
        this.peakHeight = Math.max(seaLevel + 2, peakHeight);
        this.snowLine = Math.max(peakHeight + 1, snowLine);
        this.secondaryPeaks = secondaryPeaks;
    }

    /**
     * В WaterGenerator уже создаётся единый рельеф острова и центрального холма.
     * Здесь больше не ставится масса горы отдельными блоками, поэтому не возникает
     * парящих гор и разрывов между чанками.
     */
    public void generate(World world, int chunkX, int chunkZ) {
        int minX = chunkX * 16, minZ = chunkZ * 16;
        int maxX = minX + 15, maxZ = minZ + 15;
        int startX = Math.max(minX, centerX - radius), endX = Math.min(maxX, centerX + radius);
        int startZ = Math.max(minZ, centerZ - radius), endZ = Math.min(maxZ, centerZ + radius);
        if (startX > endX || startZ > endZ) return;

        // Только декорация поверхности существующего рельефа.
        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                double dx = x - centerX, dz = z - centerZ;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius) continue;
                int top = world.getHighestBlockYAt(x, z);
                if (top <= seaLevel) continue;

                if (top >= snowLine) {
                    world.getBlockAt(x, top, z).setType(Material.SNOW_BLOCK, false);
                    if (world.getBlockAt(x, top + 1, z).isEmpty()) {
                        world.getBlockAt(x, top + 1, z).setType(Material.SNOW, false);
                    }
                } else {
                    world.getBlockAt(x, top, z).setType(Material.GRASS_BLOCK, false);
                    if (world.getBlockAt(x, top + 1, z).isEmpty() && ((x * 31L + z * 17L) & 3L) == 0L) {
                        world.getBlockAt(x, top + 1, z).setType(Material.SHORT_GRASS, false);
                    }
                }
            }
        }
    }
}
