package com.dagxam.waterworld;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Deterministic cached island layout. No layout is recreated during chunk population. */
public final class IslandLayout {
    public enum IslandType { MAIN, FOREST, ROCKY, TROPICAL }

    public record Island(int x, int z, int radius, IslandType type) {
        public boolean main() { return type == IslandType.MAIN; }
    }

    private final int mainX, mainZ, mainRadius;
    private final int count, minDistance, maxDistance, minRadius, maxRadius;
    private long seed = Long.MIN_VALUE;
    private List<Island> islands = List.of();

    public IslandLayout(FileConfiguration config) {
        mainX = config.getInt("island.center-x", 0);
        mainZ = config.getInt("island.center-z", 0);
        mainRadius = Math.max(24, config.getInt("island.radius", 100));
        count = Math.max(0, config.getInt("additional-islands.count", 3));
        minDistance = Math.max(mainRadius + 64, config.getInt("additional-islands.min-distance", 180));
        maxDistance = Math.max(minDistance + 32, config.getInt("additional-islands.max-distance", 520));
        minRadius = Math.max(18, config.getInt("additional-islands.min-radius", 32));
        maxRadius = Math.max(minRadius, config.getInt("additional-islands.max-radius", 72));
    }

    public synchronized List<Island> get(long worldSeed) {
        if (seed == worldSeed && !islands.isEmpty()) return islands;
        Random random = new Random(worldSeed ^ 0x5DEECE66DL);
        List<Island> result = new ArrayList<>(count + 1);
        result.add(new Island(mainX, mainZ, mainRadius, IslandType.MAIN));

        for (int i = 0; i < count; i++) {
            Island candidate = null;
            for (int attempt = 0; attempt < 96; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                int distance = minDistance + random.nextInt(maxDistance - minDistance + 1);
                int radius = minRadius + random.nextInt(maxRadius - minRadius + 1);
                int x = mainX + (int) Math.round(Math.cos(angle) * distance);
                int z = mainZ + (int) Math.round(Math.sin(angle) * distance);
                IslandType type = switch (random.nextInt(3)) {
                    case 0 -> IslandType.FOREST;
                    case 1 -> IslandType.ROCKY;
                    default -> IslandType.TROPICAL;
                };
                boolean overlaps = false;
                for (Island other : result) {
                    long dx = (long) x - other.x();
                    long dz = (long) z - other.z();
                    long min = (long) radius + other.radius() + 48L;
                    if (dx * dx + dz * dz < min * min) { overlaps = true; break; }
                }
                if (!overlaps) { candidate = new Island(x, z, radius, type); break; }
            }
            if (candidate != null) result.add(candidate);
        }
        seed = worldSeed;
        islands = Collections.unmodifiableList(result);
        return islands;
    }
}
