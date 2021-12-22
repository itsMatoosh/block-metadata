package me.matoosh.blockmetadata.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import me.matoosh.blockmetadata.ChunkInfo;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;

import java.io.Serializable;
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
        // force unload each chunk
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                storage.unloadChunk(ChunkInfo.fromChunk(chunk)).get();
            }
        }

        log.info("Block metadata saved successfully!");
    }
}
