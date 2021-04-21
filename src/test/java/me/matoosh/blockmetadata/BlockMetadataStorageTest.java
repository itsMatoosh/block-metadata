package me.matoosh.blockmetadata;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import me.matoosh.blockmetadata.exception.ChunkBusyException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

abstract class BlockMetadataStorageTest<T extends Serializable> {

    private ServerMock server;
    private WorldMock world;
    private BlockMetadataStorage<T> blockMetadataStorage;

    protected abstract Collection<T> createMetadataCollection();
    protected abstract T createMetadata();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("test-world");

        Path dataDir = Paths.get("/");
        blockMetadataStorage = new BlockMetadataStorage<>(dataDir);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void getMetadataNull() throws ChunkBusyException, ChunkNotLoadedException {
        T metadata = blockMetadataStorage.getMetadata(
                world.getBlockAt(0, 0, 0));
        assertNull(metadata);
    }

    @Test
    void getMetadata() {

    }

    @Test
    void setMetadata() throws ChunkBusyException, ChunkNotLoadedException {
        Block blockToSet = world.getBlockAt(0, 0, 0);
        T metadata = createMetadata();
        blockMetadataStorage.setMetadata(blockToSet, metadata);
    }

    @Test
    void removeMetadata() {
    }

    @Test
    void hasMetadataForChunk() {
    }

    @Test
    void removeMetadataForChunk() {
    }

    @Test
    void modifyMetadataInChunk() {
    }

    @Test
    void getMetadataInChunk() {
    }

    @Test
    void persistChunk() {
    }

    @Test
    void loadChunk() {
    }

    @Test
    void getRegionKey() {
    }

    @Test
    void getChunkKey() {
    }

    @Test
    void isChunkBusy() {
    }

    @Test
    void getRegionFile() {
    }

    @Test
    void getBlockKeyInChunk() {
    }

    @Test
    void testGetBlockKeyInChunk() {
    }
}