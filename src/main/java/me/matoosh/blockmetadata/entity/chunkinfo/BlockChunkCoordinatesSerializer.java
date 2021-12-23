package me.matoosh.blockmetadata.entity.chunkinfo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

public class BlockChunkCoordinatesSerializer extends StdSerializer<BlockChunkCoordinates> {

    public BlockChunkCoordinatesSerializer() {
        this(null);
    }

    public BlockChunkCoordinatesSerializer(Class<BlockChunkCoordinates> t) {
        super(t);
    }

    @Override
    public void serialize(BlockChunkCoordinates value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeFieldName(value.getX() + "," + value.getY() + "," + value.getZ());
    }
}
