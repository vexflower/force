package loader;

import hardware.Display;
import model.Mesh;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import util.VulkanUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

// [CHANGED: 1] Removed ALL org.lwjgl.opengl.* imports. Replaced with Vulkan 1.0
import static org.lwjgl.vulkan.VK10.*;

/**
 * High-performance Vulkan MeshLoader.
 * Replaces OpenGL VAOs/VBOs with Vulkan VkBuffers and Staging memory.
 */
public final class MeshLoader {

    // [CHANGED: 2] Vulkan handles are 64-bit pointers, so we MUST use long[] instead of int[].
    // We separate Vertices and Indices because Vulkan binds them differently.
    private static long[] vertexBuffers = new long[128];
    private static long[] vertexMemories = new long[128];
    private static long[] indexBuffers = new long[128];
    private static long[] indexMemories = new long[128];

    // [CHANGED: 3] Vulkan needs to know exactly how many indices to draw during the render loop
    public static int[] indexCounts = new int[128];
    private static long[] uvBuffers = new long[128];
    private static long[] uvMemories = new long[128];

    private static int meshCount = 0;

    /**
     * [CHANGED: 4] Instead of returning an int right away, we pass the Mesh object in,
     * upload its data, and assign its vaoId directly.
     * We require the 'commandPool' to perform the Staging Buffer copies.
     */
    public static void loadMesh(Mesh mesh, long commandPool) {
        if (meshCount == vertexBuffers.length) expandArrays();

        int id = meshCount;

        // 1. Upload Positions (Vertices) to the GPU
        long[] vData = createBufferFromFloatArray(mesh.positions, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, commandPool);
        vertexBuffers[id] = vData[0];
        vertexMemories[id] = vData[1];

        // 2. Upload Indices to the GPU
        long[] iData = createBufferFromIntArray(mesh.indices, VK_BUFFER_USAGE_INDEX_BUFFER_BIT, commandPool);
        indexBuffers[id] = iData[0];
        indexMemories[id] = iData[1];

        long[] uvData = createBufferFromFloatArray(mesh.textures, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, commandPool);
        uvBuffers[id] = uvData[0];
        uvMemories[id] = uvData[1];

        indexCounts[id] = mesh.indices.length;

        // [CHANGED: 5] Assign the registry ID back to the Mesh so we can look it up later!
        mesh.vaoId = id;
        meshCount++;
    }

    /**
     * [CHANGED: 6] The Vulkan equivalent of OpenGL's glBufferData for Floats.
     * Returns a long array: [0] = the VkBuffer handle, [1] = the VkDeviceMemory handle
     */
    private static long[] createBufferFromFloatArray(float[] data, int usageFlag, long commandPool) {
        long bufferSize = (long) data.length * 4; // 4 bytes per float
        VkDevice device = Display.getDevice();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // STEP A: Create the CPU-Visible Staging Buffer
            long stagingBuffer = VulkanUtils.createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
            long stagingMemory = VulkanUtils.allocateBufferMemory(stagingBuffer, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

            // STEP B: Map the memory and copy the Java float[] into the C-Memory
            PointerBuffer pData = stack.mallocPointer(1);
            vkMapMemory(device, stagingMemory, 0, bufferSize, 0, pData);
            FloatBuffer floatBuffer = MemoryUtil.memFloatBuffer(pData.get(0), data.length);
            floatBuffer.put(data);
            vkUnmapMemory(device, stagingMemory);

            // STEP C: Create the ultra-fast GPU-Only Buffer
            long gpuBuffer = VulkanUtils.createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_DST_BIT | usageFlag);
            long gpuMemory = VulkanUtils.allocateBufferMemory(gpuBuffer, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

            // STEP D: Instruct the GPU to copy from Staging to Device_Local
            copyBuffer(stagingBuffer, gpuBuffer, bufferSize, commandPool);

            // STEP E: Destroy the temporary Staging Buffer (Zero memory leaks!)
            vkDestroyBuffer(device, stagingBuffer, null);
            vkFreeMemory(device, stagingMemory, null);

            return new long[]{gpuBuffer, gpuMemory};
        }
    }

