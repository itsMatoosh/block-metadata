package me.matoosh.blockmetadata.event;

import be.seeseemelk.mockbukkit.ChunkMock;
import be.seeseemelk.mockbukkit.WorldMock;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import me.matoosh.blockmetadata.exception.ChunkAlreadyLoadedException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import org.bukkit.Material;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;

import static org.mockito.Mockito.*;

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
    void onChunkLoadUnload() throws ChunkAlreadyLoadedException, ChunkNotLoadedException {
        // ensure chunk load wasnt called yet
        verify(blockMetadataStorage, never()).loadChunk(sampleChunk);

        // trigger chunk load event
        chunkLoadHandler.onChunkLoad(new ChunkLoadEvent(sampleChunk, false));

        // assert that the chunk load caused metadata load
        verify(blockMetadataStorage, times(1))
                .loadChunk(sampleChunk);

        // trigger chunk unload event
        chunkLoadHandler.onChunkUnload(new ChunkUnloadEvent(sampleChunk, true));

        // assert that the chunk unload caused metadata unload
        verify(blockMetadataStorage, times(1)).persistChunk(sampleChunk);
    }
}