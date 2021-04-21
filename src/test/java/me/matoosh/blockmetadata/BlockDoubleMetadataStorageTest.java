package me.matoosh.blockmetadata;

public class BlockDoubleMetadataStorageTest extends BlockMetadataStorageTest<Double> {
    @Override
    protected Double createMetadata() {
        return Math.random() * 100;
    }
}
