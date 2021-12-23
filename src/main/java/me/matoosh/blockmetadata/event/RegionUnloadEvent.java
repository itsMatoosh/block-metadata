package me.matoosh.blockmetadata.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.matoosh.blockmetadata.entity.chunkinfo.ChunkCoordinates;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Set;

@RequiredArgsConstructor
@Getter
public class RegionUnloadEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    /**
     * The world in which the region was unloaded.
     */
    private final String world;
    /**
     * Coordinates of the unloaded chunks.
     */
    private final Set<ChunkCoordinates> unloadedChunks;

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
}
