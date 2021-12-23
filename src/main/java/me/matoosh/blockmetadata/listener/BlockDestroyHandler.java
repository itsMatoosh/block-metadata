package me.matoosh.blockmetadata.listener;

import lombok.RequiredArgsConstructor;
import me.matoosh.blockmetadata.BlockMetadataStorage;
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
    public void onBlockDestroy(BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }
        storage.removeMetadata(event.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.isCancelled()) {
            return;
        }
        storage.removeMetadata(event.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBurn(BlockBurnEvent event) {
        if (event.isCancelled()) {
            return;
        }
        storage.removeMetadata(event.getBlock());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockFade(BlockFadeEvent event) {
        if (event.isCancelled()) {
            return;
        }
        storage.removeMetadata(event.getBlock());
    }
}
