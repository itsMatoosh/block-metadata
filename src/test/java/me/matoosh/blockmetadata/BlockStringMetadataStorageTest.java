package me.matoosh.blockmetadata;

import java.util.ArrayList;
import java.util.Collection;

public class BlockStringMetadataStorageTest extends BlockMetadataStorageTest<String> {
    @Override
    protected Collection<String> createMetadataCollection() {
        return new ArrayList<>();
    }

    @Override
    protected String createMetadata() {
        return "123";
    }
}
