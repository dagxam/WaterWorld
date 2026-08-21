package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;

/**
 * Создаёт небольшую естественную пещерную систему внутри холма.
 * Пещера детерминирована по seed мира и генерируется только в чанках,
 * пересекающих холм, поэтому не требует фоновых задач и не нагружает весь мир.
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
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        if (maxX < centerX - radius || minX > centerX + radius
                || maxZ < centerZ - radius || minZ > centerZ + radius) {
            return;
        }

        // Главная камера в центре холма.
        carveEllipsoid(world, centerX, seaLevel + 13, centerZ, 7.5, 5.0, 7.5);

        // Наклонный вход с поверхности к основной камере.
        int entranceX = centerX + radius / 2;
        int entranceZ = centerZ + radius / 3;
        int entranceY = Math.min(maxHeight - 2, world.getHighestBlockYAt(entranceX, entranceZ));
        carveTunnel(world, entranceX, entranceY - 1, entranceZ,
                centerX + 5, seaLevel + 15, centerZ + 4, 2.1);

        // Две небольшие боковые ветки.
        carveTunnel(world, centerX - 4, seaLevel + 13, centerZ - 4,
                centerX - radius / 2, seaLevel + 9, centerZ - radius / 3, 1.8);
        carveTunnel(world, centerX + 4, seaLevel + 14, centerZ + 2,
                centerX + radius / 3, seaLevel + 20, centerZ - radius / 2, 1.7);

        // Небольшие камеры, чтобы пещера не выглядела одной прямой трубой.
        carveEllipsoid(world, centerX - radius / 3, seaLevel + 10, centerZ - radius / 4, 4.2, 3.0, 4.2);
        carveEllipsoid(world, centerX + radius / 4, seaLevel + 19, centerZ - radius / 3, 3.8, 2.8, 3.8);
    }

    private void carveTunnel(World world,
                             int x1, int y1, int z1,
                             int x2, int y2, int z2,
                             double tunnelRadius) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) * 1.25));

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.round(x1 + dx * t);
            int y = (int) Math.round(y1 + dy * t);
            int z = (int) Math.round(z1 + dz * t);
            carveEllipsoid(world, x, y, z, tunnelRadius, tunnelRadius * 0.82, tunnelRadius);
        }
    }

    private void carveEllipsoid(World world, int cx, int cy, int cz,
                                double rx, double ry, double rz) {
        int minX = (int) Math.floor(cx - rx);
        int maxX = (int) Math.ceil(cx + rx);
        int minY = Math.max(6, (int) Math.floor(cy - ry));
        int maxY = Math.min(world.getMaxHeight() - 2, (int) Math.ceil(cy + ry));
        int minZ = (int) Math.floor(cz - rz);
        int maxZ = (int) Math.ceil(cz + rz);

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