    /**
     * [CHANGED: 7] The Vulkan equivalent of glBufferData for Integers (Indices).
     * Exact same logic as Floats, but we use an IntBuffer for mapping.
     */
    private static long[] createBufferFromIntArray(int[] data, int usageFlag, long commandPool) {
        long bufferSize = (long) data.length * 4; // 4 bytes per int
        VkDevice device = Display.getDevice();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            long stagingBuffer = VulkanUtils.createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
            long stagingMemory = VulkanUtils.allocateBufferMemory(stagingBuffer, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

            PointerBuffer pData = stack.mallocPointer(1);
            vkMapMemory(device, stagingMemory, 0, bufferSize, 0, pData);
            IntBuffer intBuffer = MemoryUtil.memIntBuffer(pData.get(0), data.length);
            intBuffer.put(data);
            vkUnmapMemory(device, stagingMemory);

            long gpuBuffer = VulkanUtils.createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_DST_BIT | usageFlag);
            long gpuMemory = VulkanUtils.allocateBufferMemory(gpuBuffer, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

            copyBuffer(stagingBuffer, gpuBuffer, bufferSize, commandPool);

            vkDestroyBuffer(device, stagingBuffer, null);
            vkFreeMemory(device, stagingMemory, null);

            return new long[]{gpuBuffer, gpuMemory};
        }
    }

    /**
     * [NEW: 8] Helper method to record and submit a memory copy command to the GPU
     */
    private static void copyBuffer(long srcBuffer, long dstBuffer, long size, long commandPool) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBuffer commandBuffer = VulkanUtils.beginSingleTimeCommands(commandPool);

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack);
            copyRegion.srcOffset(0);
            copyRegion.dstOffset(0);
            copyRegion.size(size);

            vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, copyRegion);

            VulkanUtils.endSingleTimeCommands(commandBuffer, commandPool);
        }
    }

    // [CHANGED: 9] We use glDelete* in OpenGL, but in Vulkan we use vkDestroyBuffer and vkFreeMemory
    public static void destroy() {
        VkDevice device = Display.getDevice();
        for (int i = 0; i < meshCount; i++) {
            vkDestroyBuffer(device, vertexBuffers[i], null);
            vkFreeMemory(device, vertexMemories[i], null);
            vkDestroyBuffer(device, indexBuffers[i], null);
            vkFreeMemory(device, indexMemories[i], null);
        }
        meshCount = 0;
    }

    // [CHANGED: 10] Updated to expand the new long arrays
    private static void expandArrays() {
        int newSize = vertexBuffers.length * 2;

        long[] newVB = new long[newSize];
        System.arraycopy(vertexBuffers, 0, newVB, 0, meshCount);
        vertexBuffers = newVB;

        long[] newVM = new long[newSize];
        System.arraycopy(vertexMemories, 0, newVM, 0, meshCount);
        vertexMemories = newVM;

        long[] newIB = new long[newSize];
        System.arraycopy(indexBuffers, 0, newIB, 0, meshCount);
        indexBuffers = newIB;

        long[] newIM = new long[newSize];
        System.arraycopy(indexMemories, 0, newIM, 0, meshCount);
        indexMemories = newIM;

        int[] newIC = new int[newSize];
        System.arraycopy(indexCounts, 0, newIC, 0, meshCount);
        indexCounts = newIC;
    }

    // --- GETTERS FOR RENDERER ---
    public static long getVertexBuffer(int id) { return vertexBuffers[id]; }
    public static long getIndexBuffer(int id) { return indexBuffers[id]; }
    public static int getIndexCount(int id) { return indexCounts[id]; }
    public static long getUvBuffer(int id) { return uvBuffers[id]; }
}