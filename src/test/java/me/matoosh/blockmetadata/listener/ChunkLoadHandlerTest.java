package me.matoosh.blockmetadata.listener;

import be.seeseemelk.mockbukkit.ChunkMock;
import be.seeseemelk.mockbukkit.WorldMock;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import me.matoosh.blockmetadata.entity.chunkinfo.ChunkInfo;
import org.bukkit.Material;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
abstract class ChunkLoadHandlerTest<T extends Serializable> {

    @Mock
    private BlockMetadataStorage<T> blockMetadataStorage;

    @InjectMocks
    private ChunkLoadHandler<T> chunkLoadHandler;

    private final WorldMock world = new WorldMock(Material.GRASS_BLOCK, 10);
    private final ChunkMock sampleChunk = world.getChunkAt(0, 0);

    /**
     * Verifies that a chunk load/unload causes metadata load/unload.
     */
    @Test
    void onChunkLoadUnload() {
        // trigger chunk unload event
        chunkLoadHandler.onChunkUnload(new ChunkUnloadEvent(sampleChunk, true));

        // assert that the chunk unload caused metadata unload
        verify(blockMetadataStorage, times(1))
                .saveChunk(ChunkInfo.fromChunk(sampleChunk), true);
    }
}