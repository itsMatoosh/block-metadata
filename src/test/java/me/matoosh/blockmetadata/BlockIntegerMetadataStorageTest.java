package me.matoosh.blockmetadata;

import org.junit.jupiter.api.BeforeEach;

import java.util.Random;

public class BlockIntegerMetadataStorageTest extends BlockMetadataStorageTest<Integer> {

    private Random random;

    @Override
    @BeforeEach
    void setUp() throws java.util.concurrent.ExecutionException, InterruptedException, java.io.IOException {
        random = new Random();
        super.setUp();
    }

    @Override
    protected Integer createMetadata() {
        return random.nextInt();
    }
}
