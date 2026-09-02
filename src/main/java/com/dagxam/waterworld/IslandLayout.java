package com.dagxam.waterworld;

import org.bukkit.configuration.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Главный остров фиксирован, маленькие острова детерминированно и случайно распределяются по большой области. */
public final class IslandLayout {
    public record Island(int x, int z, int radius, int height, double variation, boolean main, String flora) {}

    private final int mainX, mainZ, mainRadius, mainHeight;
    private final double mainVariation;
    private final int count, minDistance, maxDistance, extraRadius, extraHeight;
    private final double extraVariation;
    private long seed = Long.MIN_VALUE;
    private List<Island> islands = List.of();

    public IslandLayout(FileConfiguration config) {
        mainX = config.getInt("island.center-x", 0);
        mainZ = config.getInt("island.center-z", 0);
        mainRadius = Math.max(16, config.getInt("island.radius", 100));
        mainHeight = Math.max(2, config.getInt("island.height", 9));
        mainVariation = Math.max(0.0D, config.getDouble("island.variation", 1.2D));
        count = Math.max(8, Math.min(10, config.getInt("additional-islands.count", 9)));
        minDistance = Math.max(mainRadius + 200, config.getInt("additional-islands.min-distance", 800));
        maxDistance = Math.max(minDistance + 100, config.getInt("additional-islands.max-distance", 6000));
        extraRadius = Math.max(8, Math.min(24, config.getInt("additional-islands.radius", 18)));
        extraHeight = Math.max(2, config.getInt("additional-islands.height", 3));
        extraVariation = Math.max(0.0D, config.getDouble("additional-islands.variation", 0.8D));
    }

    public synchronized List<Island> get(long worldSeed) {
        if (seed == worldSeed && !islands.isEmpty()) return islands;

        List<Island> result = new ArrayList<>();
        result.add(new Island(mainX, mainZ, mainRadius, mainHeight, mainVariation, true, "main"));

        Random random = new Random(worldSeed ^ 0x5DEECE66DL);
        String[] floraTypes = {"forest", "jungle", "birch", "taiga", "savanna", "flower", "swamp", "dark_forest", "mushroom"};

        // Только главный остров фиксирован. Все остальные точки вычисляются из seed мира.
        // Они находятся далеко друг от друга и распределяются по большому радиусу вокруг центра.
        for (int i = 0; i < count; i++) {
            Island candidate = null;
            for (int attempt = 0; attempt < 500; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
                int x = mainX + (int) Math.round(Math.cos(angle) * distance);
                int z = mainZ + (int) Math.round(Math.sin(angle) * distance);
                int radius = Math.max(8, extraRadius + random.nextInt(7) - 3);
                int height = Math.max(2, extraHeight + random.nextInt(3) - 1);
                String flora = floraTypes[random.nextInt(floraTypes.length)];
                Island test = new Island(x, z, radius, height, extraVariation, false, flora);
                if (doesNotOverlap(result, test)) {
                    candidate = test;
                    break;
                }
            }
            if (candidate != null) result.add(candidate);
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
