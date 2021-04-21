package me.matoosh.blockmetadata;

import be.seeseemelk.mockbukkit.*;
import me.matoosh.blockmetadata.exception.ChunkAlreadyLoadedException;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

abstract class ChunkLoadHandlerTest<T extends Serializable> {

    private ServerMock server;
    private WorldMock world;
    private MockPlugin plugin;
    private ChunkMock sampleChunk;
    private BlockMetadataStorage<T> blockMetadataStorage;

    @BeforeEach
    void setUp() throws IOException {
        // mock server
        server = MockBukkit.mock();
        world = server.addSimpleWorld("test-world");
        plugin = MockBukkit.createMockPlugin();

        // create metadata storage
        Path dataDir = Files.createTempDirectory("block-metadata-temp");
        blockMetadataStorage = new BlockMetadataStorage<>(plugin, dataDir);

        // load a chunk into memory
        sampleChunk = world.getChunkAt(0, 0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onChunkLoadUnload() {
        // ensure chunk not loaded
        assertFalse(blockMetadataStorage.isChunkLoaded(sampleChunk));

        // trigger chunk load
        world.loadChunk(sampleChunk);

        // ensure chunk loaded
        assertTrue(blockMetadataStorage.isChunkLoaded(sampleChunk));

        // trigger chunk unload
        world.unloadChunk(sampleChunk);

        assertFalse(blockMetadataStorage.isChunkLoaded(sampleChunk));
    }
}