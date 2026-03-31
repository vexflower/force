package resources;

import org.lwjgl.system.MemoryUtil;
import java.io.InputStream;
import java.nio.ByteBuffer;
import static org.lwjgl.stb.STBImage.*;
import static renderer.MasterRenderer.getCommandPool;

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
    public static ByteBuffer streamToOffHeap(String path) {
        InputStream is = Resources.class.getResourceAsStream(path);

        try {
            // IDE Fallback: If IntelliJ is acting up and didn't copy it to the out folder
            if (is == null) {
                java.io.File localFile = new java.io.File("src/resources/" + path);
                if (localFile.exists()) {
                    is = new java.io.FileInputStream(localFile);
                } else {
                    throw new RuntimeException("File not found in Classpath OR Local System: src/resources/" + path);
                }
            }

            try (InputStream stream = is) {
                int bufferSize = 32768;
                ByteBuffer nativeBuffer = MemoryUtil.memAlloc(bufferSize);
                int totalBytesRead = 0;

                int bytesRead;
                while ((bytesRead = stream.read(TRANSFER_BUFFER)) != -1) {
                    if (totalBytesRead + bytesRead > nativeBuffer.capacity()) {
                        bufferSize *= 2;
                        nativeBuffer = MemoryUtil.memRealloc(nativeBuffer, bufferSize);
                    }
                    nativeBuffer.put(totalBytesRead, TRANSFER_BUFFER, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }

                nativeBuffer.position(0);
                nativeBuffer.limit(totalBytesRead);
                return nativeBuffer;
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to stream resource to off-heap: " + path, e);
        }
    }

    /**
     * Loads an image from the Fat-Jar and decodes it via STB completely off-heap.
     * @return The Bindless Texture Array ID (Integer Handle)
     */
    // [CHANGED: Added commandPool parameter and wired it to TextureLoader]
    public static int loadTexture(String path) {

        // make sure that the master renderer has set before calling this

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

        // 4. Send it to the GPU via Staging Buffer!
        int textureId = loader.TextureLoader.uploadToGPU(decodedImage, width[0], height[0], getCommandPool());

        // 5. Free the STB decoded pixels from RAM now that the GPU has them
        stbi_image_free(decodedImage);

        return textureId; // The Entity simply stores this integer!
    }
}