package me.matoosh.blockmetadata;

import be.seeseemelk.mockbukkit.ChunkMock;
import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import me.matoosh.blockmetadata.exception.ChunkAlreadyLoadedException;
import me.matoosh.blockmetadata.exception.ChunkBusyException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

abstract class BlockMetadataStorageTest<T extends Serializable> {

    private WorldMock world;
    private ChunkMock sampleChunk;
    private Block sampleBlock;
    private BlockMetadataStorage<T> blockMetadataStorage;

    protected abstract T createMetadata();

    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException,
            ChunkAlreadyLoadedException, IOException {
        ServerMock server = MockBukkit.mock();
        world = server.addSimpleWorld("test-world");

        Path dataDir = Files.createTempDirectory("block-metadata-temp");
        blockMetadataStorage = new BlockMetadataStorage<>(dataDir);

        // load a chunk into memory
        sampleChunk = world.getChunkAt(0, 0);
        blockMetadataStorage.loadChunk(sampleChunk).get();

        // get sample block
        sampleBlock = world.getBlockAt(0, 0, 0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void getMetadataNull() throws ChunkBusyException, ChunkNotLoadedException {
        T metadata = blockMetadataStorage.getMetadata(
                sampleChunk.getBlock(0, 0, 0));
        assertNull(metadata);
    }

    @Test
    void setGetMetadata() throws ChunkBusyException, ChunkNotLoadedException {
        // set metadata on a block
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata);

        // retrieve the metadata
        T metadataRetrieved = blockMetadataStorage.getMetadata(sampleBlock);

        // ensure that the metadata was retrieved correctly
        assertEquals(metadata, metadataRetrieved);
    }

    @Test
    void removeMetadata() throws ChunkNotLoadedException, ChunkBusyException {
        // set metadata on a block
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata);

        // remove metadata from block
        blockMetadataStorage.removeMetadata(sampleBlock);

        // retrieve the metadata
        T metadataRetrieved = blockMetadataStorage.getMetadata(sampleBlock);

        // ensure the retrieved metadata is null
        assertNull(metadataRetrieved);
    }

    @Test
    void hasMetadataForChunk() throws ChunkNotLoadedException, ChunkBusyException {
        // initially there should be no metadata
        assertFalse(blockMetadataStorage.hasMetadataForChunk(sampleChunk));

        // set some metadata
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata);

        // there should now be metadata in chunk
        assertTrue(blockMetadataStorage.hasMetadataForChunk(sampleChunk));

