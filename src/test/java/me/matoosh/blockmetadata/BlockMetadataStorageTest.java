package me.matoosh.blockmetadata;

import be.seeseemelk.mockbukkit.*;
import me.matoosh.blockmetadata.entity.chunkinfo.BlockChunkCoordinates;
import me.matoosh.blockmetadata.entity.chunkinfo.ChunkInfo;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

abstract class BlockMetadataStorageTest<T extends Serializable> {

    private WorldMock world;
    private ChunkMock sampleChunk;
    private ChunkInfo sampleChunkInfo;
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
        sampleChunkInfo = ChunkInfo.fromChunk(sampleChunk);

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
        blockMetadataStorage.saveChunk(sampleChunkInfo, true).get();

        // ensure chunk is not loaded
        assertFalse(blockMetadataStorage.isChunkLoaded(sampleChunkInfo));

        // load the sample chunk
        blockMetadataStorage.loadChunk(sampleChunkInfo).get();

        // ensure chunk is loaded now
        assertTrue(blockMetadataStorage.isChunkLoaded(sampleChunkInfo));
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
        assertFalse(blockMetadataStorage.hasMetadataForChunk(sampleChunkInfo).get());

        // set some metadata
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata).get();

        // there should now be metadata in chunk
        assertTrue(blockMetadataStorage.hasMetadataForChunk(sampleChunkInfo).get());

        // remove metadata from the block
        blockMetadataStorage.removeMetadata(sampleBlock);
        assertFalse(blockMetadataStorage.hasMetadataForChunk(sampleChunkInfo).get());
    }

    @Test
    void removeMetadataForChunk() throws ExecutionException, InterruptedException {
        // initially there should be no metadata
        assertFalse(blockMetadataStorage.hasMetadataForChunk(sampleChunkInfo).get());

        // set some metadata
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata).get();

        // there should now be metadata in chunk
        assertTrue(blockMetadataStorage.hasMetadataForChunk(sampleChunkInfo).get());

        // remove metadata from the block
        blockMetadataStorage.removeMetadataForChunk(sampleChunkInfo).get();
        assertFalse(blockMetadataStorage.hasMetadataForChunk(sampleChunkInfo).get());
    }

    @Test
    void setMetadataSetsChunkDirty() throws ExecutionException, InterruptedException {
        // set metadata on block
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata).get();

        // ensure the chunk is dirty now
        assertTrue(blockMetadataStorage.isChunkDirty(sampleChunkInfo));

        // get metadata
        Map<BlockChunkCoordinates, T> retrievedMetadata = blockMetadataStorage.getMetadataInChunk(sampleChunkInfo).get();

        // make sure only 1 block is set with metadata
        assertEquals(1, retrievedMetadata.size());
        // ensure the block we set metadata for is in the map
        assertTrue(retrievedMetadata.containsKey(BlockChunkCoordinates.fromBlock(sampleBlock)));

        // remove metadata from block
        blockMetadataStorage.removeMetadata(sampleBlock).get();

        // ensure the chunk is dirty now
        assertTrue(blockMetadataStorage.isChunkDirty(sampleChunkInfo));
    }

    @Test
    void noMetadataRemovesChunkMap() throws ExecutionException, InterruptedException {
        // get metadata
        assertNull(blockMetadataStorage.getMetadataInChunk(sampleChunkInfo).get());

        // set metadata on block
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata).get();

        // get metadata
        Map<BlockChunkCoordinates, T> retrievedMetadata = blockMetadataStorage.getMetadataInChunk(sampleChunkInfo).get();
        // make sure only 1 block is set with metadata
        assertEquals(1, retrievedMetadata.size());
        // ensure the block we set metadata for is in the map
        assertTrue(retrievedMetadata.containsKey(BlockChunkCoordinates.fromBlock(sampleBlock)));

        // remove metadata from block
        blockMetadataStorage.removeMetadata(sampleBlock).get();

        // make sure there are no blocks with metadata
        assertNull(blockMetadataStorage.getMetadataInChunk(sampleChunkInfo).get());
    }

    @Test
    void saveChunk() throws ExecutionException, InterruptedException {
        // load sample chunk
        blockMetadataStorage.loadChunk(sampleChunkInfo).get();

        // ensure sample chunk is loaded
        assertTrue(blockMetadataStorage.isChunkLoaded(sampleChunkInfo));

        // set metadata on chunk
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(sampleBlock, metadata).get();

        // save chunk
        blockMetadataStorage.unloadChunks(sampleChunkInfo).get();

        // should be unloaded
        assertFalse(blockMetadataStorage.isChunkLoaded(sampleChunkInfo));

        // metadata should not be accessible now
        assertFalse(blockMetadataStorage.isChunkLoaded(ChunkInfo.fromChunk(sampleBlock.getChunk())));

        // load chunk
        blockMetadataStorage.loadChunk(sampleChunkInfo).get();

        // should be loaded
        assertTrue(blockMetadataStorage.isChunkLoaded(sampleChunkInfo));

        // check if metadata is available
        T metadataRetrieved = blockMetadataStorage.getMetadata(sampleBlock).get();
        assertEquals(metadata, metadataRetrieved);
    }

    @Test
    void loadChunk() throws ExecutionException, InterruptedException {
        // get chunk
        Chunk toLoad = world.getChunkAt(1, 1);
        ChunkInfo toLoadInfo = ChunkInfo.fromChunk(toLoad);

        // shouldn't be loaded yet
        assertFalse(blockMetadataStorage.isChunkLoaded(toLoadInfo));

        // load chunk
        blockMetadataStorage.loadChunk(toLoadInfo).get();

        // should be loaded
        assertTrue(blockMetadataStorage.isChunkLoaded(toLoadInfo));

        // unload chunk
        blockMetadataStorage.unloadChunks(toLoadInfo).get();

        // shouldn't be loaded
        assertFalse(blockMetadataStorage.isChunkLoaded(toLoadInfo));
    }

    @Test
    void isChunkBusy() throws ExecutionException, InterruptedException {
        // normally shouldn't be busy
        assertFalse(blockMetadataStorage.isChunkSaving(sampleChunkInfo));

        // start loading new chunk
        Chunk chunk = world.getChunkAt(1, 1);
        ChunkInfo chunkInfo = ChunkInfo.fromChunk(chunk);
        CompletableFuture<Void> loadFuture = blockMetadataStorage.loadChunk(chunkInfo);
        // chunk should not appear loaded while loading
        assertTrue(!blockMetadataStorage.isChunkLoaded(chunkInfo) || loadFuture.isDone());
        // finish loading
        loadFuture.get();
        // chunk should not be busy anymore
        assertFalse(blockMetadataStorage.isChunkSaving(chunkInfo));

        // start saving the chunk
        CompletableFuture<Void> saveFuture = blockMetadataStorage.saveChunk(chunkInfo, false);
        // chunk should be busy while saving
        assertTrue(blockMetadataStorage.isChunkSaving(chunkInfo) || saveFuture.isDone());
        // finish loading
        saveFuture.get();
        // chunk should not be busy anymore
        assertFalse(blockMetadataStorage.isChunkSaving(chunkInfo));
    }
}