package loader;

import hardware.VulkanContext;
import mesh.Mesh;
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

    // --- 7 SEPARATE SOA VAULTS ---
    private static final CFloatList mPos = new CFloatList(1_000_000);
    private static final CFloatList mUv = new CFloatList(1_000_000);
    private static final CFloatList mNorm = new CFloatList(1_000_000);
    private static final CFloatList mTang = new CFloatList(1_000_000);
    public static CFloatList vaultCones = new CFloatList(1_000_000); // Initialize this wherever vaultBounds is initialized!

    private static final CIntList mInd = new CIntList(1_000_000);
    private static final CIntList mMeshData = new CIntList(50_000);
    private static final CFloatList mMeshBounds = new CFloatList(50_000);

    private static int vOff = 0, iOff = 0, mOff = 0;

    public static long gpuPos, gpuUv, gpuNorm, gpuTang, gpuInd, gpuMData, gpuMBounds;
    public static long gpuMCones; 

    public static long memPos, memUv, memNorm, memTang, memInd, memMData, memMBounds;
    public static long memMCones;

    // ---> THE FIX: The UI Fast-Path <---
    public static void appendPrimitive(mesh.Mesh mesh, float[] pos, float[] uvs, int[] inds) {
        // 1. Add Positions and UVs
        for (float p : pos) mPos.add(p);
        for (float u : uvs) mUv.add(u);

        // 2. Pad Normals and Tangents with zeros so the VRAM indexing stays perfectly aligned!
        for (int i = 0; i < pos.length; i++) {
            mNorm.add(0f);
            mTang.add(0f);
        }

        // 3. Add Indices
        for (int i : inds) mInd.add(i);

        // 4. Save the global offsets to the Mesh object
        mesh.globalVertexOffset = vOff;
        mesh.globalIndexOffset = iOff;
        mesh.indexCount = inds.length;

        // Explicitly declare this has NO meshlets (Bypasses the Compute Shader)
        mesh.meshletCount = 0;
        mesh.globalMeshletOffset = -1;

        // 5. Advance the global trackers
        vOff += pos.length / 3;
        iOff += inds.length;
    }

    public static void uploadToGPU() {
        System.out.println("Uploading SoA Vault: " + vOff + " Verts, " + mOff + " Meshlets.");
        if (mPos.isEmpty()) { mPos.add(0f); mUv.add(0f); mNorm.add(0f); mTang.add(0f); mInd.add(0); mMeshData.add(0); mMeshBounds.add(0f); }

        long[] d;
        d = createBufF(mPos, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT); gpuPos = d[0]; memPos = d[1];
        d = createBufF(mUv, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);  gpuUv = d[0]; memUv = d[1];
        d = createBufF(mNorm, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);gpuNorm = d[0]; memNorm = d[1];
        d = createBufF(mTang, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);gpuTang = d[0]; memTang = d[1];

        d = createBufI(mInd, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);   gpuInd = d[0]; memInd = d[1];
        d = createBufI(mMeshData, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT); gpuMData = d[0]; memMData = d[1];
        d = createBufF(mMeshBounds, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT); gpuMBounds = d[0]; memMBounds = d[1];

        d = createBufF(vaultCones, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT); gpuMCones = d[0]; memMCones = d[1];
        vaultCones.free();

        mPos.free(); mUv.free(); mNorm.free(); mTang.free(); mInd.free(); mMeshData.free(); mMeshBounds.free();
    }

    private static long[] createBufF(CFloatList list, int usage) {
        long size = (long) list.size() * 4L;
        VkDevice device = VulkanContext.getDevice();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long sBuf = VK.createBuffer(size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
            long sMem = VK.allocateBufferMemory(sBuf, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            PointerBuffer pData = stack.mallocPointer(1);
            vkMapMemory(device, sMem, 0, size, 0, pData);
            MemoryUtil.memCopy(list.address(), pData.get(0), size);
            vkUnmapMemory(device, sMem);
            long gBuf = VK.createBuffer(size, VK_BUFFER_USAGE_TRANSFER_DST_BIT | usage);
            long gMem = VK.allocateBufferMemory(gBuf, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            copyBuffer(sBuf, gBuf, size);
            vkDestroyBuffer(device, sBuf, null); vkFreeMemory(device, sMem, null);
            return new long[]{gBuf, gMem};
        }
    }

    private static long[] createBufI(CIntList list, int usage) {
        long size = (long) list.size() * 4L;
        VkDevice device = VulkanContext.getDevice();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long sBuf = VK.createBuffer(size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
            long sMem = VK.allocateBufferMemory(sBuf, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            PointerBuffer pData = stack.mallocPointer(1);
            vkMapMemory(device, sMem, 0, size, 0, pData);
            MemoryUtil.memCopy(list.address(), pData.get(0), size);
            vkUnmapMemory(device, sMem);
            long gBuf = VK.createBuffer(size, VK_BUFFER_USAGE_TRANSFER_DST_BIT | usage);
            long gMem = VK.allocateBufferMemory(gBuf, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            copyBuffer(sBuf, gBuf, size);
            vkDestroyBuffer(device, sBuf, null); vkFreeMemory(device, sMem, null);
            return new long[]{gBuf, gMem};
        }
    }

    private static void copyBuffer(long src, long dst, long size) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBuffer cmd = VK.beginSingleTimeCommands();
            VkBufferCopy.Buffer cp = VkBufferCopy.calloc(1, stack).srcOffset(0).dstOffset(0).size(size);
            vkCmdCopyBuffer(cmd, src, dst, cp);
            VK.endSingleTimeCommands(cmd);
        }
    }

    public static void destroy() {
        VkDevice dev = VulkanContext.getDevice();
        if (gpuPos != VK_NULL_HANDLE) {
            vkDestroyBuffer(dev, gpuPos, null); vkFreeMemory(dev, memPos, null);
            vkDestroyBuffer(dev, gpuUv, null); vkFreeMemory(dev, memUv, null);
            vkDestroyBuffer(dev, gpuNorm, null); vkFreeMemory(dev, memNorm, null);
            vkDestroyBuffer(dev, gpuTang, null); vkFreeMemory(dev, memTang, null);
            vkDestroyBuffer(dev, gpuInd, null); vkFreeMemory(dev, memInd, null);
            vkDestroyBuffer(dev, gpuMData, null); vkFreeMemory(dev, memMData, null);
            vkDestroyBuffer(dev, gpuMBounds, null); vkFreeMemory(dev, memMBounds, null);
            vkDestroyBuffer(dev, gpuMCones, null); vkFreeMemory(dev, memMCones, null);
        }
    }

    public static void appendMeshlet(Mesh mesh, float[] pos, float[] uvs, float[] norms, float[] tangs, int[] inds, int[] mData, float[] mBounds, float[] mCone)
    {
        for (float p : pos) mPos.add(p);
        for (float u : uvs) mUv.add(u);
        for (float n : norms) mNorm.add(n);
        for (float t : tangs) mTang.add(t);
        for (int i : inds) mInd.add(i);

        mesh.globalMeshletOffset = mOff;

        // Fix the offsets globally
        for (int i = 0; i < mData.length / 4; i++) {
            mMeshData.add(mData[i*4] + iOff); // Global Index Offset
            mMeshData.add(mData[i*4+1]);      // Index Count
            mMeshData.add(vOff);              // Global Vertex Offset
            mMeshData.add(0);                 // Padding

            mMeshBounds.add(mBounds[i*4]);
            mMeshBounds.add(mBounds[i*4+1]);
            mMeshBounds.add(mBounds[i*4+2]);
            mMeshBounds.add(mBounds[i*4+3]);

            // ---> THE FIX: Moved inside the loop and indexed by i*4 ! <---
            vaultCones.add(mCone[i*4], mCone[i*4+1], mCone[i*4+2], mCone[i*4+3]);
        }

        vOff += pos.length / 3;
        iOff += inds.length;
        mOff += mData.length / 4;
    }
}