package me.matoosh.blockmetadata.listener;

import lombok.RequiredArgsConstructor;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import me.matoosh.blockmetadata.entity.chunkinfo.ChunkInfo;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldSaveEvent;

import java.io.Serializable;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

@RequiredArgsConstructor
public class WorldSaveHandler<T extends Serializable> implements Listener {
    private final BlockMetadataStorage<T> storage;

    @EventHandler
    public void onWorldSave(WorldSaveEvent event)
            throws ExecutionException, InterruptedException {
        // save each loaded chunk in the saved world
        storage.saveChunks(Arrays.stream(event.getWorld().getLoadedChunks())
                .map(ChunkInfo::fromChunk)
                .toArray(ChunkInfo[]::new)).get();
    }
}
