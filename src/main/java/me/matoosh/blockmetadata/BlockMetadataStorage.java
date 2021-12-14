package me.matoosh.blockmetadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * Service managing the storage of block metadata.
 * @param <T> The type of metadata to store.
 */
@Log
public class BlockMetadataStorage<T extends Serializable> {

    /**
     * Path of the data folder.
     */
    private final Path dataPath;

    /**
     * Currently loaded metadata of each block grouped by chunk.
     */
    private final Map<Chunk, Map<String, T>> metadata = new HashMap<>();

    /**
     * Regions that are currently being loaded/persisted and their load futures.
     */
    private final Map<Path, RegionTask> busyRegions = new HashMap<>();

    /**
     * Chunks that are currently loaded.
     */
    private final Map<Chunk, LoadedChunkData> loadedChunks = new HashMap<>();

    /**
     * YAML data file mapper.
     */
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    /**
     * The currently loading chunks.
     * Prevents double loads.
     */
    private final Map<Chunk, CompletableFuture<Void>> loadingChunks = new HashMap<>();

    /**
     * The currently saving chunks.
     */
    private final Map<Chunk, CompletableFuture<Void>> savingChunks = new HashMap<>();

    /**
     * Delay before the chunk metadata clenup task is executed.
     */
    private final int CLEANUP_TASK_DELAY = 2500;

