package me.matoosh.blockmetadata.entity.chunkinfo;

import lombok.Value;
import org.bukkit.Chunk;
import org.bukkit.block.Block;

/**
 * Coordinates of a block within a chunk.
 */
@Value
public class BlockChunkCoordinates {
    int x, y, z;
    /**
     * Construct a block chunk coordinates object from a block.
     * @param block The block.
     * @return The constructed block chunk coordinates.
     */
    public static BlockChunkCoordinates fromBlock(Block block) {
        Chunk chunk = block.getChunk();
        return new BlockChunkCoordinates(
                block.getX() - chunk.getX() * 16,
                block.getY(),
                block.getZ() - chunk.getZ() * 16
        );
    }
}
