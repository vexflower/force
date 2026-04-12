package loader;

import hardware.VulkanContext;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import util.CFloatList;
import util.CIntList;
import util.VK;

import static org.lwjgl.vulkan.VK10.*;

public class GeomRegistry {

        // The raw C-Memory data pools
        private static final CFloatList megaVertexBuffer = new CFloatList(1_000_000);
        private static final CIntList megaIndexBuffer = new CIntList(1_000_000);

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
    public static void appendMesh(mesh.Mesh mesh) {
        for (int l = 0; l < mesh.lodCount; l++) {
            for (int i = 0; i < mesh.lodVertexCounts[l] * 8; i++) {
                megaVertexBuffer.add(MemoryUtil.memGetFloat(mesh.lodVertexPtrs[l] + (i * 4L)));
            }
            for (int i = 0; i < mesh.lodIndexCounts[l]; i++) {
                megaIndexBuffer.add(MemoryUtil.memGetInt(mesh.lodIndexPtrs[l] + (i * 4L)));
            }
            mesh.lodVertexOffsets[l] = currentVertexOffset;
            mesh.lodFirstIndices[l] = currentIndexOffset;
            currentVertexOffset += mesh.lodVertexCounts[l];
            currentIndexOffset += mesh.lodIndexCounts[l];
        }
    }

    /**
     * Call this exactly ONCE during MasterRenderer.setRenderer(),
     * after all meshes are loaded.
     */
    public static void uploadToGPU() {
        System.out.println("Uploading Bindless Geometry: " + currentVertexOffset + " Vertices, " + currentIndexOffset + " Indices.");
        if (megaVertexBuffer.isEmpty() || megaIndexBuffer.isEmpty()) {
            for (int i = 0; i < 8; i++) megaVertexBuffer.add(0f);
            megaIndexBuffer.add(0);
        }

        long[] vData = createBufferFromFloatList(megaVertexBuffer, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        gpuVertexBuffer = vData[0];
        gpuVertexMemory = vData[1];

        long[] iData = createBufferFromIntList(megaIndexBuffer, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
        gpuIndexBuffer = iData[0];
        gpuIndexMemory = iData[1];

        // Free the CPU memory since the GPU has it now
        megaVertexBuffer.clear();
        megaIndexBuffer.clear();
    }

    private static long[] createBufferFromFloatList(CFloatList list, int usageFlag) {
        long bufferSize = (long) list.size() * 4L;
        VkDevice device = VulkanContext.getDevice();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            long stagingBuffer = VK.createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
            long stagingMemory = VK.allocateBufferMemory(stagingBuffer, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

            PointerBuffer pData = stack.mallocPointer(1);
            vkMapMemory(device, stagingMemory, 0, bufferSize, 0, pData);

            // ---> THE ULTIMATE ZERO-GC MEMORY TRANSFER <---
            MemoryUtil.memCopy(list.address(), pData.get(0), bufferSize);

            long gpuBuffer = VK.createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_DST_BIT | usageFlag);
            long gpuMemory = VK.allocateBufferMemory(gpuBuffer, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

            copyBuffer(stagingBuffer, gpuBuffer, bufferSize);

            vkDestroyBuffer(device, stagingBuffer, null);
            vkFreeMemory(device, stagingMemory, null);

            return new long[]{gpuBuffer, gpuMemory};
        }
    }

    private static long[] createBufferFromIntList(CIntList list, int usageFlag) {
        long bufferSize = (long) list.size() * 4L;
        VkDevice device = VulkanContext.getDevice();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            long stagingBuffer = VK.createBuffer(bufferSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
            long stagingMemory = VK.allocateBufferMemory(stagingBuffer, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

            PointerBuffer pData = stack.mallocPointer(1);
            vkMapMemory(device, stagingMemory, 0, bufferSize, 0, pData);

            // ---> THE ULTIMATE ZERO-GC MEMORY TRANSFER <---
            MemoryUtil.memCopy(list.address(), pData.get(0), bufferSize);

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