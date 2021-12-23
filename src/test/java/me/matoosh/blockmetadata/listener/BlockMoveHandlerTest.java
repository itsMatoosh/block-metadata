package me.matoosh.blockmetadata.listener;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import me.matoosh.blockmetadata.BlockMetadataStorage;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
abstract class BlockMoveHandlerTest<T extends Serializable> {

    @Mock
    private BlockMetadataStorage<T> blockMetadataStorage;

    @InjectMocks
    private BlockMoveHandler<T> blockMoveHandler;

    private WorldMock world;
    private Block pistonBlock;

    /**
     * Creates a sample metadata value for testing.
     * @return sample metadata.
     */
    protected abstract T createSampleMetadata();

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        world = server.addSimpleWorld("test-world");
        pistonBlock = world.getBlockAt(0,5,0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @ParameterizedTest
    @MethodSource("directionGenerator")
    void extendSingleBlock(BlockFace direction) throws ExecutionException, InterruptedException {
        // mock origin
        Block origin = pistonBlock.getRelative(direction);
        when(blockMetadataStorage.removeMetadata(origin))
                .thenReturn(CompletableFuture.completedFuture(createSampleMetadata()));

        // mock destination
        Block destination = origin.getRelative(direction);

        // fire the piston extend event
        List<Block> blocks = new ArrayList<>();
        blocks.add(origin);
        blockMoveHandler.onBlockPistonExtend(
                new BlockPistonExtendEvent(pistonBlock, blocks, direction));

        // ensure the metadata was transferred
        verify(blockMetadataStorage, times(1)).removeMetadata(origin);
        verify(blockMetadataStorage, times(1)).setMetadata(eq(destination), any());
    }

    @ParameterizedTest
    @MethodSource("directionGenerator")
    void extendSingleBlockNoMetadata(BlockFace direction) throws ExecutionException, InterruptedException {
        // mock origin
        Block origin = pistonBlock.getRelative(direction);
        when(blockMetadataStorage.removeMetadata(origin))
                .thenReturn(CompletableFuture.completedFuture(null));

        // fire the piston extend event
        List<Block> blocks = new ArrayList<>();
        blocks.add(origin);
        blockMoveHandler.onBlockPistonExtend(
                new BlockPistonExtendEvent(pistonBlock, blocks, direction));

        // ensure the metadata was transferred
        verify(blockMetadataStorage, times(1)).removeMetadata(origin);
    }

    @ParameterizedTest
    @MethodSource("directionGenerator")
    void retractSingleBlock(BlockFace direction) throws ExecutionException, InterruptedException {
        // mock origin
        Block origin = pistonBlock.getRelative(direction);
        when(blockMetadataStorage.removeMetadata(origin))
                .thenReturn(CompletableFuture.completedFuture(createSampleMetadata()));

        // mock destination
        Block destination = origin.getRelative(direction);

        // fire the piston extend event
        List<Block> blocks = new ArrayList<>();
        blocks.add(origin);
        blockMoveHandler.onBlockPistonRetract(
                new BlockPistonRetractEvent(pistonBlock, blocks, direction));

        // ensure the metadata was transferred
        verify(blockMetadataStorage, times(1)).removeMetadata(origin);
        verify(blockMetadataStorage, times(1)).setMetadata(eq(destination), any());
    }

    @ParameterizedTest
    @MethodSource("directionGenerator")
    void retractSingleBlockNoMetadata(BlockFace direction) throws ExecutionException, InterruptedException {
        // mock origin
        Block origin = pistonBlock.getRelative(direction);
        when(blockMetadataStorage.removeMetadata(origin))
                .thenReturn(CompletableFuture.completedFuture(null));

        // fire the piston extend event
        List<Block> blocks = new ArrayList<>();
        blocks.add(origin);
        blockMoveHandler.onBlockPistonRetract(
                new BlockPistonRetractEvent(pistonBlock, blocks, direction));

        // ensure the metadata was transferred
        verify(blockMetadataStorage, times(1)).removeMetadata(origin);
    }

    /**
     * Generates block directions.
     * @return stream of block directions.
     */
    private static Stream<BlockFace> directionGenerator() {
        return Stream.of(
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST,
            BlockFace.UP,
            BlockFace.DOWN
        );
    }
}