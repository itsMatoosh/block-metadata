package me.matoosh.blockmetadata;

import lombok.Data;
import org.bukkit.Chunk;

@Data
public class ChunkInfo {
    private final String world;
    private final ChunkCoordinates coordinates;

    public static ChunkInfo fromChunk(Chunk chunk) {
        return new ChunkInfo(chunk.getWorld().getName(),
                new ChunkCoordinates(chunk.getX(), chunk.getZ()));
    }
}
