package me.matoosh.blockmetadata;

public class BlockStringMetadataStorageTest extends BlockMetadataStorageTest<String> {
    @Override
    protected String createMetadata() {
        return "testMetadata";
    }
}
