package me.matoosh.blockmetadata;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * Handles loading/saving block metadata automatically on chunk loading/unloading.
 * @param <T> Type of the saved metadata.
 */
public class ChunkLoadHandler<T> implements Listener {

    private final BlockMetadataStorage<T> storage;

    public ChunkLoadHandler(BlockMetadataStorage<T> metadataStorage) {
        this.storage = metadataStorage;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        storage.loadChunk(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        storage.persistChunk(event.getChunk(), true);
    }
}
