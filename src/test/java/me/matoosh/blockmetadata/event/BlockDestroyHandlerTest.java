package me.matoosh.blockmetadata.event;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import me.matoosh.blockmetadata.exception.ChunkBusyException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BlockDestroyHandlerTest<T extends Serializable> {

    @Mock
    private BlockMetadataStorage<T> blockMetadataStorage;

    @InjectMocks
    private BlockDestroyHandler<T> blockDestroyHandler;

    private ServerMock server;
    private Block sampleBlock;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        WorldMock world = server.addSimpleWorld("test-world");
        sampleBlock = world.getBlockAt(0,5,0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onBlockDestroy() throws ChunkNotLoadedException, ChunkBusyException {
        PlayerMock player = server.addPlayer();
        blockDestroyHandler.onBlockDestroy(
                new BlockBreakEvent(sampleBlock, player));
        verify(blockMetadataStorage, times(1))
                .removeMetadata(sampleBlock);
    }

    @Test
    void onBlockBurn() throws ChunkNotLoadedException, ChunkBusyException {
        blockDestroyHandler.onBlockBurn(
                new BlockBurnEvent(sampleBlock, null));
        verify(blockMetadataStorage, times(1))
                .removeMetadata(sampleBlock);
    }

    @Test
    void onBlockFade() throws ChunkNotLoadedException, ChunkBusyException {
        blockDestroyHandler.onBlockFade(
                new BlockFadeEvent(sampleBlock, sampleBlock.getState()));
        verify(blockMetadataStorage, times(1))
                .removeMetadata(sampleBlock);
    }

    @Test
    void onEntityChangeBlock() throws ChunkNotLoadedException, ChunkBusyException {
        blockDestroyHandler.onEntityChangeBlock(
                new EntityChangeBlockEvent(null, sampleBlock, sampleBlock.getBlockData()));
        verify(blockMetadataStorage, times(1))
                .removeMetadata(sampleBlock);
    }
}