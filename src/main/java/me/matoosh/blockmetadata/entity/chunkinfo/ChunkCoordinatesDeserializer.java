package me.matoosh.blockmetadata.entity.chunkinfo;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;

public class ChunkCoordinatesDeserializer extends KeyDeserializer {
    @Override
    public Object deserializeKey(String key, DeserializationContext ctxt) {
        String[] positionRaw = key.split(",");
        return new ChunkCoordinates(Integer.parseInt(positionRaw[0]),
                Integer.parseInt(positionRaw[1]));
    }
}
