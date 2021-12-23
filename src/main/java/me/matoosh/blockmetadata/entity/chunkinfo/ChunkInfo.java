package me.matoosh.blockmetadata.entity.chunkinfo;

import lombok.Value;
import org.bukkit.Chunk;

@Value
public class ChunkInfo {
    String world;
    ChunkCoordinates coordinates;

    public static ChunkInfo fromChunk(Chunk chunk) {
        return new ChunkInfo(chunk.getWorld().getName(),
                new ChunkCoordinates(chunk.getX(), chunk.getZ()));
    }
}
