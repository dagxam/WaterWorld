package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;

/**
 * Создаёт небольшую естественную пещерную систему внутри холма.
 * Все операции ограничиваются текущим чанком: соседние чанки не загружаются.
 */
public final class CaveDecorator {
    private final int centerX;
    private final int centerZ;
    private final int radius;
    private final int seaLevel;
    private final int maxHeight;

    public CaveDecorator(int centerX, int centerZ, int radius, int seaLevel, int maxHeight) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = Math.max(12, radius);
        this.seaLevel = seaLevel;
        this.maxHeight = maxHeight;
    }

    public void generate(World world, int chunkX, int chunkZ) {
        int minX = chunkX * 16, maxX = minX + 15;
        int minZ = chunkZ * 16, maxZ = minZ + 15;
        if (maxX < centerX - radius || minX > centerX + radius
                || maxZ < centerZ - radius || minZ > centerZ + radius) return;

        carveEllipsoid(world, minX, maxX, minZ, maxZ,
                centerX, seaLevel + 13, centerZ, 7.5, 5.0, 7.5);

        int entranceX = centerX + radius / 2;
        int entranceZ = centerZ + radius / 3;
        int entranceY = estimateHillSurfaceY(entranceX, entranceZ);
        carveTunnel(world, minX, maxX, minZ, maxZ,
                entranceX, entranceY - 1, entranceZ,
                centerX + 5, seaLevel + 15, centerZ + 4, 2.1);
        carveTunnel(world, minX, maxX, minZ, maxZ,
                centerX - 4, seaLevel + 13, centerZ - 4,
                centerX - radius / 2, seaLevel + 9, centerZ - radius / 3, 1.8);
        carveTunnel(world, minX, maxX, minZ, maxZ,
                centerX + 4, seaLevel + 14, centerZ + 2,
                centerX + radius / 3, seaLevel + 20, centerZ - radius / 2, 1.7);
        carveEllipsoid(world, minX, maxX, minZ, maxZ,
                centerX - radius / 3, seaLevel + 10, centerZ - radius / 4, 4.2, 3.0, 4.2);
        carveEllipsoid(world, minX, maxX, minZ, maxZ,
                centerX + radius / 4, seaLevel + 19, centerZ - radius / 3, 3.8, 2.8, 3.8);
    }

    private int estimateHillSurfaceY(int x, int z) {
        double dx = x - centerX, dz = z - centerZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double factor = Math.max(0.0D, 1.0D - distance / radius);
        double height = Math.pow(factor, 1.75D) * (maxHeight - seaLevel - 1);
        return seaLevel + 1 + (int) Math.round(height);
    }

    private void carveTunnel(World world, int minX, int maxX, int minZ, int maxZ,
                             int x1, int y1, int z1, int x2, int y2, int z2,
                             double r) {
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) * 1.25));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            carveEllipsoid(world, minX, maxX, minZ, maxZ,
                    (int) Math.round(x1 + dx * t),
                    (int) Math.round(y1 + dy * t),
                    (int) Math.round(z1 + dz * t), r, r * 0.82, r);
        }
    }

    private void carveEllipsoid(World world, int chunkMinX, int chunkMaxX, int chunkMinZ, int chunkMaxZ,
                                int cx, int cy, int cz, double rx, double ry, double rz) {
        int minX = Math.max(chunkMinX, (int) Math.floor(cx - rx));
        int maxX = Math.min(chunkMaxX, (int) Math.ceil(cx + rx));
        int minY = Math.max(6, (int) Math.floor(cy - ry));
        int maxY = Math.min(world.getMaxHeight() - 2, (int) Math.ceil(cy + ry));
        int minZ = Math.max(chunkMinZ, (int) Math.floor(cz - rz));
        int maxZ = Math.min(chunkMaxZ, (int) Math.ceil(cz + rz));
        for (int x = minX; x <= maxX; x++) {
            double nx = (x - cx) / rx;
            for (int z = minZ; z <= maxZ; z++) {
                double nz = (z - cz) / rz;
                for (int y = minY; y <= maxY; y++) {
                    double ny = (y - cy) / ry;
                    if (nx * nx + ny * ny + nz * nz > 1.0) continue;
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (type == Material.STONE || type == Material.DEEPSLATE
                            || type.name().endsWith("_ORE") || type == Material.DIRT) {
                        world.getBlockAt(x, y, z).setType(Material.CAVE_AIR, false);
                    }
                }
            }
        }
    }
}
