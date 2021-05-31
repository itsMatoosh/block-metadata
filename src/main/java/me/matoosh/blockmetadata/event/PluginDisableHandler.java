package me.matoosh.blockmetadata.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
    public void onWorldUnload(WorldUnloadEvent event) {
        log.info("Saving metadata in world " + event.getWorld().getName());
        for (Chunk c : event.getWorld().getLoadedChunks()) {
            try {
                storage.persistChunk(c);
            } catch (ChunkNotLoadedException ignored) {
            }
        }
    }

    @EventHandler
    public void onServerStop(PluginDisableEvent event)
            throws ExecutionException, InterruptedException {
        log.info("Saving block metadata...");

        // wait until all the data is saved
        Map<Chunk, CompletableFuture<Void>> savingChunks = storage.getSavingChunks();
        for (CompletableFuture<Void> future :
                savingChunks.values()) {
            future.get();
        }
        
        log.info("Block metadata saved successfully!");
    }
}