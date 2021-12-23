package me.matoosh.blockmetadata.entity.chunkinfo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

public class ChunkCoordinatesSerializer extends StdSerializer<ChunkCoordinates> {

    public ChunkCoordinatesSerializer() {
        this(null);
    }

    public ChunkCoordinatesSerializer(Class<ChunkCoordinates> t) {
        super(t);
    }

    @Override
    public void serialize(ChunkCoordinates value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        gen.writeFieldName(value.getX() + "," + value.getZ());
    }
}
