package com.dagxam.waterworld;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/** WaterWorld 3.0 main-world generator. Geometry is generated in WaterGenerator; population stays lightweight. */
public final class WaterWorldPlugin extends JavaPlugin implements Listener {
    private static final String GENERATOR_NAME = "WaterWorld";
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private WaterGenerator generator;
    private NaturalIslandDecorator vegetation;
    private VillageDecorator village;
    private MobDecorator mobs;
    private String worldName = "world";
    private boolean restartRequired, initialized;
    private BukkitTask timeCycleTask;

    @Override public void onLoad() {
        saveDefaultConfig();
        worldName = readLevelName();
        generator = new WaterGenerator(getConfig());
        restartRequired = registerMainWorldGenerator();
    }

    @Override public void onEnable() {
        if (restartRequired) {
            getLogger().warning("WaterWorld подготовил генератор основного мира. Перезапустите сервер.");
            Bukkit.shutdown();
            return;
        }
        configurePopulation();
        getServer().getPluginManager().registerEvents(this, this);
        if (mobs != null) getServer().getPluginManager().registerEvents(mobs, this);
    }

    @Override public void onDisable() {
        if (timeCycleTask != null) timeCycleTask.cancel();
        if (mobs != null) mobs.stop();
    }

    @Override public ChunkGenerator getDefaultWorldGenerator(String requestedWorld, String id) {
        if (generator == null) generator = new WaterGenerator(getConfig());
        return generator;
    }

    private void configurePopulation() {
        vegetation = getConfig().getBoolean("vegetation.enabled", true) ? new NaturalIslandDecorator(getConfig(), generator.layout()) : null;
        int cx = getConfig().getInt("island.center-x", 0), cz = getConfig().getInt("island.center-z", 0);
        if (getConfig().getBoolean("village.enabled", true)) village = new VillageDecorator(cx, cz,
                getConfig().getInt("island.radius", 100), getConfig().getInt("village.offset-x", 0), getConfig().getInt("village.offset-z", 55));
        if (getConfig().getBoolean("animals.enabled", true)) {
            mobs = new MobDecorator(this, getConfig(), generator.layout());
        }
    }

