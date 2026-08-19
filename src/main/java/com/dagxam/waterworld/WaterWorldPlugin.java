package com.dagxam.waterworld;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public class WaterWorldPlugin extends JavaPlugin {

    private WaterGenerator generator;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        generator = new WaterGenerator(getConfig());
        getLogger().info("WaterWorld 2.0 успешно запущен.");
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (generator == null) {
            generator = new WaterGenerator(getConfig());
        }
        return generator;
    }
}
