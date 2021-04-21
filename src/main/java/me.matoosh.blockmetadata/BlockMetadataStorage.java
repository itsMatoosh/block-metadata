package me.matoosh.blockmetadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import me.matoosh.blockmetadata.async.AsyncFiles;
import me.matoosh.blockmetadata.exception.ChunkAlreadyLoadedException;
import me.matoosh.blockmetadata.exception.ChunkBusyException;
import me.matoosh.blockmetadata.exception.ChunkNotLoadedException;
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

/**
 * Service managing the storage of block metadata.
 * @param <T> The type of metadata to store.
 */
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
        Bukkit.getPluginManager().registerEvents(new ChunkLoadHandler<>(this), plugin);
    }

    /**
     * Instantiates a new block metadata storage.
     * @param dataPath Path where the metadata should be stored on disk.
     */
    public BlockMetadataStorage(Path dataPath) {
        // set data path
        this.dataPath = dataPath;
    }

    /**
     * Get metadata of a block.
     * @param block The block.
     * @return Current metadata of the block. Null if no data stored.
     * @throws ChunkBusyException Thrown if the chunk is busy.
     * @throws ChunkNotLoadedException Thrown if the chunk is not loaded.
     * @return The fetched metadata value.
     */
    public T getMetadata(Block block)
            throws ChunkBusyException, ChunkNotLoadedException {
        if (block == null) return null;

        // get chunk
        Map<String, T> metadata = getMetadataInChunk(block.getChunk());
        if (metadata == null) {
            // no data for this chunk
            return null;
        }

        // get block metadata
        return metadata.get(getBlockKeyInChunk(block));
    }

    /**
     * Set metadata of a block.
     * @param block The block.
     * @param metadata Metadata to set to the block.
     * @throws ChunkBusyException Thrown if the chunk is busy.
     * @throws ChunkNotLoadedException Thrown if the chunk is not loaded.
     */
    public void setMetadata(Block block, T metadata)
            throws ChunkBusyException, ChunkNotLoadedException {
        if (metadata == null) {
            // clear metadata
            removeMetadata(block);
        } else {
            // set metadata
            modifyMetadataInChunk(block.getChunk()).put(getBlockKeyInChunk(block), metadata);
        }
    }

    /**
     * Removes metadata for a block.
     * @param block The block.
     * @throws ChunkBusyException Thrown if the chunk is busy.
     * @throws ChunkNotLoadedException Thrown if the chunk is not loaded.
     * @return The removed metadata value. Null if no value was stored.
     */
    public T removeMetadata(Block block)
            throws ChunkBusyException, ChunkNotLoadedException {
        // cache chunk
        Chunk chunk = block.getChunk();

        // check if there are durabilities in the chunk
        if (!hasMetadataForChunk(chunk)) {
            return null;
        }

        // get chunk
        Map<String, T> metadata = modifyMetadataInChunk(chunk);

        // remove from map
        T value = metadata.remove(getBlockKeyInChunk(block));

        // check if last value
        if (metadata.size() < 1) {
            // remove chunk from storage
            removeMetadataForChunk(chunk);
        }

        return value;
    }

    /**
     * Checks whether there are metadata stored for a given chunk.
     * @param chunk The chunk to check.
     * @return Whether there are metadata stored for the given chunk.
     * @throws ChunkBusyException Thrown if the chunk is busy.
     * @throws ChunkNotLoadedException Thrown if the chunk is not loaded.
     */
    public boolean hasMetadataForChunk(Chunk chunk)
            throws ChunkBusyException, ChunkNotLoadedException {
        ensureChunkReady(chunk);
        return metadata.containsKey(chunk);
    }

    /**
     * Removes all metadata stored for a given chunk.
     * @param chunk The chunk to remove metadata from.
     * @throws ChunkBusyException Thrown if the chunk is busy.
     * @throws ChunkNotLoadedException Thrown if the chunk is not loaded.
     */
    public void removeMetadataForChunk(Chunk chunk)
            throws ChunkBusyException, ChunkNotLoadedException {
        ensureChunkReady(chunk);
        metadata.remove(chunk);
        LoadedChunkData loadedChunkData = loadedChunks.get(chunk);
        loadedChunkData.setDirty(true);
    }

    /**
     * Gets metadata of blocks in a chunk to be modified.
     * Sets the chunk as dirty.
     * @param chunk The chunk in which the metadata are.
     * @return Map of metadata.
     * @throws ChunkBusyException Thrown if the chunk is busy.
     * @throws ChunkNotLoadedException Thrown if the chunk is not loaded.
     */
    public Map<String, T> modifyMetadataInChunk(Chunk chunk)
            throws ChunkBusyException, ChunkNotLoadedException {
        ensureChunkReady(chunk);
        Map<String, T> data = metadata.computeIfAbsent(chunk, k -> new HashMap<>());
        LoadedChunkData loadedChunkData = loadedChunks.get(chunk);
        loadedChunkData.setDirty(true);
        return data;
    }

    /**
     * Gets metadata of blocks in a chunk.
     * @param chunk The chunk in which the metadata are.
     * @return Map of metadata.
     * @throws ChunkBusyException Thrown if the chunk is busy.
     * @throws ChunkNotLoadedException Thrown if the chunk is not loaded.
     */
    public Map<String, T> getMetadataInChunk(Chunk chunk)
            throws ChunkBusyException, ChunkNotLoadedException {
        ensureChunkReady(chunk);
        return metadata.get(chunk);
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
    private CompletableFuture<Void> scheduleRegionTask(Chunk chunk, java.util.function.Function
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
//            System.out.println("New session - " + regionFile.toString());
            newTask = task.apply(null);
        }

        // append clean up task after the task is done
        CompletableFuture<Boolean> cleanupTask = newTask.thenApplyAsync((t) -> {
            // delay
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                return false;
            }
            return true;
        });
        CompletableFuture<Void> completeTask = cleanupTask
            .exceptionally((e) -> {
                if (e instanceof CancellationException) {
                    return false;
                }
                throw new CompletionException(e);
            })
            .thenApply((lastInChain) -> {
                // check if should continue
                if (!lastInChain) {
                    return null;
                }

                // there is no task scheduled after this one for now because this one wasnt canceled
//                System.out.println("Commit session - " + regionFile.toString());
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

        return completeTask;
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
                                content, new TypeReference<Map<String, Map<String, T>>>(){});

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
            Path regionFile, Map<String, Map<String, T>> data) {
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
            Path regionFile, Map<String, Map<String, T>> data) {
//        System.out.println("Writing region data: " + regionFile);

        // remove old file to overwrite
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Files.deleteIfExists(regionFile);
            } catch (IOException ignored) {
                return false;
            }
        }).thenApply((s) -> {
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
     * Persists chunk metadata on disk.
     * @param chunk The chunk to persist.
     * @param unload Whether the chunk should be unloaded from memory.
     * @throws ChunkNotLoadedException Thrown if the chunk isn't loaded.
     */
    public CompletableFuture<Void> persistChunk(Chunk chunk, boolean unload)
            throws ChunkNotLoadedException {
        // check if chunk dirty
        if (!isChunkDirty(chunk)) {
            // not busy, loaded or dirty anymore
            if (unload) {
                loadedChunks.remove(chunk);
            }

            return CompletableFuture.completedFuture(null);
        }

        // save chunk asynchronously
        return scheduleRegionTask(chunk, (s) -> persistChunkAsync(chunk, unload));
    }

    /**
     * Persists chunk metadata on disk asynchronously.
     * @param chunk The chunk to persist.
     * @param unload Whether the chunk should be unloaded from memory.
     */
    private CompletableFuture<Void> persistChunkAsync(Chunk chunk, boolean unload) {
        // set busy
        LoadedChunkData loadedChunkData = loadedChunks.get(chunk);
        loadedChunkData.setBusy(true);

        return CompletableFuture.supplyAsync(() -> {
            // get appropriate file
            return getRegionFile(chunk);
        })
            .thenCompose(this::readRegionData)
            .exceptionally((e) ->{
                // error reading file
                // possibly doesnt exist
                e.printStackTrace();
                return null;
            })
            .thenApply((data) -> {
                // get chunk data
                Map<String, T> chunkMetadata;
                if(unload) {
                    chunkMetadata = metadata.remove(chunk);
                } else {
                    chunkMetadata = metadata.get(chunk);
                }

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
            .thenRun(() -> {
                // not busy, loaded or dirty anymore
                if (unload) {
                    loadedChunks.remove(chunk);
                } else {
                    loadedChunkData.setBusy(false);
                    loadedChunkData.setDirty(false);
                }
            })
            .exceptionally((e) -> {
                // not busy, loaded or dirty anymore
                if (unload) {
                    loadedChunks.remove(chunk);
                } else {
                    loadedChunkData.setBusy(false);
                }
                throw new CompletionException(e);
            });
    }

    /**
     * Loads chunk metadata into memory.
     * @param chunk The chunk.
     */
    public CompletableFuture<Void> loadChunk(Chunk chunk) throws ChunkAlreadyLoadedException {
        // check if chunk loaded
        if (isChunkLoaded(chunk)) {
            throw new ChunkAlreadyLoadedException();
        }

        // load chunk asynchronously
        return scheduleRegionTask(chunk, (s) -> loadChunkAsync(chunk));
    }

    /**
     * Loads chunk metadata into memory synchronously.
     * @param chunk The chunk.
     */
    private CompletableFuture<Void> loadChunkAsync(Chunk chunk) {
        // set busy
        LoadedChunkData loadedChunkData = new LoadedChunkData();
        loadedChunkData.setBusy(true);
        loadedChunks.put(chunk, loadedChunkData);

        return CompletableFuture.supplyAsync(() -> {
            // get appropriate file
            return getRegionFile(chunk);
        })
            .thenCompose(this::readRegionData)
            .exceptionally((e) -> {
                // error loading chunk data
                // not busy
                e.printStackTrace();
                return null;
            })
            .thenAccept((data) -> {
                // check if load was successful
                if (data == null) {
                    // no data for this chunk
                    loadedChunkData.setBusy(false);
                    return;
                }

                // load
                String chunkSection = getChunkKey(chunk);
                Map<String, T> chunkData = data.get(chunkSection);
                if (chunkData != null) {
                    metadata.put(chunk, chunkData);
                }

                // not busy
                loadedChunkData.setBusy(false);
            });
    }

    /**
     * Ensures that a chunk is ready. Throws exceptions in other case.
     * @param chunk The chunk.
     * @throws ChunkNotLoadedException Thrown if the chunk is not loaded.
     * @throws ChunkBusyException Thrown if the chunk is busy.
     */
    public void ensureChunkReady(Chunk chunk)
            throws ChunkNotLoadedException, ChunkBusyException {
        boolean busy = isChunkBusy(chunk);
        if (busy) {
            throw new ChunkBusyException();
        }
    }

    /**
     * Checks whether the specified chunk is busy.
     * A chunk is busy if it's being loaded/unloaded.
     * @param chunk The chunk.
     * @return Whether the chunk is busy.
     */
    public boolean isChunkBusy(Chunk chunk) throws ChunkNotLoadedException {
        LoadedChunkData loadedChunkData = loadedChunks.get(chunk);
        if (loadedChunkData == null) {
            throw new ChunkNotLoadedException();
        }
        return loadedChunkData.isBusy();
    }

    /**
     * Checks whether the specified chunk is dirty.
     * A chunk is dirty if it has been modified since is was loaded into memory.
     * @param chunk The chunk.
     * @return Whether the chunk is dirty.
     */
    public boolean isChunkDirty(Chunk chunk) throws ChunkNotLoadedException {
        LoadedChunkData loadedChunkData = loadedChunks.get(chunk);
        if (loadedChunkData == null) {
            throw new ChunkNotLoadedException();
        }
        return loadedChunkData.isDirty();
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
     * Represents a metadata task on a region.
     */
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

        public RegionTask() {
            this.buffered = false;
        }

        public CompletableFuture<Void> getTask() {
            return task;
        }

        public void setTask(CompletableFuture<Void> task) {
            this.task = task;
        }

        public Map<String, Map<String, T>> getBuffer() {
            return buffer;
        }

        public void setBuffer(Map<String, Map<String, T>> buffer) {
            this.buffer = buffer;
        }

        public boolean isDirty() {
            return dirty;
        }

        public void setDirty(boolean dirty) {
            this.dirty = dirty;
        }

        public boolean isBuffered() {
            return buffered;
        }

        public void setBuffered(boolean buffered) {
            this.buffered = buffered;
        }

        public CompletableFuture<Boolean> getCleanupTask() {
            return cleanupTask;
        }

        public void setCleanupTask(CompletableFuture<Boolean> cleanupTask) {
            this.cleanupTask = cleanupTask;
        }
    }

    /**
     * Information about a loaded chunk
     */
    private static class LoadedChunkData {
        private boolean dirty;
        private boolean busy;

        public boolean isDirty() {
            return dirty;
        }

        public void setDirty(boolean dirty) {
            this.dirty = dirty;
        }

        public boolean isBusy() {
            return busy;
        }

        public void setBusy(boolean busy) {
            this.busy = busy;
        }
    }
}
