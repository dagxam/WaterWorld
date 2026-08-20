package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;

/**
 * Большая центральная гора главного острова.
 *
 * Форма сделана не конусом: широкое основание плавно поднимается из равнины,
 * есть две боковые вершины, каменные склоны и снежная верхушка.
 */
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
        this.radius = Math.max(20, radius);
        this.peakHeight = Math.max(seaLevel + 20, peakHeight);
        this.snowLine = Math.max(seaLevel + 20, snowLine);
        this.secondaryPeaks = secondaryPeaks;
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

                int targetY = getMountainHeight(x, z, distance);
                int baseY = world.getHighestBlockYAt(x, z);
                if (targetY <= baseY) continue;

                // Внутри горы — цельный каменный массив.
                for (int y = baseY + 1; y <= targetY; y++) {
                    world.getBlockAt(x, y, z).setType(Material.STONE, false);
                }

                buildNaturalSurface(world, x, z, targetY, distance);
            }
        }
    }

    private int getMountainHeight(int x, int z, double distance) {
        double factor = Math.max(0.0D, 1.0D - distance / radius);

        // Широкий мягкий подъём. У вершины уклон становится круче.
        double main = Math.pow(factor, 1.62D)
                * (peakHeight - seaLevel - 1);

        double secondary = 0.0D;
        if (secondaryPeaks) {
            // Левая боковая вершина.
            secondary += gaussian(x, z, centerX - 28, centerZ + 1, 24.0D, 17.0D);
            // Правая боковая вершина.
            secondary += gaussian(x, z, centerX + 27, centerZ - 3, 21.0D, 16.0D);
        }

        // Небольшая неровность хребтов, без "ступенчатого" рельефа.
        double ridge =
                Math.sin(x * 0.071D + z * 0.023D) * 2.0D
                + Math.cos(z * 0.083D - x * 0.019D) * 1.5D;

        int result = seaLevel + 1 + (int) Math.round(main + secondary + ridge);
        return Math.max(seaLevel + 1, Math.min(peakHeight + 4, result));
    }

    private double gaussian(
            int x,
            int z,
            int peakX,
            int peakZ,
            double height,
            double width
    ) {
        double dx = x - peakX;
        double dz = z - peakZ;
        double distanceSquared = dx * dx + dz * dz;
        return height * Math.exp(-distanceSquared / (2.0D * width * width));
    }

    private void buildNaturalSurface(World world, int x, int z, int targetY, double distance) {
        int top = world.getHighestBlockYAt(x, z);
        if (top <= seaLevel) return;

        double heightRatio = (double) (top - seaLevel)
                / Math.max(1.0D, peakHeight - seaLevel);

        if (top >= snowLine) {
            // Снег занимает верхнюю часть горы, но не превращает её в плоскую
            // белую пирамиду: камень остаётся виден на крутых склонах.
            world.getBlockAt(x, top, z).setType(Material.SNOW_BLOCK, false);

            if (world.getBlockAt(x, top + 1, z).isEmpty()) {
                world.getBlockAt(x, top + 1, z).setType(Material.SNOW, false);
            }

            // Небольшой снеговой покров на соседних блоках.
            if (heightRatio > 0.86D) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        int bx = x + dx;
                        int bz = z + dz;
                        int by = world.getHighestBlockYAt(bx, bz);
                        if (by >= snowLine - 2 && world.getBlockAt(bx, by + 1, bz).isEmpty()) {
                            world.getBlockAt(bx, by + 1, bz).setType(Material.SNOW, false);
                        }
                    }
                }
            }
            return;
        }

        // Нижняя часть горы остаётся зелёной и постепенно переходит
        // в каменные открытые участки на крутых склонах.
        double steepness = distance / radius;
        boolean exposedStone = heightRatio > 0.38D &&
                (Math.sin(x * 0.17D) + Math.cos(z * 0.13D)) > 0.55D;

        if (exposedStone || steepness > 0.82D) {
            world.getBlockAt(x, top, z).setType(Material.STONE, false);
        } else {
            world.getBlockAt(x, top, z).setType(Material.GRASS_BLOCK, false);
            if (top > seaLevel + 2 && world.getBlockAt(x, top + 1, z).isEmpty()) {
                world.getBlockAt(x, top + 1, z).setType(Material.GRASS, false);
            }
        }
    }
}