        // remove metadata from the block
        blockMetadataStorage.removeMetadata(sampleBlock);
        assertFalse(blockMetadataStorage.hasMetadataForChunk(sampleChunk));
    }

    @Test
    void removeMetadataForChunk() throws ChunkNotLoadedException, ChunkBusyException {
        // initially there should be no metadata
        assertFalse(blockMetadataStorage.hasMetadataForChunk(sampleChunk));

        // set some metadata
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata);

        // there should now be metadata in chunk
        assertTrue(blockMetadataStorage.hasMetadataForChunk(sampleChunk));

        // remove metadata from the block
        blockMetadataStorage.removeMetadataForChunk(sampleChunk);
        assertFalse(blockMetadataStorage.hasMetadataForChunk(sampleChunk));
    }

    @Test
    void modifyMetadataInChunk() throws ChunkNotLoadedException, ChunkBusyException {
        // get metadata
        assertEquals(0, blockMetadataStorage.modifyMetadataInChunk(sampleChunk).size());

        // ensure the chunk is dirty now
        assertTrue(blockMetadataStorage.isChunkDirty(sampleChunk));

        // set metadata on block
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata);

        // get metadata
        Map<String, T> retrievedMetadata = blockMetadataStorage.modifyMetadataInChunk(sampleChunk);
        // make sure only 1 block is set with metadata
        assertEquals(1, retrievedMetadata.size());
        // ensure the block we set metadata for is in the map
        assertTrue(retrievedMetadata.containsKey(BlockMetadataStorage
                .getBlockKeyInChunk(sampleBlock)));
        // ensure the chunk is dirty now
        assertTrue(blockMetadataStorage.isChunkDirty(sampleChunk));

        // remove metadata from block
        blockMetadataStorage.removeMetadata(sampleBlock);
        // make sure there are no blocks with metadata
        assertEquals(0, blockMetadataStorage.modifyMetadataInChunk(sampleChunk).size());
        // ensure the chunk is dirty now
        assertTrue(blockMetadataStorage.isChunkDirty(sampleChunk));
    }

    @Test
    void getMetadataInChunk() throws ChunkNotLoadedException, ChunkBusyException {
        // get metadata
        assertNull(blockMetadataStorage.getMetadataInChunk(sampleChunk));

        // set metadata on block
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata);

        // get metadata
        Map<String, T> retrievedMetadata = blockMetadataStorage.getMetadataInChunk(sampleChunk);
        // make sure only 1 block is set with metadata
        assertEquals(1, retrievedMetadata.size());
        // ensure the block we set metadata for is in the map
        assertTrue(retrievedMetadata.containsKey(BlockMetadataStorage
                .getBlockKeyInChunk(sampleBlock)));

        // remove metadata from block
        blockMetadataStorage.removeMetadata(sampleBlock);
        // get metadata
        retrievedMetadata = blockMetadataStorage.getMetadataInChunk(sampleChunk);
        // make sure there are no blocks with metadata
        assertNull(retrievedMetadata);
    }

    @Test
    void persistChunk() throws ChunkNotLoadedException, ChunkBusyException,
            ExecutionException, InterruptedException, ChunkAlreadyLoadedException {
        // ensure sample chunk is loaded
        assertTrue(blockMetadataStorage.isChunkLoaded(sampleChunk));

        // set metadata on chunk
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata);

        // save chunk
        blockMetadataStorage.persistChunk(sampleChunk, true).get();

        // should be unloaded
        assertFalse(blockMetadataStorage.isChunkLoaded(sampleChunk));

        // metadata should not be accessible now
        assertThrows(ChunkNotLoadedException.class,
                () -> blockMetadataStorage.getMetadata(sampleBlock));

        // load chunk
        blockMetadataStorage.loadChunk(sampleChunk).get();

        // should be loaded
        assertTrue(blockMetadataStorage.isChunkLoaded(sampleChunk));

        // check if metadata is available
        T metadataRetrieved = blockMetadataStorage.getMetadata(sampleBlock);
        assertEquals(metadata, metadataRetrieved);
    }

    @Test
    void loadChunk() throws ChunkAlreadyLoadedException, ExecutionException,
            InterruptedException, ChunkNotLoadedException {
        // get chunk
        Chunk toLoad = world.getChunkAt(1, 1);

        // shouldn't be loaded yet
        assertFalse(blockMetadataStorage.isChunkLoaded(toLoad));

        // load chunk
        blockMetadataStorage.loadChunk(toLoad).get();

        // should be loaded
        assertTrue(blockMetadataStorage.isChunkLoaded(toLoad));

        // unload chunk
        blockMetadataStorage.persistChunk(toLoad, true).get();

        // shouldn't be loaded
        assertFalse(blockMetadataStorage.isChunkLoaded(toLoad));
    }

    @Test
    void isChunkBusy() throws ChunkNotLoadedException, ExecutionException,
            InterruptedException, ChunkAlreadyLoadedException {
        // normally shouldn't be busy
        assertFalse(blockMetadataStorage.isChunkBusy(sampleChunk));

        // start loading new chunk
        Chunk chunk = world.getChunkAt(1, 1);
        CompletableFuture<Void> loadFuture = blockMetadataStorage.loadChunk(chunk);
        // chunk should be busy while loading
        assertTrue(blockMetadataStorage.isChunkBusy(chunk) || loadFuture.isDone());
        // finish loading
        loadFuture.get();
        // chunk should not be busy anymore
        assertFalse(blockMetadataStorage.isChunkBusy(chunk));

        // start saving the chunk
        CompletableFuture<Void> saveFuture = blockMetadataStorage.persistChunk(chunk, false);
        // chunk should be busy while loading
        assertTrue(blockMetadataStorage.isChunkBusy(chunk) || saveFuture.isDone());
        // finish loading
        saveFuture.get();
        // chunk should not be busy anymore
        assertFalse(blockMetadataStorage.isChunkBusy(chunk));
    }

    @Test
    void getBlockKeyInChunkUnique() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                for (int k = 0; k < 256; k++) {
                    keys.add(BlockMetadataStorage.getBlockKeyInChunk(i, j, k));
                }
            }
        }

        // ensure keys are unique
        assertEquals(16 * 16 * 256, keys.size());
    }
}