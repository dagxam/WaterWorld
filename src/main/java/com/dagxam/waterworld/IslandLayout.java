package com.dagxam.waterworld;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Детерминированное расположение главного и малых зелёных островов. */
public final class IslandLayout {
    public record Island(int x, int z, int radius, int height, double variation, boolean main) {}

    private final int mainX, mainZ, mainRadius, mainHeight;
    private final double mainVariation;
    private final int count, minDistance, maxDistance, extraRadius, extraHeight;
    private final double extraVariation;
    private final List<Island> configuredIslands;
    private long seed = Long.MIN_VALUE;
    private List<Island> islands = List.of();

    public IslandLayout(FileConfiguration config) {
        mainX = config.getInt("island.center-x", 0);
        mainZ = config.getInt("island.center-z", 0);
        mainRadius = Math.max(16, config.getInt("island.radius", 100));
        mainHeight = Math.max(2, config.getInt("island.height", 10));
        mainVariation = Math.max(0.0D, config.getDouble("island.variation", 1.8D));

        count = Math.max(0, config.getInt("additional-islands.count", 2));
        minDistance = Math.max(mainRadius + 64, config.getInt("additional-islands.min-distance", 240));
        maxDistance = Math.max(minDistance + 16, config.getInt("additional-islands.max-distance", 420));
        extraRadius = Math.max(12, config.getInt("additional-islands.radius", 28));
        extraHeight = Math.max(2, config.getInt("additional-islands.height", 5));
        extraVariation = Math.max(0.0D, config.getDouble("additional-islands.variation", 1.0D));

        List<Island> explicit = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("additional-islands.islands");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection island = section.getConfigurationSection(key);
                if (island == null || !island.getBoolean("enabled", true)) continue;
                int x = island.getInt("x", 0);
                int z = island.getInt("z", 0);
                int radius = Math.max(10, island.getInt("radius", extraRadius));
                int height = Math.max(2, island.getInt("height", extraHeight));
                double variation = Math.max(0.0D, island.getDouble("variation", extraVariation));
                explicit.add(new Island(x, z, radius, height, variation, false));
            }
        }
        configuredIslands = Collections.unmodifiableList(explicit);
    }

    public synchronized List<Island> get(long worldSeed) {
        if (seed == worldSeed && !islands.isEmpty()) return islands;

        List<Island> result = new ArrayList<>();
        result.add(new Island(mainX, mainZ, mainRadius, mainHeight, mainVariation, true));

        if (!configuredIslands.isEmpty()) {
            // Фиксированные острова: ровно те, что заданы в config.yml.
            for (Island island : configuredIslands) {
                if (doesNotOverlap(result, island)) result.add(island);
            }
        } else {
            // Резервный режим для старых конфигов.
            Random random = new Random(worldSeed ^ 0x5DEECE66DL);
            for (int i = 0; i < count; i++) {
                Island candidate = null;
                for (int attempt = 0; attempt < 96; attempt++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    int distance = minDistance + random.nextInt(maxDistance - minDistance + 1);
                    int x = mainX + (int) Math.round(Math.cos(angle) * distance);
                    int z = mainZ + (int) Math.round(Math.sin(angle) * distance);
                    Island test = new Island(x, z, extraRadius, extraHeight, extraVariation, false);
                    if (doesNotOverlap(result, test)) { candidate = test; break; }
                }
                if (candidate != null) result.add(candidate);
            }
        }

        seed = worldSeed;
        islands = Collections.unmodifiableList(result);
        return islands;
    }

    private static boolean doesNotOverlap(List<Island> existing, Island candidate) {
        for (Island other : existing) {
            long dx = (long) candidate.x() - other.x();
            long dz = (long) candidate.z() - other.z();
            long min = (long) candidate.radius() + other.radius() + 64L;
            if (dx * dx + dz * dz < min * min) return false;
        }
        return true;
    }
}
