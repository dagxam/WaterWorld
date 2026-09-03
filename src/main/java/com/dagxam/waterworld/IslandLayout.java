package com.dagxam.waterworld;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Фиксированный главный остров и 10–20 случайных малых островов. */
public final class IslandLayout {
    public record Island(int x, int z, int radius, int height, double variation, boolean main, String flora) {}

    private final int mainX, mainZ, mainRadius, mainHeight;
    private final double mainVariation;
    private final int minCount, maxCount, minDistance, maxDistance;
    private final int minRadius, maxRadius, minHeight, maxHeight;
    private final double extraVariation;
    private long seed = Long.MIN_VALUE;
    private List<Island> islands = List.of();

    public IslandLayout(FileConfiguration config) {
        mainX = config.getInt("island.center-x", 0);
        mainZ = config.getInt("island.center-z", 0);
        mainRadius = Math.max(16, config.getInt("island.radius", 100));
        mainHeight = Math.max(2, config.getInt("island.height", 9));
        mainVariation = Math.max(0.0D, config.getDouble("island.variation", 1.2D));
        minCount = Math.max(10, config.getInt("additional-islands.count-min", 10));
        maxCount = Math.max(minCount, config.getInt("additional-islands.count-max", 20));
        minDistance = Math.max(mainRadius + 200, config.getInt("additional-islands.min-distance", 700));
        maxDistance = Math.max(minDistance + 100, config.getInt("additional-islands.max-distance", 6000));
        minRadius = Math.max(8, config.getInt("additional-islands.radius-min", 15));
        maxRadius = Math.max(minRadius, config.getInt("additional-islands.radius-max", 24));
        minHeight = Math.max(2, config.getInt("additional-islands.height-min", 3));
        maxHeight = Math.max(minHeight, config.getInt("additional-islands.height-max", 5));
        extraVariation = Math.max(0.0D, config.getDouble("additional-islands.variation", 0.7D));
    }

    public synchronized List<Island> get(long worldSeed) {
        if (seed == worldSeed && !islands.isEmpty()) return islands;
        List<Island> result = new ArrayList<>();
        result.add(new Island(mainX, mainZ, mainRadius, mainHeight, mainVariation, true, "main"));

        Random random = new Random(worldSeed ^ 0x5DEECE66DL);
        int targetCount = minCount + random.nextInt(maxCount - minCount + 1);
        String[] floraTypes = {"forest", "jungle", "birch", "taiga", "savanna", "flower", "swamp", "dark_forest", "mushroom"};

        for (int i = 0; i < targetCount; i++) {
            Island candidate = null;
            for (int attempt = 0; attempt < 1000; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
                int x = mainX + (int) Math.round(Math.cos(angle) * distance);
                int z = mainZ + (int) Math.round(Math.sin(angle) * distance);
                int radius = minRadius + random.nextInt(maxRadius - minRadius + 1);
                int height = minHeight + random.nextInt(maxHeight - minHeight + 1);
                String flora = floraTypes[random.nextInt(floraTypes.length)];
                Island test = new Island(x, z, radius, height, extraVariation, false, flora);
                if (doesNotOverlap(result, test)) {
                    candidate = test;
                    break;
                }
            }
            if (candidate != null) result.add(candidate);
        }

        // Не допускаем молча меньше 10 островов из-за редкой коллизии координат.
        // На огромной области этого практически не требуется, но цикл гарантирует минимум.
        int safety = 0;
        while (result.size() - 1 < minCount && safety++ < 5000) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            int x = mainX + (int) Math.round(Math.cos(angle) * distance);
            int z = mainZ + (int) Math.round(Math.sin(angle) * distance);
            int radius = minRadius + random.nextInt(maxRadius - minRadius + 1);
            int height = minHeight + random.nextInt(maxHeight - minHeight + 1);
            String flora = floraTypes[random.nextInt(floraTypes.length)];
            Island test = new Island(x, z, radius, height, extraVariation, false, flora);
            if (doesNotOverlap(result, test)) result.add(test);
        }

        seed = worldSeed;
        islands = Collections.unmodifiableList(result);
        return islands;
    }

    private static boolean doesNotOverlap(List<Island> existing, Island candidate) {
        for (Island other : existing) {
            long dx = (long) candidate.x() - other.x();
            long dz = (long) candidate.z() - other.z();
            long min = (long) candidate.radius() + other.radius() + 300L;
            if (dx * dx + dz * dz < min * min) return false;
        }
        return true;
    }
}
