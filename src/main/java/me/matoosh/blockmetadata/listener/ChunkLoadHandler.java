package me.matoosh.blockmetadata.listener;

import lombok.RequiredArgsConstructor;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import me.matoosh.blockmetadata.entity.chunkinfo.ChunkInfo;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.io.Serializable;

/**
 * Handles loading/saving block metadata automatically on chunk loading/unloading.
 * @param <T> Type of the saved metadata.
 */
@RequiredArgsConstructor
public class ChunkLoadHandler<T extends Serializable> implements Listener {

    private final BlockMetadataStorage<T> storage;

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        storage.saveChunk(ChunkInfo.fromChunk(event.getChunk()), true);
    }
}
