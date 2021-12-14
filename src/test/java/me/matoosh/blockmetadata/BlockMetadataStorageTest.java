package me.matoosh.blockmetadata;

import be.seeseemelk.mockbukkit.*;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

abstract class BlockMetadataStorageTest<T extends Serializable> {

    private WorldMock world;
    private ChunkMock sampleChunk;
    private Block sampleBlock;
    private BlockMetadataStorage<T> blockMetadataStorage;

    protected abstract T createMetadata();

    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException, IOException {
        ServerMock server = MockBukkit.mock();
        MockPlugin mockPlugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("test-world");

        Path dataDir = Files.createTempDirectory("block-metadata-temp");
        blockMetadataStorage = new BlockMetadataStorage<>(mockPlugin, dataDir);

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
    void getMetadataNull() throws ExecutionException, InterruptedException {
        T metadata = blockMetadataStorage.getMetadata(
                sampleChunk.getBlock(0, 0, 0)).get();
        assertNull(metadata);
    }

    @Test
    void setGetMetadata() throws ExecutionException, InterruptedException {
        // set metadata on a block
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata).get();

        // retrieve the metadata
        T metadataRetrieved = blockMetadataStorage.getMetadata(sampleBlock).get();

        // ensure that the metadata was retrieved correctly
        assertEquals(metadata, metadataRetrieved);
    }

    @Test
    void ensureChunkReadyAutoloadChunk() throws ExecutionException, InterruptedException {
        // unload the sample chunk
        blockMetadataStorage.persistChunk(sampleChunk).get();

        // ensure chunk is not loaded
        assertFalse(blockMetadataStorage.isChunkLoaded(sampleChunk));

        // load the sample chunk
        blockMetadataStorage.loadChunk(sampleChunk).get();

        // ensure chunk is loaded now
        assertTrue(blockMetadataStorage.isChunkLoaded(sampleChunk));
    }

    @Test
    void removeMetadata() throws ExecutionException, InterruptedException {
        // set metadata on a block
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata).get();

        // remove metadata from block
        blockMetadataStorage.removeMetadata(sampleBlock).get();

        // retrieve the metadata
        T metadataRetrieved = blockMetadataStorage.getMetadata(sampleBlock).get();

