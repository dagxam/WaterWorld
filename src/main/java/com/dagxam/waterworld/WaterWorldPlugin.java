package com.dagxam.waterworld;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public class WaterWorldPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Плагин WaterWorld от dagxam успешно запущен!");
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new WaterGenerator();
    }
}
