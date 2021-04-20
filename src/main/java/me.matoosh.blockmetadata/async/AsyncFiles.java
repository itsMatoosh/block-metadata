/*
 * MIT License
 *
 * Copyright (c) 2018, Miguel Gamboa (gamboa.pt)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package me.matoosh.blockmetadata.async;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;

import static java.nio.channels.AsynchronousFileChannel.open;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Asynchronous non-blocking read and write operations with a reactive based API.
 * Read operations return a CompletableFuture with a single String or a Publisher
 * of strings corresponding to lines.
 * Write methods return a CompletableFuture with the final file index after the
 * completion of corresponding write operation.
 * These operations use an underlying AsynchronousFileChannel.
 */

public class AsyncFiles {
    /**
     * Reads the file from the beginning using
     * an AsyncFileChannel with a ByteBuffer of
     * the specified bufferSize capacity.
     * It automatically closes the underlying AsyncFileChannel
     * when read is complete.
     */
    public static CompletableFuture<String> readAll(Path file, int bufferSize) {
        return readAllBytes(file, bufferSize)
                .thenApply(bytes -> new String(bytes, UTF_8));
    }

    /**
     * Reads all bytes from the beginning of the file
     * using an AsyncFileChannel with a ByteBuffer of
     * the specified bufferSize capacity.
     */
    public static CompletableFuture<byte[]> readAllBytes(
            Path file,
            int bufferSize,
            StandardOpenOption...options)
    {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            AsynchronousFileChannel asyncFile = open(file, options);
            CompletableFuture<byte[]> bytes = AsyncFileReaderBytes
                .readAllBytes(asyncFile, buffer, 0, out)
                .thenApply(position -> out.toByteArray());
            /**
             * Deliberately chained in this way.
             * Code smell: If closeAfc throws an Exception it will be lost!
             */
            bytes.whenCompleteAsync((pos, ex) -> closeAfc(asyncFile));
            return bytes;
        } catch (IOException e) {
            CompletableFuture<byte[]> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    /**
     * Writes bytes to a file.
     * The options parameter specifies how the file is created or opened.
     * All bytes in the byte array are written to the file.
     * The method ensures that the underlying {@code AsynchronousFileChannel}
     * is closed when all bytes have been written (or an I/O error or any other
     * runtime exception is thrown).
     * Returns a {@code CompletableFuture} with the final file index
     * after the completion of the corresponding write operation.
     * If an I/O error occurs then it may complete the resulting CompletableFuture
     * exceptionally.
     */
    public static CompletableFuture<Integer> writeBytes(
            Path path,
            byte[] bytes,
            StandardOpenOption... options)
    {
        try (AsyncFileWriter writer = new AsyncFileWriter(path, options)) {
            writer.write(bytes);
            // The call to writer.close() is asynchronous and will chain
            // a continuation to close the AsyncFileChannel only after completion.
            return writer.getPosition();
        } catch (IOException e) {
            CompletableFuture<Integer> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    static void closeAfc(AsynchronousFileChannel asyncFile) {
        try {
            asyncFile.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