        // ensure the retrieved metadata is null
        assertNull(metadataRetrieved);
    }

    @Test
    void hasMetadataForChunk() throws ExecutionException, InterruptedException {
        // initially there should be no metadata
        assertFalse(blockMetadataStorage.hasMetadataForChunk(sampleChunk).get());

        // set some metadata
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata).get();

        // there should now be metadata in chunk
        assertTrue(blockMetadataStorage.hasMetadataForChunk(sampleChunk).get());

        // remove metadata from the block
        blockMetadataStorage.removeMetadata(sampleBlock);
        assertFalse(blockMetadataStorage.hasMetadataForChunk(sampleChunk).get());
    }

    @Test
    void removeMetadataForChunk() throws ExecutionException, InterruptedException {
        // initially there should be no metadata
        assertFalse(blockMetadataStorage.hasMetadataForChunk(sampleChunk).get());

        // set some metadata
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata).get();

        // there should now be metadata in chunk
        assertTrue(blockMetadataStorage.hasMetadataForChunk(sampleChunk).get());

        // remove metadata from the block
        blockMetadataStorage.removeMetadataForChunk(sampleChunk).get();
        assertFalse(blockMetadataStorage.hasMetadataForChunk(sampleChunk).get());
    }

    @Test
    void setMetadataSetsChunkDirty() throws ExecutionException, InterruptedException {
        // set metadata on block
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata).get();

        // ensure the chunk is dirty now
        assertTrue(blockMetadataStorage.isChunkDirty(sampleChunk));

        // get metadata
        Map<String, T> retrievedMetadata = blockMetadataStorage.getMetadataInChunk(sampleChunk).get();

        // make sure only 1 block is set with metadata
        assertEquals(1, retrievedMetadata.size());
        // ensure the block we set metadata for is in the map
        assertTrue(retrievedMetadata.containsKey(BlockMetadataStorage
                .getBlockKeyInChunk(sampleBlock)));

        // remove metadata from block
        blockMetadataStorage.removeMetadata(sampleBlock).get();

        // ensure the chunk is dirty now
        assertTrue(blockMetadataStorage.isChunkDirty(sampleChunk));
    }

    @Test
    void noMetadataRemovesChunkMap() throws ExecutionException, InterruptedException {
        // get metadata
        assertNull(blockMetadataStorage.getMetadataInChunk(sampleChunk).get());

        // set metadata on block
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata).get();

        // get metadata
        Map<String, T> retrievedMetadata = blockMetadataStorage.getMetadataInChunk(sampleChunk).get();
        // make sure only 1 block is set with metadata
        assertEquals(1, retrievedMetadata.size());
        // ensure the block we set metadata for is in the map
        assertTrue(retrievedMetadata.containsKey(BlockMetadataStorage
                .getBlockKeyInChunk(sampleBlock)));

        // remove metadata from block
        blockMetadataStorage.removeMetadata(sampleBlock).get();

        // make sure there are no blocks with metadata
        assertNull(blockMetadataStorage.getMetadataInChunk(sampleChunk).get());
    }

    @Test
    void persistChunk() throws ExecutionException, InterruptedException {
        // ensure sample chunk is loaded
        assertTrue(blockMetadataStorage.isChunkLoaded(sampleChunk));

        // set metadata on chunk
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata).get();

        // save chunk
        blockMetadataStorage.persistChunk(sampleChunk).get();

        // should be unloaded
        assertFalse(blockMetadataStorage.isChunkLoaded(sampleChunk));

        // metadata should not be accessible now
        assertFalse(blockMetadataStorage.isChunkLoaded(sampleBlock.getChunk()));

        // load chunk
        blockMetadataStorage.loadChunk(sampleChunk).get();

        // should be loaded
        assertTrue(blockMetadataStorage.isChunkLoaded(sampleChunk));

        // check if metadata is available
        T metadataRetrieved = blockMetadataStorage.getMetadata(sampleBlock).get();
        assertEquals(metadata, metadataRetrieved);
    }

    @Test
    void loadChunk() throws ExecutionException, InterruptedException {
        // get chunk
        Chunk toLoad = world.getChunkAt(1, 1);

        // shouldn't be loaded yet
        assertFalse(blockMetadataStorage.isChunkLoaded(toLoad));

        // load chunk
        blockMetadataStorage.loadChunk(toLoad).get();

        // should be loaded
        assertTrue(blockMetadataStorage.isChunkLoaded(toLoad));

        // unload chunk
        blockMetadataStorage.persistChunk(toLoad).get();

        // shouldn't be loaded
        assertFalse(blockMetadataStorage.isChunkLoaded(toLoad));
    }

    @Test
    void isChunkBusy() throws ExecutionException, InterruptedException {
        // normally shouldn't be busy
        assertFalse(blockMetadataStorage.isChunkBusy(sampleChunk));

        // start loading new chunk
        Chunk chunk = world.getChunkAt(1, 1);
        CompletableFuture<Void> loadFuture = blockMetadataStorage.loadChunk(chunk);
        // chunk should not appear loaded while loading
        assertTrue(!blockMetadataStorage.isChunkLoaded(chunk) || loadFuture.isDone());
        // finish loading
        loadFuture.get();
        // chunk should not be busy anymore
        assertFalse(blockMetadataStorage.isChunkBusy(chunk));

        // start saving the chunk
        CompletableFuture<Void> saveFuture = blockMetadataStorage.persistChunk(chunk);
        // chunk should be busy while saving
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
        assertThat(keys).hasSize(16 * 16 * 256);
    }
}