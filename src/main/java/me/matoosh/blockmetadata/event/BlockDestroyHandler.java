package me.matoosh.blockmetadata.event;

import lombok.RequiredArgsConstructor;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import me.matoosh.blockmetadata.exception.ChunkBusyException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import java.io.Serializable;

/**
 * Handles clearing block metadata when a block is destroyed.
 * @param <T> Type of the saved metadata.
 */
@RequiredArgsConstructor
public class BlockDestroyHandler<T extends Serializable> implements Listener {
    private final BlockMetadataStorage<T> storage;

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockDestroy(BlockBreakEvent event)
            throws ChunkBusyException, ChunkNotLoadedException {
        if (event.isCancelled()) {
            return;
        }
        storage.removeMetadata(event.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityChangeBlock(EntityChangeBlockEvent event)
            throws ChunkNotLoadedException, ChunkBusyException {
        if (event.isCancelled()) {
            return;
        }
        storage.removeMetadata(event.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBurn(BlockBurnEvent event)
            throws ChunkBusyException, ChunkNotLoadedException {
        if (event.isCancelled()) {
            return;
        }
        storage.removeMetadata(event.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockFade(BlockFadeEvent event)
            throws ChunkBusyException, ChunkNotLoadedException {
        if (event.isCancelled()) {
            return;
        }
        storage.removeMetadata(event.getBlock());
    }
}
