package com.dagxam.waterworld;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Детерминированное расположение главного и дополнительных островов. */
public final class IslandLayout {
    public record Island(int x, int z, int radius, boolean main) {}

    private final int mainX, mainZ, mainRadius;
    private final int count, minDistance, maxDistance, extraRadius;
    private long seed = Long.MIN_VALUE;
    private List<Island> islands = List.of();

    public IslandLayout(FileConfiguration config) {
        mainX = config.getInt("island.center-x", 0);
        mainZ = config.getInt("island.center-z", 0);
        mainRadius = Math.max(16, config.getInt("island.radius", 100));
        count = Math.max(1, config.getInt("additional-islands.count", 1));
        minDistance = Math.max(mainRadius + 32, config.getInt("additional-islands.min-distance", 180));
        maxDistance = Math.max(minDistance + 16, config.getInt("additional-islands.max-distance", 420));
        extraRadius = Math.max(24, config.getInt("additional-islands.radius", 64));
    }

    public synchronized List<Island> get(long worldSeed) {
        if (seed == worldSeed && !islands.isEmpty()) return islands;
        Random random = new Random(worldSeed ^ 0x5DEECE66DL);
        List<Island> result = new ArrayList<>();
        result.add(new Island(mainX, mainZ, mainRadius, true));
        for (int i = 0; i < count; i++) {
            Island candidate = null;
            for (int attempt = 0; attempt < 64; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                int distance = minDistance + random.nextInt(maxDistance - minDistance + 1);
                int x = mainX + (int) Math.round(Math.cos(angle) * distance);
                int z = mainZ + (int) Math.round(Math.sin(angle) * distance);
                boolean overlaps = false;
                for (Island other : result) {
                    long dx = x - other.x(), dz = z - other.z();
                    long min = (long) extraRadius + other.radius() + 48L;
                    if (dx * dx + dz * dz < min * min) { overlaps = true; break; }
                }
                if (!overlaps) { candidate = new Island(x, z, extraRadius, false); break; }
            }
            if (candidate != null) result.add(candidate);
        }
        seed = worldSeed;
        islands = Collections.unmodifiableList(result);
        return islands;
    }
}