    /**
     * Cleanup task delay function.
     */
    private final Function<Void, Boolean> CLEANUP_DELAY = (t) -> {
        // delay
        try {
            Thread.sleep(CLEANUP_TASK_DELAY);
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    };

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
        return getMetadataInChunk(block.getChunk()).thenApply((metadata) -> {
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
            return getMetadataInChunk(block.getChunk()).thenApply((d) -> {
                // make sure theres a map to put data in
                if (d == null) {
                    d = new HashMap<>();
                }
                d.put(getBlockKeyInChunk(block), data);

                // set chunk as dirty
                LoadedChunkData loadedChunkData = loadedChunks.get(block.getChunk());
                loadedChunkData.setDirty(true);

                // update the data
                metadata.put(block.getChunk(), d);

                return null;
            });
        }
    }

    /**
     * Removes metadata for a block.
     * @param block The block.
     * @return The removed metadata value. Null if no value was stored.
     */
    public CompletableFuture<T> removeMetadata(@NonNull Block block) {
        Chunk chunk = block.getChunk();
        return getMetadataInChunk(chunk).thenApply((metadata) -> {
            // no metadata in chunk
            if (metadata == null) {
                return null;
            }

            // remove metadata value from map
            T value = metadata.remove(getBlockKeyInChunk(block));

            // if this was the last one left remove metadata entry for this chunk
            if (metadata.size() == 0) {
                removeMetadataForChunk(chunk);
            }

            return value;
        });
    }

    /**
     * Checks whether there are metadata stored for a given chunk.
     * @param chunk The chunk to check.
     * @return Whether there are metadata stored for the given chunk.
     */
    public CompletableFuture<Boolean> hasMetadataForChunk(@NonNull Chunk chunk) {
        return loadChunk(chunk).thenApply((s) -> metadata.containsKey(chunk));
    }

    /**
     * Removes all metadata stored for a given chunk.
     * @param chunk The chunk to remove metadata from.
     */
    public CompletableFuture<Map<String, T>> removeMetadataForChunk(@NonNull Chunk chunk) {
        return loadChunk(chunk).thenRun(() -> {
            // set chunk as dirty
            LoadedChunkData loadedChunkData = loadedChunks.get(chunk);
            loadedChunkData.setDirty(true);
        }).thenApply((s) -> metadata.remove(chunk));
    }

    /**
     * Gets metadata of blocks in a chunk.
     * @param chunk The chunk in which the metadata are.
     * @return Map of metadata.
     */
    public CompletableFuture<Map<String, T>> getMetadataInChunk(@NonNull Chunk chunk) {
        return loadChunk(chunk).thenApply((s) -> metadata.get(chunk));
    }


    /**
     * Sets all the data in a given chunk to the specified data.
     * @param chunk The chunk for which to set metadata.
     * @param data The metadata map.
     */
    public CompletableFuture<Void> setMetadataInChunk(@NonNull Chunk chunk, Map<String, T> data) {
        return loadChunk(chunk).thenRun(() -> {
            // set chunk as dirt
            LoadedChunkData loadedChunkData = loadedChunks.get(chunk);
            loadedChunkData.setDirty(true);

            // update the data
            if (data == null || data.size() == 0) {
                metadata.remove(chunk);
            } else {
                metadata.put(chunk, data);
            }
        });
    }

    /**
     * Schedules a region task.
     * Region tasks are executed asynchronously,
     * but only 1 task can run for a single region
     * at any time.
     * @param chunk Chunk which is in the region which we want to schedule a task for.
     * @param task The task to be scheduled.
     * @return Future of the task.
     */
    private CompletableFuture<Void> scheduleRegionTask(
            @NonNull Chunk chunk, @NonNull java.util.function.Function
            <Void, ? extends CompletableFuture<Void>> task) {
        // get region task
        Path regionFile = getRegionFile(chunk);
        RegionTask regionTask = busyRegions.computeIfAbsent(regionFile, k -> new RegionTask());

        // cancel last region cleanup task
        if (regionTask.getCleanupTask() != null) {
            regionTask.getCleanupTask().cancel(true);
        }

        // set this future as the latest future for the region
        // schedule operation
        CompletableFuture<Void> newTask;
        if (regionTask.getTask() != null) {
            // append future and run it on the same tread as last one
            newTask = regionTask.getTask().thenCompose(task);
        } else {
            // first future in the chain
            newTask = task.apply(null);
        }

        // append clean up task after the task is done
        CompletableFuture<Boolean> cleanupTask = newTask.thenApplyAsync(CLEANUP_DELAY);
        CompletableFuture<Void> completeTask = cleanupTask
            .exceptionally((e) -> {
                if (e instanceof CancellationException) {
                    // cleanup task canceled, therefore not last in chain
                    return false;
                }
                throw new CompletionException(e);
            })
            .thenApply((lastInChain) -> {
                // check if we should continue
                if (!lastInChain) {
                    return null;
                }

                // there is no task scheduled after this one for now because this one wasn't cancelled
                busyRegions.remove(regionFile);
                // clear the region record and commit if the buffer was modified
                if (regionTask.isDirty()) {
                    return regionTask.getBuffer();
                } else {
                    return null;
                }
            })
            .thenCompose((data) -> data != null
            ? writeRegionData(regionFile, data) : CompletableFuture.completedFuture(null));

        // set new task
        regionTask.setTask(newTask);
        regionTask.setCleanupTask(cleanupTask);

        return newTask;
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

        // check if region is buffered
        RegionTask task = busyRegions.get(regionFile);
        if (task != null && task.isBuffered()) {
            return CompletableFuture.completedFuture(task.getBuffer());
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
                    Map<String, Map<String, T>> data = mapper.readValue(
                        content, new TypeReference<Map<String, Map<String, T>>>() {
                    });

                    // buffer data
                    if (task != null) {
                        task.setBuffer(data);
                        task.setBuffered(true);
                    }

                    return data;
                } catch (JsonProcessingException e) {
                    throw new CompletionException(e);
                }
            });
    }

    /**
     * Writes the region data to the buffer.
     * Buffered region data will be written to disk at the end of each region chain.
     * @param regionFile Path to the region file.
     * @return The number of bytes that were written to disk.
     */
    private CompletableFuture<Void> bufferRegionData(
            @NonNull Path regionFile, Map<String, Map<String, T>> data) {
        RegionTask regionTask = busyRegions.get(regionFile);
        if (regionTask != null) {
            regionTask.setBuffer(data);
            regionTask.setBuffered(true);
            regionTask.setDirty(true);
            return CompletableFuture.completedFuture(null);
        } else {
            return writeRegionData(regionFile, data);
        }
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
     * Persists metadata of specified chunks on disk.
     * @param chunks Chunks to persist metadata for.
     */
    public CompletableFuture<Void> persistChunks(Chunk... chunks) {
        CompletableFuture<Void>[] tasks = new CompletableFuture[chunks.length];
        for (int i = 0; i < chunks.length; i++) {
            tasks[i] = persistChunk(chunks[i]);
        }
        return CompletableFuture.allOf(tasks);
    }

    /**
     * Persists chunk metadata on disk and unloads the metadata from memory.
     * @param chunk The chunk to persist.
     */
    public CompletableFuture<Void> persistChunk(@NonNull Chunk chunk) {
        // check if chunk dirty
        if (!isChunkDirty(chunk)) {
            // not dirty, nothing to save to disk
            loadedChunks.remove(chunk);

            return CompletableFuture.completedFuture(null);
        }

        // check if chunk is already being persisted
        if (savingChunks.containsKey(chunk)) {
            return savingChunks.get(chunk);
        }

        // save chunk asynchronously
        CompletableFuture<Void> future = scheduleRegionTask(chunk, (s) -> persistChunkAsync(chunk))
                .thenRun(() -> savingChunks.remove(chunk));
        savingChunks.put(chunk, future);
        return future;
    }

    /**
     * Persists chunk metadata on disk asynchronously.
     * @param chunk The chunk to persist.
     */
    private CompletableFuture<Void> persistChunkAsync(@NonNull Chunk chunk) {
        return CompletableFuture.supplyAsync(() -> getRegionFile(chunk))
            .thenCompose(this::readRegionData)
            .exceptionally((e) -> {
                e.printStackTrace();
                return null;
            })
            .thenApply((data) -> {
                // get chunk data
                Map<String, T> chunkMetadata = metadata.remove(chunk);
                loadedChunks.remove(chunk);

                // update data
                String chunkSection = getChunkKey(chunk);
                if (data != null) {
                    // metadata already stored for this region, append
                    if (chunkMetadata == null || chunkMetadata.size() == 0) {
                        // no metadata to save for this chunk, remove entry from region
                        data.remove(chunkSection);
                    } else {
                        // update metadata for this chunk
                        data.put(chunkSection, chunkMetadata);
                    }
                } else if (chunkMetadata != null) {
                    // no metadata stored for this region yet, create new map
                    data = new HashMap<>();
                    data.put(chunkSection, chunkMetadata);
                }

                // delete empty region files
                if (data != null && data.size() == 0) {
                    data = null;
                }

                return data;
            })
            .thenCompose((data) -> bufferRegionData(getRegionFile(chunk), data))
            .exceptionally((e) -> {
                e.printStackTrace();
                return null;
            });
    }

    /**
     * Loads chunk metadata for each specified chunk.
     * @param chunks Chunks to load metadata for.
     */
    public CompletableFuture<Void> loadChunks(Chunk... chunks) {
        CompletableFuture<Void>[] tasks = new CompletableFuture[chunks.length];
        for (int i = 0; i < chunks.length; i++) {
            tasks[i] = loadChunk(chunks[i]);
        }
        return CompletableFuture.allOf(tasks);
    }

    /**
     * Loads chunk metadata into memory.
     * @param chunk The chunk.
     */
    public CompletableFuture<Void> loadChunk(@NonNull Chunk chunk) {
        // check if chunk loaded
        if (isChunkLoaded(chunk)) {
            return CompletableFuture.completedFuture(null);
        }

        // check if chunk is already loading
        if (loadingChunks.containsKey(chunk)) {
            return loadingChunks.get(chunk);
        }

        // load chunk asynchronously
        CompletableFuture<Void> future = scheduleRegionTask(chunk, (s) -> loadChunkAsync(chunk))
                .thenRun(() -> loadingChunks.remove(chunk));
        loadingChunks.put(chunk, future);
        return future;
    }

    /**
     * Loads chunk metadata into memory synchronously.
     * @param chunk The chunk.
     */
    private CompletableFuture<Void> loadChunkAsync(@NonNull Chunk chunk) {
        return CompletableFuture.supplyAsync(() -> getRegionFile(chunk))
            .thenCompose(this::readRegionData)
            .exceptionally((e) -> {
                e.printStackTrace();
                return null;
            })
            .thenAccept((data) -> {
                // check if load was successful
                if (data == null) {
                    // no data for this chunk
                    loadedChunks.put(chunk, new LoadedChunkData());
                    return;
                }

                // load
                String chunkSection = getChunkKey(chunk);
                Map<String, T> chunkData = data.get(chunkSection);
                if (chunkData != null) {
                    metadata.put(chunk, chunkData);
                }

                // loaded
                loadedChunks.put(chunk, new LoadedChunkData());
            });
    }

    /**
     * Checks whether the specified chunk is busy.
     * A chunk is busy if it's being saved.
     * @param chunk The chunk.
     * @return Whether the chunk is busy.
     */
    public boolean isChunkBusy(Chunk chunk) {
        return savingChunks.containsKey(chunk);
    }

    /**
     * Checks whether the specified chunk is loading.
     * @param chunk The chunk.
     * @return Whether the chunk is loading.
     */
    public boolean isChunkLoading(Chunk chunk) {
        return loadingChunks.containsKey(chunk);
    }

    /**
     * Checks whether the specified chunk is loaded.
     * @param chunk The chunk.
     * @return Whether the chunk is loaded.
     */
    public boolean isChunkLoaded(Chunk chunk) {
        LoadedChunkData loadedChunkData = loadedChunks.get(chunk);
        return loadedChunkData != null;
    }

    /**
     * Checks whether the specified chunk is dirty.
     * A chunk is dirty if it has been modified since is was loaded into memory.
     * @param chunk The chunk.
     * @return Whether the chunk is dirty.
     */
    public boolean isChunkDirty(Chunk chunk) {
        LoadedChunkData loadedChunkData = loadedChunks.get(chunk);
        if (loadedChunkData == null) {
            return false;
        }
        return loadedChunkData.isDirty();
    }

    /**
     * Gets a key unique for a block in a chunk.
     * @param block The block for which to get the key.
     * @return The key.
     */
    public static String getBlockKeyInChunk(Block block) {
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
     * @param chunk The chunk.
     * @return The chunk key.
     */
    private String getChunkKey(Chunk chunk) {
        return chunk.getX() + "," + chunk.getZ();
    }

    /**
     * Get file name under which a region file should be saved.
     * @param chunk The chunk in the saved region.
     * @return The file name.
     */
    private Path getRegionFile(Chunk chunk) {
        return dataPath.resolve(chunk.getWorld().getName() + "_" + getRegionKey(chunk) + ".yml");
    }

    /**
     * Get a key unique to a region in which a chunk is located.
     * @param chunk The chunk in the region.
     * @return The region key.
     */
    private String getRegionKey(Chunk chunk) {
        return (chunk.getX() / 16) + "_" + (chunk.getZ() / 16);
    }

    /**
     * Get the currently loading chunks.
     * @return List of all currently loading chunks and their futures.
     */
    public Map<Chunk, CompletableFuture<Void>> getLoadingChunks() {
        return loadingChunks;
    }

    /**
     * Get the currently saving chunks.
     * @return List of all currently saving chunks and their futures.
     */
    public Map<Chunk, CompletableFuture<Void>> getSavingChunks() {
        return savingChunks;
    }

    /**
     * Represents a metadata task on a region.
     */
    @Data
    private class RegionTask {
        /**
         * Future of the task that is currently running on the region.
         */
        private CompletableFuture<Void> task;
        /**
         * Task which cleans up the task's buffer and commits the region metadata
         * to disk if it was modified. This is scheduled 2 seconds after the currently
         * running task future. If a new task is scheduled before the 2 seconds,
         * this cleanup task will be cancelled.
         */
        private CompletableFuture<Boolean> cleanupTask;
        /**
         * Buffered metadata for the blocks within the region.
         * Use of the buffer speeds up processing.
         * The buffer is committed to disk after a chain of operations on
         * the region is finished.
         */
        private Map<String, Map<String, T>> buffer;
        /**
         * Whether the metadata for this region has been modified.
         */
        private boolean dirty;
        /**
         * Whether the metadata for this region is buffered.
         * If true, the metadata is stored in the buffer variable.
         */
        private boolean buffered;
    }

    /**
     * Information about a loaded chunk
     */
    @Data
    private static class LoadedChunkData {
        private boolean dirty;
    }
}
