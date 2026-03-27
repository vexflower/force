package resources;

import org.lwjgl.system.MemoryUtil;
import java.io.InputStream;
import java.nio.ByteBuffer;
import static org.lwjgl.stb.STBImage.*;

/**
 * AAA Fat-Jar Resource Loader & Bindless Texture Manager.
 * Streams files from inside a JAR directly into Off-Heap C-Memory.
 */
public class Resources {

    // The single, permanent heap allocation for streaming file bytes.
    private static final byte[] TRANSFER_BUFFER = new byte[8192];

    // We will store our loaded texture Vulkan Image handles here later
    public static final long[] BINDLESS_TEXTURE_ARRAY = new long[4096];
    public static int textureCount = 0;

    /**
     * Reads a file from the Fat-Jar directly into Off-Heap Memory.
     * @param path The path inside the resources folder (e.g., "textures/grass.png")
     * @return A direct ByteBuffer containing the raw file bytes. MUST BE FREED manually.
     */
    private static ByteBuffer streamToOffHeap(String path) {
        try (InputStream is = Resources.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("File not found in Fat-Jar: " + path);
            }

            // Start with a 32KB native buffer
            int bufferSize = 32768;
            ByteBuffer nativeBuffer = MemoryUtil.memAlloc(bufferSize);
            int totalBytesRead = 0;

            int bytesRead;
            while ((bytesRead = is.read(TRANSFER_BUFFER)) != -1) {
                // If the native buffer is full, reallocate it to double the size
                if (totalBytesRead + bytesRead > nativeBuffer.capacity()) {
                    bufferSize *= 2;
                    nativeBuffer = MemoryUtil.memRealloc(nativeBuffer, bufferSize);
                }

                // Blast the bytes from the 8KB heap buffer into the native C-memory
                nativeBuffer.put(totalBytesRead, TRANSFER_BUFFER, 0, bytesRead);
                totalBytesRead += bytesRead;
            }

            // Set the final limit so STB knows exactly where the file ends
            nativeBuffer.position(0);
            nativeBuffer.limit(totalBytesRead);
            return nativeBuffer;

        } catch (Exception e) {
            throw new RuntimeException("Failed to stream resource to off-heap: " + path, e);
        }
    }

    /**
     * Loads an image from the Fat-Jar and decodes it via STB completely off-heap.
     * @return The Bindless Texture Array ID (Integer Handle)
     */
    public static int loadTexture(String path) {
        System.out.println("Streaming Texture to Off-Heap: " + path);

        // 1. Stream the raw ZIP/JAR bytes into C-Memory
        ByteBuffer rawFileBuffer = streamToOffHeap(path);

        int[] width = new int[1];
        int[] height = new int[1];
        int[] channels = new int[1];

        stbi_set_flip_vertically_on_load(true);

        // 2. STB decodes the image straight from the native memory we just filled
        ByteBuffer decodedImage = stbi_load_from_memory(rawFileBuffer, width, height, channels, 4);

        if (decodedImage == null) {
            MemoryUtil.memFree(rawFileBuffer);
            throw new RuntimeException("STB failed to decode image: " + stbi_failure_reason());
        }

        // 3. Free the raw compressed file bytes immediately. We only need the decoded pixels now.
        MemoryUtil.memFree(rawFileBuffer);

        // TODO: In Step 2, we will upload `decodedImage` to the Vulkan Bindless Array here.
        // For now, we simulate adding it to the registry.

        int textureId = textureCount++;
        // BINDLESS_TEXTURE_ARRAY[textureId] = vulkanImageHandle;

        // 4. Free the STB decoded pixels once uploaded to the GPU
        stbi_image_free(decodedImage);

        return textureId; // The Entity simply stores this integer!
    }
}