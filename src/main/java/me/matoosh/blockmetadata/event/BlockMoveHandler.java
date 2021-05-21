package me.matoosh.blockmetadata.event;

import lombok.RequiredArgsConstructor;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import me.matoosh.blockmetadata.exception.ChunkBusyException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles preserving block metadata when moving blocks with a piston.
 * @param <T> Type of the saved metadata.
 */
@RequiredArgsConstructor
public class BlockMoveHandler<T extends Serializable> implements Listener {

    private final BlockMetadataStorage<T> storage;

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPistonExtend(BlockPistonExtendEvent event)
            throws ChunkBusyException, ChunkNotLoadedException {
        if (event.isCancelled()) {
            return;
        }
        onBlocksMoveByPiston(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPistonRetract(BlockPistonRetractEvent event)
            throws ChunkBusyException, ChunkNotLoadedException {
        if (event.isCancelled()) {
            return;
        }
        onBlocksMoveByPiston(event.getBlocks(), event.getDirection());
    }

    /**
     * Handles moving a block with a piston.
     * Preserves blocks' metadata.
     * @param blocks The blocks that were moved.
     * @param direction The direction in which the blocks were moved.
     * @throws ChunkBusyException thrown if the chunk is busy.
     * @throws ChunkNotLoadedException thrown if the chunk metadata was not loaded.
     */
    private void onBlocksMoveByPiston(List<Block> blocks, BlockFace direction)
            throws ChunkBusyException, ChunkNotLoadedException {
        // cache origin block metadata
        Map<Block, T> cachedMetadata = new HashMap<>();
        for (Block origin : blocks) {
            // remove block metadata
            T metadata = storage.removeMetadata(origin);

            // skip if no metadata stored
            if (metadata == null) {
                continue;
            }

            // cache metadata for next step
            cachedMetadata.put(origin, metadata);
        }

        // move block metadata over
        for (Block origin : cachedMetadata.keySet()) {
            // move metadata in direction of the piston extend
            Block resulting = origin.getRelative(direction);
            storage.setMetadata(resulting, cachedMetadata.get(origin));
        }
    }
}
