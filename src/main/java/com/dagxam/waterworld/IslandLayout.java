package com.dagxam.waterworld;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Fixed archipelago layout. The map is intentionally deterministic: one main island and
 * exactly two green secondary islands. No random rocky islands can appear.
 */
public final class IslandLayout {
    public enum IslandType { MAIN, FOREST, TROPICAL }

    public record Island(int x, int z, int radius, IslandType type) {
        public boolean main() { return type == IslandType.MAIN; }
    }

    private final int mainX, mainZ, mainRadius;
    private final Island forest;
    private final Island tropical;
    private volatile long seed = Long.MIN_VALUE;
    private volatile List<Island> islands = List.of();

    public IslandLayout(FileConfiguration config) {
        mainX = config.getInt("island.center-x", 0);
        mainZ = config.getInt("island.center-z", 0);
        mainRadius = Math.max(80, config.getInt("island.radius", 150));

        forest = new Island(
                config.getInt("additional-islands.forest.x", 0),
                config.getInt("additional-islands.forest.z", -220),
                Math.max(22, config.getInt("additional-islands.forest.radius", 35)),
                IslandType.FOREST
        );
        tropical = new Island(
                config.getInt("additional-islands.tropical.x", 170),
                config.getInt("additional-islands.tropical.z", 190),
                Math.max(22, config.getInt("additional-islands.tropical.radius", 30)),
                IslandType.TROPICAL
        );
    }

    public List<Island> get(long worldSeed) {
        if (seed == worldSeed && !islands.isEmpty()) return islands;
        synchronized (this) {
            if (seed != worldSeed || islands.isEmpty()) {
                islands = List.of(
                        new Island(mainX, mainZ, mainRadius, IslandType.MAIN),
                        forest,
                        tropical
                );
                seed = worldSeed;
            }
            return islands;
        }
    }
}
