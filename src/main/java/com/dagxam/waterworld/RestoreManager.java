package com.dagxam.waterworld;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Запоминает разрушенные блоки и позволяет восстановить их вокруг игрока. */
public final class RestoreManager implements Listener, CommandExecutor, TabCompleter {
    private final WaterWorldPlugin plugin;
    private final Map<String, SavedBlock> history = new HashMap<>();
    private final File file;
    private final int defaultRadius;
    private final int maxRadius;
    private final boolean persistent;
    private BukkitTask saveTask;

    public RestoreManager(WaterWorldPlugin plugin) {
        this.plugin = plugin;
        defaultRadius = Math.max(1, plugin.getConfig().getInt("restore.radius", 32));
        maxRadius = Math.max(defaultRadius, plugin.getConfig().getInt("restore.max-radius", 64));
        persistent = plugin.getConfig().getBoolean("restore.persistent", true);
        file = new File(plugin.getDataFolder(), "restore-history.yml");
        if (persistent) load();
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::save, 20L * 30L, 20L * 30L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        remember(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) remember(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) remember(block);
    }

    private void remember(Block block) {
        String key = key(block.getWorld(), block.getX(), block.getY(), block.getZ());
        if (history.containsKey(key)) return;
        BlockState state = block.getState();
        ItemStack[] contents = null;
        if (state instanceof Container container) contents = cloneContents(container.getInventory().getContents());
        history.put(key, new SavedBlock(block.getBlockData().getAsString(), contents));
    }

    public int restore(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return 0;
        int r = Math.min(Math.max(1, radius), maxRadius);
        long r2 = (long) r * r;
        List<String> remove = new ArrayList<>();
        int restored = 0;

        for (Map.Entry<String, SavedBlock> entry : history.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 4);
            if (parts.length != 4 || !parts[0].equals(world.getName())) continue;
            int x, y, z;
            try { x = Integer.parseInt(parts[1]); y = Integer.parseInt(parts[2]); z = Integer.parseInt(parts[3]); }
            catch (NumberFormatException ignored) { continue; }
            long dx = x - center.getBlockX();
            long dz = z - center.getBlockZ();
            if (dx * dx + dz * dz > r2) continue;
            Block block = world.getBlockAt(x, y, z);
            SavedBlock saved = entry.getValue();
            try {
                block.setBlockData(Bukkit.createBlockData(saved.blockData), false);
                if (saved.contents != null && block.getState() instanceof Container container) {
                    container.getInventory().setContents(cloneContents(saved.contents));
                    container.update(true, false);
                }
                remove.add(entry.getKey());
                restored++;
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Не удалось восстановить блок " + entry.getKey());
            }
        }
        for (String key : remove) history.remove(key);
        if (!remove.isEmpty() && persistent) save();
        return restored;
    }

    public int getTrackedCount() { return history.size(); }
    public int getDefaultRadius() { return defaultRadius; }
    public int getMaxRadius() { return maxRadius; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманду можно выполнить только игроком.");
            return true;
        }
        if (!player.hasPermission("waterworld.restore")) {
            player.sendMessage("§cНет права: waterworld.restore");
            return true;
        }
        int radius = defaultRadius;
        if (args.length > 0) {
            try { radius = Integer.parseInt(args[0]); }
            catch (NumberFormatException e) {
                player.sendMessage("§eИспользование: /wwrestore [радиус]");
                return true;
            }
        }
        radius = Math.min(Math.max(1, radius), maxRadius);
        int restored = restore(player.getLocation(), radius);
        player.sendMessage("§aWaterWorld: восстановлено блоков: §f" + restored + "§a в радиусе §f" + radius + "§a.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        return List.of("16", "32", "48", String.valueOf(maxRadius));
    }

    public void shutdown() {
        if (saveTask != null) saveTask.cancel();
        if (persistent) save();
    }

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            String data = yaml.getString(key + ".data");
            if (data == null) continue;
            List<?> list = yaml.getList(key + ".inventory");
            ItemStack[] contents = null;
            if (list != null) {
                contents = new ItemStack[list.size()];
                for (int i = 0; i < list.size(); i++) if (list.get(i) instanceof ItemStack item) contents[i] = item.clone();
            }
            history.put(key, new SavedBlock(data, contents));
        }
    }

    private void save() {
        if (!persistent) return;
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, SavedBlock> entry : history.entrySet()) {
            yaml.set(entry.getKey() + ".data", entry.getValue().blockData);
            if (entry.getValue().contents != null) yaml.set(entry.getKey() + ".inventory", List.of(entry.getValue().contents));
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить историю восстановления: " + e.getMessage());
        }
    }

    private static String key(World world, int x, int y, int z) { return world.getName() + "|" + x + "|" + y + "|" + z; }

    private static ItemStack[] cloneContents(ItemStack[] source) {
        if (source == null) return null;
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) result[i] = source[i] == null ? null : source[i].clone();
        return result;
    }

    private record SavedBlock(String blockData, ItemStack[] contents) {}
}
