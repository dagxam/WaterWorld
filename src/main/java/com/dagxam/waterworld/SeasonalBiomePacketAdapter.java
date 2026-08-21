package com.dagxam.waterworld;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedLevelChunkData;
import org.bukkit.plugin.Plugin;

/**
 * Визуальная подмена биомов для клиента без изменения реального мира.
 *
 * ВАЖНО:
 * Minecraft 1.21.11+ отправляет биомы отдельным пакетом CHUNKS_BIOMES.
 * MAP_CHUNK остается пакетом данных чанка, но не является надежным местом
 * для чтения отдельного массива биомов.
 *
 * Поэтому этот адаптер подписывается на оба типа пакетов:
 * - MAP_CHUNK — оставлен для совместимости со старыми версиями ProtocolLib;
 * - CHUNKS_BIOMES — основной путь для актуального протокола.
 *
 * Сам алгоритм сезонной замены должен работать только с сетевым представлением
 * данных и никогда не изменять Bukkit Chunk/Block/World.
 */
public final class SeasonalBiomePacketAdapter extends PacketAdapter {

    public enum Season {
        SPRING,
        SUMMER,
        AUTUMN,
        WINTER
    }

    private final SeasonProvider seasonProvider;

    public SeasonalBiomePacketAdapter(Plugin plugin, SeasonProvider seasonProvider) {
        super(plugin,
                ListenerPriority.NORMAL,
                PacketType.Play.Server.MAP_CHUNK,
                PacketType.Play.Server.CHUNKS_BIOMES);
        this.seasonProvider = seasonProvider;
    }

    /**
     * Регистрирует адаптер в ProtocolLib.
     */
    public static SeasonalBiomePacketAdapter register(
            Plugin plugin,
            SeasonProvider seasonProvider
    ) {
        SeasonalBiomePacketAdapter adapter =
                new SeasonalBiomePacketAdapter(plugin, seasonProvider);
        ProtocolLibrary.getProtocolManager().addPacketListener(adapter);
        plugin.getLogger().info("Сезонная подмена биомов включена.");
        return adapter;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        // Никаких Bukkit Chunk/Block операций в packet-thread.
        // Получаем сезон из заранее подготовленного, потокобезопасного провайдера.
        Season season = seasonProvider.getSeason(event);
        if (season == null) {
            return;
        }

        PacketType type = event.getPacketType();

        try {
            if (type == PacketType.Play.Server.CHUNKS_BIOMES) {
                handleChunksBiomes(event, season);
            } else if (type == PacketType.Play.Server.MAP_CHUNK) {
                handleLegacyMapChunk(event, season);
            }
        } catch (Throwable throwable) {
            // Сетевой обработчик не должен ломать отправку чанка игроку.
            event.getPlayer().sendMessage("§c[Сезоны] Ошибка обработки данных биома: "
                    + throwable.getClass().getSimpleName());
            event.getPlayer().getServer().getLogger().warning(
                    "[Сезоны] Не удалось изменить биом в пакете " + type + ": "
                            + throwable.getMessage()
            );
        }
    }

    /**
     * Основной путь для современных версий.
     *
     * В актуальном протоколе пакет содержит сериализованный список
     * ChunkBiomeData, поэтому здесь нельзя обращаться к нему как к простому
     * массиву Bukkit Biome. Сначала извлекается его реальное сетевое
     * представление, после чего модифицируется только буфер биомов.
     */
    private void handleChunksBiomes(PacketEvent event, Season season) {
        // TODO: Вынести точный codec-декодер в отдельный SeasonBiomeCodec.
        // Он должен:
        // 1. прочитать список ChunkBiomeData;
        // 2. для каждой секции сохранить палитру и размер BitStorage;
        // 3. заменить записи палитры на один сезонный biome holder;
        // 4. сериализовать обратно без создания Bukkit-объектов.
        //
        // Этот метод намеренно не пытается использовать getStructures()
        // вслепую: на 1.21.11 структура пакета менялась, а неверный индекс
        // приводит к повреждению VarInt/длиной буфера.
    }

    /**
     * Совместимость со старыми ProtocolLib/Minecraft, где MAP_CHUNK мог
     * содержать chunk payload вместе с биомами.
     */
    private void handleLegacyMapChunk(PacketEvent event, Season season) {
        if (event.getPacket().getStructures().size() == 0) {
            return;
        }

        // В старых сборках ProtocolLib MAP_CHUNK оборачивался через
        // WrappedLevelChunkData.ChunkData. Его буфер — уже сериализованные
        // данные chunk-секций. Простая запись byte[] здесь НЕПРАВИЛЬНА:
        // внутри находятся palette/BitStorage/heightmap и NBT, а не массив
        // biome-id фиксированной длины.
        //
        // Не изменяем пакет, пока не определен конкретный codec версии.
        // Это предотвращает повреждение сетевого потока.
        Object structure = event.getPacket().getStructures().readSafely(0);
        if (structure instanceof WrappedLevelChunkData.ChunkData) {
            // Точка расширения для legacy codec.
        }
    }

    @FunctionalInterface
    public interface SeasonProvider {
        Season getSeason(PacketEvent event);
    }
}
