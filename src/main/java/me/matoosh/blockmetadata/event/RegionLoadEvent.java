package me.matoosh.blockmetadata.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.matoosh.blockmetadata.entity.chunkinfo.ChunkCoordinates;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Set;

/**
 * Called when metadata for a region is loaded.
 */
@RequiredArgsConstructor
@Getter
public class RegionLoadEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    /**
     * The world in which the region was loaded.
     */
    private final String world;
    /**
     * Coordinates of the loaded chunks.
     */
    private final Set<ChunkCoordinates> loadedChunks;

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