    @EventHandler public void onServerLoad(ServerLoadEvent event) {
        if (initialized || event.getType() != ServerLoadEvent.LoadType.STARTUP) return;
        initialized = true;
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) world = Bukkit.getWorlds().getFirst();
        if (world == null || world.getGenerator() == null) {
            getLogger().severe("Основной мир WaterWorld не был загружен с генератором. Сервер остановлен."); Bukkit.shutdown(); return;
        }
        worldName = world.getName();
        generateSpawnArea(world);
        setIslandSpawn(world);
        startCustomTimeCycle(world);
        if (mobs != null) mobs.initializeWorld(world);
        getLogger().info("WaterWorld 3.0 готов: " + worldName);
    }

    private String readLevelName() {
        Properties p = new Properties(); File file = new File(serverRoot(), "server.properties");
        if (file.isFile()) try (Reader r = Files.newBufferedReader(file.toPath())) { p.load(r); } catch (IOException e) { getLogger().warning(e.getMessage()); }
        String name = p.getProperty("level-name", "world").trim(); return name.isEmpty() ? "world" : name;
    }

    private boolean registerMainWorldGenerator() {
        File file = new File(serverRoot(), "bukkit.yml"); YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection worlds = yml.getConfigurationSection("worlds"); if (worlds == null) worlds = yml.createSection("worlds");
        ConfigurationSection section = worlds.getConfigurationSection(worldName); if (section == null) section = worlds.createSection(worldName);
        if (GENERATOR_NAME.equalsIgnoreCase(section.getString("generator"))) return false;
        backupExistingWorld(); section.set("generator", GENERATOR_NAME);
        try { yml.save(file); return true; } catch (IOException e) { getLogger().severe("Не удалось записать bukkit.yml: " + e.getMessage()); return false; }
    }

    private void backupExistingWorld() {
        Path world = new File(serverRoot(), worldName).toPath(); if (!Files.isDirectory(world)) return;
        if (!Files.exists(world.resolve("level.dat")) && !Files.isDirectory(world.resolve("region"))) return;
        Path backup = world.resolveSibling(worldName + "-waterworld-backup-" + BACKUP_TIME.format(LocalDateTime.now()));
        try { try { Files.move(world, backup, StandardCopyOption.ATOMIC_MOVE); } catch (IOException ignored) { Files.move(world, backup); }
        } catch (IOException e) { getLogger().warning("Не удалось сохранить старый мир: " + e.getMessage()); }
    }

    private File serverRoot() { File plugins = getDataFolder().getParentFile(); File root = plugins == null ? null : plugins.getParentFile(); return root == null ? new File(".").getAbsoluteFile() : root; }

    private void generateSpawnArea(World world) {
        int cx = Math.floorDiv(getConfig().getInt("island.center-x", 0), 16), cz = Math.floorDiv(getConfig().getInt("island.center-z", 0), 16);
        int radius = Math.max(1, getConfig().getInt("spawn.preload-chunk-radius", 3));
        for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++) world.getChunkAt(x, z).load(true);
    }

    private void setIslandSpawn(World world) {
        int cx = getConfig().getInt("island.center-x", 0), cz = getConfig().getInt("island.center-z", 0);
        int[][] offsets = {{0,0},{8,0},{-8,0},{0,8},{0,-8},{12,12},{-12,12},{12,-12},{-12,-12}};
        for (int[] offset : offsets) {
            int x = cx + offset[0], z = cz + offset[1], y = world.getHighestBlockYAt(x, z);
            if (world.getBlockAt(x, y, z).getType() == Material.GRASS_BLOCK && world.getBlockAt(x, y + 1, z).isEmpty() && world.getBlockAt(x, y + 2, z).isEmpty()) { world.setSpawnLocation(x, y + 1, z); return; }
        }
        world.setSpawnLocation(cx, world.getHighestBlockYAt(cx, cz) + 1, cz);
    }

    private void startCustomTimeCycle(World world) {
        if (!getConfig().getBoolean("time-cycle.enabled", true)) return;
        int day = getConfig().getInt("time-cycle.day-duration-seconds", 600), night = getConfig().getInt("time-cycle.night-duration-seconds", 600);
        if (day <= 0 || night <= 0) return;
        long interval = Math.max(10L, getConfig().getLong("time-cycle.update-interval-ticks", 20L));
        double daySpeed = 12000.0D / (day * 20.0D), nightSpeed = 12000.0D / (night * 20.0D); final double[] remainder = {0.0D};
        world.setGameRuleValue("doDaylightCycle", "false");
        timeCycleTask = getServer().getScheduler().runTaskTimer(this, () -> {
            long time = Math.floorMod(world.getTime(), 24000L); remainder[0] += (time < 12000L ? daySpeed : nightSpeed) * interval;
            long advance = (long) remainder[0]; if (advance > 0) { remainder[0] -= advance; world.setTime((time + advance) % 24000L); }
        }, interval, interval);
    }

    @EventHandler public void onChunkPopulate(ChunkPopulateEvent event) {
        World world = event.getWorld(); if (!world.getName().equals(worldName)) return;
        Chunk chunk = event.getChunk();
        if (vegetation != null) vegetation.decorate(world, chunk.getX(), chunk.getZ());
        if (village != null && isVillageTriggerChunk(chunk.getX(), chunk.getZ())) village.generate(world);
        if (mobs != null) mobs.populate(chunk);
    }

    private boolean isVillageTriggerChunk(int chunkX, int chunkZ) {
        int x = getConfig().getInt("island.center-x", 0) + getConfig().getInt("village.offset-x", 0);
        int z = getConfig().getInt("island.center-z", 0) + getConfig().getInt("village.offset-z", 55);
        return chunkX == Math.floorDiv(x, 16) && chunkZ == Math.floorDiv(z, 16);
    }
}
