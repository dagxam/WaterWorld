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

/** WaterWorld replaces the main server world and never creates a second world. */
public final class WaterWorldPlugin extends JavaPlugin implements Listener {
    private static final String GENERATOR_NAME = "WaterWorld";
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private WaterGenerator generator;
    private NaturalIslandDecorator decorator;
    private MountainDecorator mountain;
    private CaveDecorator cave;
    private VillageDecorator village;
    private MobDecorator mobs;
    private String worldName = "world";
    private boolean restartRequired;
    private boolean initialized;
    private BukkitTask timeCycleTask;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        worldName = readLevelName();
        generator = new WaterGenerator(getConfig());
        restartRequired = registerMainWorldGenerator();
    }

    @Override
    public void onEnable() {
        if (restartRequired) {
            getLogger().warning("WaterWorld настроил основной мир '" + worldName + "' и сохранил старый мир в резервную копию.");
            getLogger().warning("Сервер сейчас остановится. Просто запустите его ещё раз — level-name менять не нужно.");
            Bukkit.shutdown();
            return;
        }
        configureDecorators();
        getServer().getPluginManager().registerEvents(this, this);
        if (mobs != null) getServer().getPluginManager().registerEvents(mobs, this);
        getLogger().info("WaterWorld ожидает создание основного мира '" + worldName + "'.");
    }

    @Override
    public void onDisable() {
        if (timeCycleTask != null) timeCycleTask.cancel();
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String requestedWorld, String id) {
        if (generator == null) generator = new WaterGenerator(getConfig());
        getLogger().info("WaterWorldGenerator подключён к миру '" + requestedWorld + "'.");
        return generator;
    }

    private void configureDecorators() {
        int sea = getConfig().getInt("sea-level", 63);
        int cx = getConfig().getInt("island.center-x", 0);
        int cz = getConfig().getInt("island.center-z", 0);
        int radius = getConfig().getInt("island.radius", 100);
        if (!getConfig().getBoolean("island.enabled", true)) return;
        decorator = new NaturalIslandDecorator(sea, cx, cz, radius);
        if (getConfig().getBoolean("island.mountain.enabled", true)) {
            int mx = cx + getConfig().getInt("island.mountain.offset-x", 0);
            int mz = cz + getConfig().getInt("island.mountain.offset-z", -22);
            int mr = getConfig().getInt("island.mountain.radius", 38);
            int peak = getConfig().getInt("island.mountain.peak-height", 92);
            mountain = new MountainDecorator(sea, mx, mz, mr, peak,
                    getConfig().getInt("island.mountain.snow-line", 120),
                    getConfig().getBoolean("island.mountain.secondary-peaks", false));
            if (getConfig().getBoolean("island.mountain.cave.enabled", true)) cave = new CaveDecorator(mx, mz, mr, sea, peak);
        }
        if (getConfig().getBoolean("village.enabled", true)) village = new VillageDecorator(cx, cz, radius,
                getConfig().getInt("village.offset-x", 0), getConfig().getInt("village.offset-z", 55));
        if (getConfig().getBoolean("animals.enabled", true)) mobs = new MobDecorator(this, sea, cx, cz, radius,
                getConfig().getInt("animals.ocean-radius", 280));
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (initialized || event.getType() != ServerLoadEvent.LoadType.STARTUP) return;
        initialized = true;
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) world = Bukkit.getWorlds().get(0);
        if (world == null) {
            getLogger().severe("Основной мир не найден после запуска.");
            return;
        }
        worldName = world.getName();
        if (world.getGenerator() == null) {
            getLogger().severe("Основной мир загружен без WaterGenerator. Сервер остановлен, чтобы не создавать ванильные чанки.");
            Bukkit.shutdown();
            return;
        }
        if (mobs != null) {
            mobs.initializeWorld(world);
            mobs.start();
        }
        generateSpawnIslandChunks(world);
        setIslandSpawn(world);
        startCustomTimeCycle(world);
        getLogger().info("WaterWorld успешно запущен. Основной мир: " + world.getName()
                + ", генератор: " + world.getGenerator().getClass().getName());
    }

    private String readLevelName() {
        Properties p = new Properties();
        File file = new File(serverRoot(), "server.properties");
        if (file.isFile()) {
            try (Reader reader = Files.newBufferedReader(file.toPath())) { p.load(reader); }
            catch (IOException e) { getLogger().warning("Не удалось прочитать server.properties: " + e.getMessage()); }
        }
        String name = p.getProperty("level-name", "world").trim();
        return name.isEmpty() ? "world" : name;
    }

    private boolean registerMainWorldGenerator() {
        File file = new File(serverRoot(), "bukkit.yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection worlds = yml.getConfigurationSection("worlds");
        if (worlds == null) worlds = yml.createSection("worlds");
        ConfigurationSection section = worlds.getConfigurationSection(worldName);
        if (section == null) section = worlds.createSection(worldName);
        if (GENERATOR_NAME.equalsIgnoreCase(section.getString("generator"))) return false;

        backupExistingWorld();
        section.set("generator", GENERATOR_NAME);
        try {
            yml.save(file);
            getLogger().info("Автоматически настроен bukkit.yml: worlds." + worldName + ".generator = " + GENERATOR_NAME);
            return true;
        } catch (IOException e) {
            getLogger().severe("Не удалось записать bukkit.yml: " + e.getMessage());
            return false;
        }
    }

    private void backupExistingWorld() {
        Path world = new File(serverRoot(), worldName).toPath();
        if (!Files.isDirectory(world)) return;
        if (!Files.exists(world.resolve("level.dat")) && !Files.isDirectory(world.resolve("region"))) return;
        Path backup = world.resolveSibling(worldName + "-waterworld-backup-" + BACKUP_TIME.format(LocalDateTime.now()));
        try {
            try { Files.move(world, backup, StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException ignored) { Files.move(world, backup); }
            getLogger().warning("Старый ванильный мир сохранён в: " + backup.getFileName());
        } catch (IOException e) {
            getLogger().warning("Не удалось сохранить старый мир в резервную копию: " + e.getMessage());
        }
    }

    private File serverRoot() {
        File plugins = getDataFolder().getParentFile();
        File root = plugins == null ? null : plugins.getParentFile();
        return root == null ? new File(".").getAbsoluteFile() : root;
    }

    private void generateSpawnIslandChunks(World world) {
        int radius = Math.max(16, getConfig().getInt("island.radius", 100));
        int cx = getConfig().getInt("island.center-x", 0), cz = getConfig().getInt("island.center-z", 0);
        int cr = Math.max(1, (radius + 31) / 16);
        int ccx = Math.floorDiv(cx, 16), ccz = Math.floorDiv(cz, 16);
        for (int x = ccx - cr; x <= ccx + cr; x++) for (int z = ccz - cr; z <= ccz + cr; z++) world.getChunkAt(x, z).load(true);
    }

    private void setIslandSpawn(World world) {
        int cx = getConfig().getInt("island.center-x", 0), cz = getConfig().getInt("island.center-z", 0);
        int radius = Math.min(32, Math.max(8, getConfig().getInt("island.radius", 100) / 4));
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            int x = cx + dx, z = cz + dz, y = world.getHighestBlockYAt(x, z);
            if (world.getBlockAt(x, y, z).getType() == Material.GRASS_BLOCK
                    && world.getBlockAt(x, y + 1, z).isEmpty() && world.getBlockAt(x, y + 2, z).isEmpty()) {
                world.setSpawnLocation(x, y + 1, z);
                getLogger().info("Точка появления установлена на острове: " + x + ", " + (y + 1) + ", " + z);
                return;
            }
        }
        getLogger().warning("Безопасная точка появления на острове не найдена.");
    }

    private void startCustomTimeCycle(World world) {
        if (!getConfig().getBoolean("time-cycle.enabled", true)) return;
        int day = getConfig().getInt("time-cycle.day-duration-seconds", 600);
        int night = getConfig().getInt("time-cycle.night-duration-seconds", 600);
        if (day <= 0 || night <= 0) return;
        long interval = Math.max(1L, getConfig().getLong("time-cycle.update-interval-ticks", 1L));
        double daySpeed = 12000.0D / (day * 20.0D), nightSpeed = 12000.0D / (night * 20.0D);
        final double[] remainder = {0.0D};
        world.setGameRuleValue("doDaylightCycle", "false");
        timeCycleTask = getServer().getScheduler().runTaskTimer(this, () -> {
            long time = Math.floorMod(world.getTime(), 24000L);
            remainder[0] += (time < 12000L ? daySpeed : nightSpeed) * interval;
            long advance = (long) remainder[0];
            if (advance <= 0) return;
            remainder[0] -= advance;
            world.setTime((time + advance) % 24000L);
        }, interval, interval);
    }

    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        World world = event.getWorld();
        if (!world.getName().equals(worldName)) return;
        Chunk chunk = event.getChunk();
        if (mountain != null) mountain.generate(world, chunk.getX(), chunk.getZ());
        if (cave != null) cave.generate(world, chunk.getX(), chunk.getZ());
        if (decorator != null && isChunkNearAnyIsland(chunk.getX(), chunk.getZ(), world.getSeed())) decorator.decorate(world, chunk.getX(), chunk.getZ());
        if (village != null && isVillageTriggerChunk(chunk.getX(), chunk.getZ())) village.generate(world);
        if (mobs != null) getServer().getScheduler().runTask(this, () -> mobs.populate(chunk));
    }

    private boolean isVillageTriggerChunk(int chunkX, int chunkZ) {
        int x = getConfig().getInt("island.center-x", 0) + getConfig().getInt("village.offset-x", 0);
        int z = getConfig().getInt("island.center-z", 0) + getConfig().getInt("village.offset-z", 55);
        return chunkX == Math.floorDiv(x, 16) && chunkZ == Math.floorDiv(z, 16);
    }

    private boolean isChunkNearAnyIsland(int chunkX, int chunkZ, long seed) {
        IslandLayout layout = new IslandLayout(getConfig());
        double x = chunkX * 16 + 8.0D, z = chunkZ * 16 + 8.0D;
        for (IslandLayout.Island island : layout.get(seed)) {
            int check = island.radius() + Math.max(20, island.radius() / 3) + 16;
            double dx = x - island.x(), dz = z - island.z();
            if (dx * dx + dz * dz <= (double) check * check) return true;
        }
        return false;
    }
}
