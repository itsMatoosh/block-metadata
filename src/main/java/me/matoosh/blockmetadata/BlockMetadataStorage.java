package me.matoosh.blockmetadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.java.Log;
import me.matoosh.blockmetadata.async.AsyncFiles;
import me.matoosh.blockmetadata.event.BlockDestroyHandler;
import me.matoosh.blockmetadata.event.BlockMoveHandler;
import me.matoosh.blockmetadata.event.ChunkLoadHandler;
import me.matoosh.blockmetadata.event.PluginDisableHandler;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Service managing the storage of block metadata.
 * @param <T> The type of metadata to store.
 */
@Log
@Getter
public class BlockMetadataStorage<T extends Serializable> {

    /**
     * Path of the data folder.
     */
    private final Path dataPath;

    /**
     * Currently loaded regions.
     */
    private final Map<String, Region> regions = new HashMap<>();

    /**
     * YAML data file mapper.
     */
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    /**
     * Instantiates a new block metadata storage with automatic loading/saving.
     * @param plugin Instance of the plugin.
     * @param dataPath Path where the metadata should be stored on disk.
     */
    public BlockMetadataStorage(JavaPlugin plugin, Path dataPath) {
        // set data path
        try {
            Files.createDirectories(dataPath);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        this.dataPath = dataPath;

        // automatically manage metadata loading/saving
        Bukkit.getPluginManager().registerEvents(
                new ChunkLoadHandler<>(this), plugin);

        // automatically manage blocks moved by pistons
        Bukkit.getPluginManager().registerEvents(
                new BlockMoveHandler<>(this), plugin);

        // automatically manage blocks that get destroyed
        Bukkit.getPluginManager().registerEvents(
                new BlockDestroyHandler<>(this), plugin);

        // automatically manage metadata saving when plugin disabled
        Bukkit.getPluginManager().registerEvents(
                new PluginDisableHandler<>(this), plugin);

        log.info("Block Metadata storage registered at: " + dataPath);
    }

    /**
     * Get metadata of a block.
     * @param block The block.
     * @return Current metadata of the block. Null if no data stored.
     */
    public CompletableFuture<T> getMetadata(@NonNull Block block) {
        // get chunk
        return getMetadataInChunk(ChunkInfo.fromChunk(block.getChunk()))
        .thenApply((metadata) -> {
            if (metadata == null) {
                // no data for this chunk
                return null;
            }

            // get block metadata
            return metadata.get(getBlockKeyInChunk(block));
        });
    }

    /**
     * Set metadata of a block.
     * @param block The block.
     * @param data Metadata to set to the block.
     */
    public CompletableFuture<Void> setMetadata(@NonNull Block block, T data) {
        if (data == null) {
            // clear metadata
            return removeMetadata(block).thenApply((s) -> null);
        } else {
            // set metadata
            ChunkInfo chunkInfo = ChunkInfo.fromChunk(block.getChunk());
            return getMetadataInChunk(chunkInfo)
            .thenApply((d) -> {
                // make sure there's a map to put data in
                if (d == null) {
                    d = new HashMap<>();
                }

                // insert data
                d.put(getBlockKeyInChunk(block), data);

                return d;
            }).thenCompose((d) -> setMetadataInChunk(chunkInfo, d));
        }
    }

    /**
     * Removes metadata for a block.
     * @param block The block.
     * @return The removed metadata value. Null if no value was stored.
     */
    public CompletableFuture<T> removeMetadata(@NonNull Block block) {
        // get chunk
        ChunkInfo chunkInfo = ChunkInfo.fromChunk(block.getChunk());

        // get and remove value from the metadata
        CompletableFuture<T> valueFuture = getMetadataInChunk(chunkInfo)
        .thenApply((metadata) -> {
            // no metadata in chunk
            if (metadata == null) {
                return null;
            }

            // get metadata value from the map
            return metadata.remove(getBlockKeyInChunk(block));
        });
        // if no metadata remaining in chunk, remove chunk section
        CompletableFuture<Void> removeChunkFuture = valueFuture.thenCompose((s) -> getMetadataInChunk(chunkInfo))
                .thenApply((data) -> data != null && data.size() == 0)
                .thenCompose((removeChunk) -> removeChunk
                        ? removeMetadataForChunk(chunkInfo).thenApply((s) -> null)
                        : CompletableFuture.completedFuture(null));
        // wait until chunk section is removed before continuing
        return valueFuture.thenCombine(removeChunkFuture, (val, n) -> val);
    }

    /**
     * Checks whether there are metadata stored for a given chunk.
     * @param chunkInfo Information about the chunk.
     * @return Whether there are metadata stored for the given chunk.
     */
    public CompletableFuture<Boolean> hasMetadataForChunk(@NonNull ChunkInfo chunkInfo) {
        return getRegion(chunkInfo).thenApply((
                region -> region != null && region.getBuffer() != null
                        && region.getBuffer().containsKey(getChunkKey(chunkInfo.getCoordinates()))));
    }

    /**
     * Removes all metadata stored for a given chunk.
     * @param chunkInfo Information about the chunk.
     */
    public CompletableFuture<Map<String, T>> removeMetadataForChunk(@NonNull ChunkInfo chunkInfo) {
        CompletableFuture<Map<String, T>> valueFuture = getRegion(chunkInfo).thenApply((region) -> {
            // check if buffer exists
            if (region.getBuffer() == null) {
                return null;
            }
            // remove metadata
            Map<String, T> metadata = region.getBuffer().remove(getChunkKey(chunkInfo.getCoordinates()));
            if (metadata != null) {
                // set region as dirty
                region.setDirty(true);
                return metadata;
            } else {
                return null;
            }
        });
        CompletableFuture<Void> removeRegionFuture = valueFuture.thenCompose((s) -> getRegion(chunkInfo))
            .thenAccept((region) -> {
                if (region != null && region.getBuffer().size() == 0) {
                    region.setBuffer(null);
                }
            });
        return valueFuture.thenCombine(removeRegionFuture, (val, n) -> val);
    }

    /**
     * Gets metadata of blocks in a chunk.
     * @param chunkInfo Information about the chunk.
     * @return Map of metadata.
     */
    public CompletableFuture<Map<String, T>> getMetadataInChunk(@NonNull ChunkInfo chunkInfo) {
        return getRegion(chunkInfo).thenApply((region) -> region.getBuffer() != null
                ? region.getBuffer().get(getChunkKey(chunkInfo.getCoordinates()))
                : null);
    }

    /**
     * Resolves a region asynchronously.
     * @param chunkInfo Information about the chunk.
     * @return The region data future.
     */
    public CompletableFuture<Region> getRegion(@NonNull ChunkInfo chunkInfo) {
        // get region
        String regionKey = getRegionKey(chunkInfo);
        Region region = regions.get(regionKey);
        if (region == null) {
            // buffer region
            Path regionPath = getRegionFile(chunkInfo);
            Region newRegion = new Region(regionKey, regionPath);
            regions.put(newRegion.getKey(), newRegion);

            // load region
            ExecutorService regionExeService = Executors.newSingleThreadExecutor();
            CompletableFuture<Void> loadFuture = loadRegion(newRegion.getFilePath(), regionExeService)
                    .thenAccept(newRegion::setBuffer);
            newRegion.setLoadFuture(loadFuture);

            // wait until region loads
            return newRegion.getLoadFuture().thenApply((d) -> newRegion);
        } else {
            // wait until region loads
            return region.getLoadFuture().thenApply((d) -> region);
        }
    }


    /**
     * Sets all the data in a given chunk to the specified data.
     * @param chunkInfo Information about the chunk.
     * @param data The metadata map.
     */
    public CompletableFuture<Void> setMetadataInChunk(@NonNull ChunkInfo chunkInfo, Map<String, T> data) {
        return getRegion(chunkInfo).thenAccept((region) -> {
            // update the data
            String chunkKey = getChunkKey(chunkInfo.getCoordinates());
            if (data == null || data.size() == 0) {
                // remove the chunk data
                Map<String, T> removed = region.getBuffer().remove(chunkKey);
                if (removed != null) {
                    // set region as dirty
                    region.setDirty(true);
                }
            } else {
                // ensure buffer exists
                if (region.getBuffer() == null) {
                    region.setBuffer(new HashMap<>());
                }
                region.getBuffer().put(chunkKey, data);

                // set region as dirty
                region.setDirty(true);
            }
        });
    }

    /**
     * Reads metadata stored for a region.
     * @param regionFile Path to the region file.
     * @return Map of region metadata.
     */
    private CompletableFuture<Map<String, Map<String, T>>> readRegionData(Path regionFile) {
        // no file to read
        if (regionFile == null) {
            return CompletableFuture.completedFuture(null);
        }

        // check if file exists
        if (!Files.exists(regionFile)) {
            return CompletableFuture.completedFuture(null);
        }

        // read file text
        return AsyncFiles.readAll(regionFile, 1024)
            .thenApply(content -> {
                try {
                    // parse file
                    return mapper.readValue(
                        content, new TypeReference<Map<String, Map<String, T>>>() {
                    });
                } catch (JsonProcessingException e) {
                    throw new CompletionException(e);
                }
            });
    }

    /**
     * Writes region data to disk.
     * @param regionFile Path to the region file.
     * @return The number of bytes that were written to disk.
     */
    private CompletableFuture<Void> writeRegionData(
            @NonNull Path regionFile, Map<String, Map<String, T>> data) {
        // remove old file to overwrite
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Files.deleteIfExists(regionFile);
            } catch (IOException ignored) {
                return false;
            }
        }).thenApply((s) -> {
            // remove empty region files
            if (data == null) return null;

            // serialize to yaml
            try {
                return mapper.writeValueAsString(data);
            } catch (JsonProcessingException e) {
                throw new CompletionException(e);
            }
        }).thenCompose((content) -> content != null
            ? AsyncFiles.writeBytes(regionFile, content.getBytes(),
            StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).thenApply((s) -> null)
            : CompletableFuture.completedFuture(null));
    }

    /**
     * Loads chunk metadata into memory.
     * @param chunkInfo Information about the chunk.
     */
    public CompletableFuture<Void> loadChunk(ChunkInfo chunkInfo) {
        // add chunk as active in its region
        // possibly load region file if not loaded already
        return getRegion(chunkInfo).thenAccept((region) -> region.addActiveChunk(chunkInfo.getCoordinates()));
    }

    /**
     * Loads chunk metadata for each specified chunk.
     * @param chunks Chunks to load metadata for.
     */
    public CompletableFuture<Void> loadChunks(ChunkInfo... chunks) {
        CompletableFuture<Void>[] tasks = new CompletableFuture[chunks.length];
        for (int i = 0; i < chunks.length; i++) {
            tasks[i] = loadChunk(chunks[i]);
        }
        return CompletableFuture.allOf(tasks);
    }

    /**
     * Unloads chunk metadata from memory.
     * @param chunkInfo Information about the chunk.
     */
    public CompletableFuture<Void> unloadChunk(@NonNull ChunkInfo chunkInfo) {
        // remove chunk from active chunks in the region
        // possibly persist region
        return getRegion(chunkInfo)
                .thenApply((region) -> region.removeActiveChunk(chunkInfo.getCoordinates()))
                .thenCompose((activeChunks) -> activeChunks == 0
                        ? saveRegion(regions.get(getRegionKey(chunkInfo)))
                        : CompletableFuture.completedFuture(null));
    }

    /**
     * Persists metadata of specified chunks on disk.
     * @param chunks Chunks to persist metadata for.
     */
    public CompletableFuture<Void> unloadChunks(ChunkInfo... chunks) {
        CompletableFuture<Void>[] tasks = new CompletableFuture[chunks.length];
        for (int i = 0; i < chunks.length; i++) {
            tasks[i] = unloadChunk(chunks[i]);
        }
        return CompletableFuture.allOf(tasks);
    }

    /**
     * Checks whether the specified chunk is busy.
     * A chunk is busy if it's being saved.
     * @param chunkInfo Information about the chunk.
     * @return Whether the chunk is busy.
     */
    public boolean isChunkSaving(@NonNull ChunkInfo chunkInfo) {
        Region region = regions.get(getRegionKey(chunkInfo));
        return region != null && region.getSaveFuture() != null && !region.getSaveFuture().isDone();
    }

    /**
     * Checks whether the specified chunk is loading.
     * @param chunkInfo Information about the chunk.
     * @return Whether the chunk is loading.
     */
    public boolean isChunkLoading(@NonNull ChunkInfo chunkInfo) {
        Region region = regions.get(getRegionKey(chunkInfo));
        return region != null && !region.getLoadFuture().isDone();
    }

    /**
     * Checks whether the specified chunk is loaded.
     * @param chunkInfo Information about the chunk.
     * @return Whether the chunk is loaded.
     */
    public boolean isChunkLoaded(@NonNull ChunkInfo chunkInfo) {
        Region region = regions.get(getRegionKey(chunkInfo));
        return region != null && region.getLoadFuture().isDone();
    }

    /**
     * Checks whether the specified chunk is dirty.
     * A chunk is dirty if it has been modified since is was loaded into memory.
     * @param chunkInfo Information about the chunk.
     * @return Whether the chunk is dirty.
     */
    public boolean isChunkDirty(@NonNull ChunkInfo chunkInfo) {
        Region region = regions.get(getRegionKey(chunkInfo));
        return region != null && region.isDirty();
    }

    /**
     * Loads region metadata into memory synchronously.
     * @param regionPath Path to the region file.
     * @param executorService Executor service to use.
     */
    private CompletableFuture<Map<String, Map<String, T>>> loadRegion(
            @NonNull Path regionPath, @NonNull ExecutorService executorService) {
        return CompletableFuture.supplyAsync(() -> regionPath, executorService)
            // read region file
            .thenCompose(this::readRegionData)
            // if there was an error reading, print it
            .exceptionally((e) -> {
                e.printStackTrace();
                return null;
            });
    }

    /**
     * Saves and unloads all loaded regions.
     * @return
     */
    public CompletableFuture<Void> saveAllLoadedRegions() {
        // copy region references
        List<Region> toSave = new ArrayList<>(regions.size());
        toSave.addAll(regions.values());

        // save all
        CompletableFuture<Void>[] futures = new CompletableFuture[toSave.size()];
        int i = 0;
        for (Region r : toSave) {
            futures[i++] = saveRegion(r);
        }

        // wait for all to save
        return CompletableFuture.allOf(futures);
    }

    /**
     * Saves region metadata on disk and unloads the metadata from memory.
     * @param region The region to save.
     */
    public CompletableFuture<Void> saveRegion(@NonNull Region region) {
        // check if chunk is already being persisted
        if (region.getSaveFuture() != null) {
            return region.getSaveFuture();
        }

        // ensure region is dirty
        if (!region.isDirty()) {
            // not dirty, nothing to save to disk
            regions.remove(region.getKey());
            region.setSaveFuture(region.getLoadFuture());
            return region.getSaveFuture();
        }

        // save region asynchronously
        CompletableFuture<Void> saveFuture = region.getLoadFuture()
                .thenCompose((s) -> writeRegionData(region.getFilePath(), region.getBuffer()))
                .thenRun(() -> regions.remove(region.getKey()));
        region.setSaveFuture(saveFuture);
        return region.getSaveFuture();
    }

    /**
     * Get the currently loading regions.
     * @return List of all currently loading regions.
     */
    public Set<Region> getLoadingRegions() {
        return regions.values().stream()
                .filter((region) -> !region.getLoadFuture().isDone())
                .collect(Collectors.toSet());
    }

    /**
     * Get the currently saving regions.
     * @return List of all currently saving regions.
     */
    public Set<Region> getSavingRegions() {
        return regions.values().stream()
                .filter((region -> region.getSaveFuture() != null && !region.getSaveFuture().isDone()))
                .collect(Collectors.toSet());
    }

    /**
     * Get a key unique to a region in which a chunk is located.
     * @param chunkInfo Information about the chunk.
     * @return The region key.
     */
    private static String getRegionKey(@NonNull ChunkInfo chunkInfo) {
        return chunkInfo.getWorld() + "_" + (chunkInfo.getCoordinates().getX() / 16)
                + "_" + (chunkInfo.getCoordinates().getZ() / 16);
    }

    /**
     * Get file name under which a region file should be saved.
     * @param chunkInfo Information about the chunk.
     * @return The file name.
     */
    private Path getRegionFile(@NonNull ChunkInfo chunkInfo) {
        return dataPath.resolve(getRegionKey(chunkInfo) + ".yml");
    }

    /**
     * Gets a key unique for a block in a chunk.
     * @param block The block for which to get the key.
     * @return The key.
     */
    public static String getBlockKeyInChunk(@NonNull Block block) {
        Chunk chunk = block.getChunk();
        return getBlockKeyInChunk(
                block.getX() - chunk.getX() * 16,
                block.getY(),
                block.getZ() - chunk.getZ() * 16);
    }

    /**
     * Gets a key unique for a block in a chunk.
     * @param x X coordinate of the block relative to the chunk.
     * @param y Y coordinate of the block.
     * @param z Z coordinate of the block relative to the chunk.
     * @return The key.
     */
    public static String getBlockKeyInChunk(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /**
     * Get a key unique to a chunk within a metadata region file.
     * @param coordinates Coordinates of the chunk.
     * @return The chunk key.
     */
    private static String getChunkKey(@NonNull ChunkCoordinates coordinates) {
        return coordinates.getX() + "," + coordinates.getZ();
    }


    /**
     * Represents a metadata task on a region.
     */
    @Data
    private class Region {
        /**
         * Region key.
         */
        private final String key;
        /**
         * Region file path.
         */
        private final Path filePath;
        /**
         * Currently active chunks in this region.
         */
        private final Set<String> activeChunks = new HashSet<>();

        /**
         * Future loading the current region.
         */
        private CompletableFuture<Void> loadFuture;
        /**
         * Future saving the current region.
         */
        private CompletableFuture<Void> saveFuture;
        /**
         * The buffer of this region.
         */
        private Map<String, Map<String, T>> buffer;
        /**
         * Whether the metadata for this region has been modified.
         */
        private boolean dirty;

        /**
         * Adds an active chunk.
         * @param coordinates Coordinates of the chunk.
         * @return The number of active chunks.
         */
        public int addActiveChunk(@NonNull ChunkCoordinates coordinates) {
            activeChunks.add(getChunkKey(coordinates));
            return activeChunks.size();
        }

        /**
         * Removes an active chunk.
         * @param coordinates Coordinates of the chunk.
         * @return The number of active chunks.
         */
        public int removeActiveChunk(@NonNull ChunkCoordinates coordinates) {
            activeChunks.remove(getChunkKey(coordinates));
            return activeChunks.size();
        }
    }
}
