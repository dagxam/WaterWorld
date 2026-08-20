package com.dagxam.waterworld;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.Random;

/**
 * Большая центральная гора главного острова.
 *
 * Широкое основание плавно переходит в равнину, есть две боковые вершины,
 * каменные склоны и естественная снежная верхушка.
 */
public final class MountainDecorator {

    private final int seaLevel;
    private final int centerX;
    private final int centerZ;
    private final int radius;
    private final int peakHeight;
    private final int snowLine;
    private final boolean secondaryPeaks;
    private final int oreAttempts;

    public MountainDecorator(
            int seaLevel,
            int centerX,
            int centerZ,
            int radius,
            int peakHeight,
            int snowLine,
            boolean secondaryPeaks,
            int oreAttempts
    ) {
        this.seaLevel = seaLevel;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = Math.max(20, radius);
        this.peakHeight = Math.max(seaLevel + 20, peakHeight);
        this.snowLine = Math.max(seaLevel + 20, snowLine);
        this.secondaryPeaks = secondaryPeaks;
        this.oreAttempts = Math.max(1, oreAttempts);
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

                // Цельный каменный массив — это важно и для руд, и для красивых склонов.
                for (int y = baseY + 1; y <= targetY; y++) {
                    world.getBlockAt(x, y, z).setType(Material.STONE, false);
                }

                buildNaturalSurface(world, x, z, targetY, distance);
            }
        }

        // Поскольку гора создаётся после ChunkGenerator, руды в её новом камне
        // тоже создаём здесь, чтобы гора не была пустой внутри.
        generateOres(world, chunkX, chunkZ);
    }

    private int getMountainHeight(int x, int z, double distance) {
        double factor = Math.max(0.0D, 1.0D - distance / radius);

        // Основной массив: широкий низ + заметно более крутая верхняя часть.
        double main = Math.pow(factor, 1.62D)
                * (peakHeight - seaLevel - 1);

        double secondary = 0.0D;
        if (secondaryPeaks) {
            secondary += gaussian(x, z, centerX - 28, centerZ + 1, 24.0D, 17.0D);
            secondary += gaussian(x, z, centerX + 27, centerZ - 3, 21.0D, 16.0D);
        }

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
            world.getBlockAt(x, top, z).setType(Material.SNOW_BLOCK, false);

            if (world.getBlockAt(x, top + 1, z).isEmpty()) {
                world.getBlockAt(x, top + 1, z).setType(Material.SNOW, false);
            }

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

        // Нижняя часть остаётся зелёной, а на крутых местах открывается камень.
        double steepness = distance / radius;
        boolean exposedStone = heightRatio > 0.38D
                && (Math.sin(x * 0.17D) + Math.cos(z * 0.13D)) > 0.55D;

        if (exposedStone || steepness > 0.82D) {
            world.getBlockAt(x, top, z).setType(Material.STONE, false);
        } else {
            world.getBlockAt(x, top, z).setType(Material.GRASS_BLOCK, false);
            if (top > seaLevel + 2 && world.getBlockAt(x, top + 1, z).isEmpty()) {
                world.getBlockAt(x, top + 1, z).setType(Material.GRASS, false);
            }
        }
    }

    /** Ванильноподобные руды внутри добавленной горы. */
    private void generateOres(World world, int chunkX, int chunkZ) {
        long seed = world.getSeed()
                ^ ((long) chunkX * 341873128712L)
                ^ ((long) chunkZ * 132897987541L)
                ^ 0x510E527FADE682D1L;
        Random random = new Random(seed);

        for (int i = 0; i < oreAttempts; i++) {
            int x = chunkX * 16 + random.nextInt(16);
            int z = chunkZ * 16 + random.nextInt(16);
            double dx = x - centerX;
            double dz = z - centerZ;
            if (dx * dx + dz * dz > (double) radius * radius) continue;

            int top = world.getHighestBlockYAt(x, z);
            if (top <= seaLevel + 5) continue;

            int y = chooseOreY(random, top);
            Material ore = chooseOre(random, y);
            if (ore == null) continue;

            int veinSize = 2 + random.nextInt(ore == Material.DIAMOND_ORE ? 3 : 7);
            for (int v = 0; v < veinSize * 3; v++) {
                int bx = x + random.nextInt(5) - 2;
                int by = y + random.nextInt(5) - 2;
                int bz = z + random.nextInt(5) - 2;

                if (by <= 0 || by >= top) continue;
                Material current = world.getBlockAt(bx, by, bz).getType();
                if (current != Material.STONE && current != Material.DEEPSLATE) continue;

                world.getBlockAt(bx, by, bz).setType(current == Material.DEEPSLATE
                        ? toDeepslateOre(ore)
                        : ore, false);
            }
        }
    }

    private int chooseOreY(Random random, int top) {
        int roll = random.nextInt(100);
        if (roll < 55) return 8 + random.nextInt(Math.max(1, Math.min(top - 8, 64)));
        if (roll < 85) return 20 + random.nextInt(Math.max(1, Math.min(top - 20, 80)));
        return 4 + random.nextInt(Math.max(1, Math.min(top - 4, 32)));
    }

    private Material chooseOre(Random random, int y) {
        int roll = random.nextInt(10000);

        if (y <= 16) {
            if (roll < 900) return Material.DIAMOND_ORE;
            if (roll < 2500) return Material.REDSTONE_ORE;
            if (roll < 3400) return Material.LAPIS_ORE;
            if (roll < 4500) return Material.GOLD_ORE;
        }

        if (y <= 64) {
            if (roll < 4700) return Material.IRON_ORE;
            if (roll < 6500) return Material.COAL_ORE;
            if (roll < 7900) return Material.COPPER_ORE;
        }

        if (roll < 6200) return Material.COAL_ORE;
        if (roll < 9000) return Material.IRON_ORE;
        if (roll < 9700 && y <= 96) return Material.COPPER_ORE;

        return null;
    }

    private Material toDeepslateOre(Material material) {
        switch (material) {
            case COAL_ORE: return Material.DEEPSLATE_COAL_ORE;
            case IRON_ORE: return Material.DEEPSLATE_IRON_ORE;
            case COPPER_ORE: return Material.DEEPSLATE_COPPER_ORE;
            case GOLD_ORE: return Material.DEEPSLATE_GOLD_ORE;
            case REDSTONE_ORE: return Material.DEEPSLATE_REDSTONE_ORE;
            case LAPIS_ORE: return Material.DEEPSLATE_LAPIS_ORE;
            case DIAMOND_ORE: return Material.DEEPSLATE_DIAMOND_ORE;
            default: return material;
        }
    }
}
