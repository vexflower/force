package loader;

import hardware.VulkanContext;
import model.Mesh;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import util.FloatList;
import util.IntList;
import util.VK;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.vulkan.VK10.*;

public class GeomRegistry {

    // The raw CPU data pools
    private static final FloatList megaVertexBuffer = new FloatList(1_000_000);
    private static final IntList megaIndexBuffer = new IntList(1_000_000);

    // Trackers for where the next mesh should be inserted
    private static int currentVertexOffset = 0;
    private static int currentIndexOffset = 0;

    // The ONLY two geometry buffers the GPU will ever care about
    public static long gpuVertexBuffer;
    public static long gpuVertexMemory;
    public static long gpuIndexBuffer;
    public static long gpuIndexMemory;

    /**
     * Appends a mesh into the mega-buffers and assigns its offsets.
     * Does NOT touch Vulkan. Strictly CPU-side packing.
     */
    public static void appendMesh(Mesh mesh) {
        int vertexCount = mesh.positions.length / 3;

        // 1. Pack the Vertex Data strictly to 8 Floats (32 Bytes) to satisfy std430
        for (int i = 0; i < vertexCount; i++) {
            // Floats 0-3: Position X, Y, Z, and UV X
            megaVertexBuffer.add(mesh.positions[i * 3]);
            megaVertexBuffer.add(mesh.positions[i * 3 + 1]);
            megaVertexBuffer.add(mesh.positions[i * 3 + 2]);
            megaVertexBuffer.add(mesh.textures[i * 2]);

            // Floats 4-7: UV Y, and Normal X, Y, Z
            megaVertexBuffer.add(mesh.textures[i * 2 + 1]);
            megaVertexBuffer.add(mesh.normals[i * 3]);
            megaVertexBuffer.add(mesh.normals[i * 3 + 1]);
            megaVertexBuffer.add(mesh.normals[i * 3 + 2]);
        }

        // 2. Append Indices
        for (int i = 0; i < mesh.indices.length; i++) {
            megaIndexBuffer.add(mesh.indices[i]);
        }

        // 3. Record the offsets in the Mesh object so the Entity SSBO can find them!
        mesh.vertexOffset = currentVertexOffset;
        mesh.firstIndex = currentIndexOffset;
        mesh.indexCount = mesh.indices.length;

        // 4. Advance the global pointers
        currentVertexOffset += vertexCount;
        currentIndexOffset += mesh.indices.length;
    }

    /**
     * Call this exactly ONCE during MasterRenderer.setRenderer(),
     * after all meshes are loaded.
     */
    public static void uploadToGPU() {
        System.out.println("Uploading Bindless Geometry: " + currentVertexOffset + " Vertices, " + currentIndexOffset + " Indices.");

        long[] vData = createBufferFromFloatList(megaVertexBuffer, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT); // Note: STORAGE buffer, not Vertex!
        gpuVertexBuffer = vData[0];
        gpuVertexMemory = vData[1];

        long[] iData = createBufferFromIntList(megaIndexBuffer, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
        gpuIndexBuffer = iData[0];
        gpuIndexMemory = iData[1];

        // Free the CPU memory since the GPU has it now
        megaVertexBuffer.clear();
        megaIndexBuffer.clear();
    }

    private static long[] createBufferFromFloatList(FloatList list, int usageFlag) {
        long bufferSize = (long) list.size() * 4;
        VkDevice device = VulkanContext.getDevice();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            long stagingBuffer = VK.createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
            long stagingMemory = VK.allocateBufferMemory(stagingBuffer, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

            PointerBuffer pData = stack.mallocPointer(1);
            vkMapMemory(device, stagingMemory, 0, bufferSize, 0, pData);

            // Fast zero-allocation bulk copy
            FloatBuffer floatBuffer = MemoryUtil.memFloatBuffer(pData.get(0), list.size());
            floatBuffer.put(list.toArray());

            vkUnmapMemory(device, stagingMemory);

            long gpuBuffer = VK.createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_DST_BIT | usageFlag);
            long gpuMemory = VK.allocateBufferMemory(gpuBuffer, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

            copyBuffer(stagingBuffer, gpuBuffer, bufferSize);

            vkDestroyBuffer(device, stagingBuffer, null);
            vkFreeMemory(device, stagingMemory, null);

            return new long[]{gpuBuffer, gpuMemory};
        }
    }

    private static long[] createBufferFromIntList(IntList list, int usageFlag) {
        long bufferSize = (long) list.size() * 4;
        VkDevice device = VulkanContext.getDevice();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            long stagingBuffer = VK.createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
            long stagingMemory = VK.allocateBufferMemory(stagingBuffer, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

            PointerBuffer pData = stack.mallocPointer(1);
            vkMapMemory(device, stagingMemory, 0, bufferSize, 0, pData);

            IntBuffer intBuffer = MemoryUtil.memIntBuffer(pData.get(0), list.size());
            intBuffer.put(list.toArray());

            vkUnmapMemory(device, stagingMemory);

            long gpuBuffer = VK.createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_DST_BIT | usageFlag);
            long gpuMemory = VK.allocateBufferMemory(gpuBuffer, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

            copyBuffer(stagingBuffer, gpuBuffer, bufferSize);

            vkDestroyBuffer(device, stagingBuffer, null);
            vkFreeMemory(device, stagingMemory, null);

            return new long[]{gpuBuffer, gpuMemory};
        }
    }

    private static void copyBuffer(long srcBuffer, long dstBuffer, long size) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBuffer commandBuffer = VK.beginSingleTimeCommands();
            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack);
            copyRegion.srcOffset(0).dstOffset(0).size(size);
            vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, copyRegion);
            VK.endSingleTimeCommands(commandBuffer);
        }
    }

    public static void destroy() {
        VkDevice device = VulkanContext.getDevice();
        if (gpuVertexBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, gpuVertexBuffer, null);
            vkFreeMemory(device, gpuVertexMemory, null);
            vkDestroyBuffer(device, gpuIndexBuffer, null);
            vkFreeMemory(device, gpuIndexMemory, null);
        }
    }
}