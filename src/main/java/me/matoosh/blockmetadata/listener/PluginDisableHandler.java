package me.matoosh.blockmetadata.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import me.matoosh.blockmetadata.entity.chunkinfo.ChunkInfo;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;

import java.io.Serializable;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/**
 * Ensures that all metadata is saved correctly when the plugin is unloaded.
 * @param <T> Type of the saved metadata.
 */
@RequiredArgsConstructor
@Log
public class PluginDisableHandler<T extends Serializable> implements Listener {

    private final BlockMetadataStorage<T> storage;

    @EventHandler
    public void onServerStop(PluginDisableEvent event)
            throws ExecutionException, InterruptedException {
        // unload each loaded chunk in the saved world
        storage.unloadChunks(Bukkit.getWorlds().stream()
                .flatMap(w -> Arrays.stream(w.getLoadedChunks()))
                .map(ChunkInfo::fromChunk)
                .toArray(ChunkInfo[]::new)).get();
    }
}
