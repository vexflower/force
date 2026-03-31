package loader;

import hardware.Display;
import model.Mesh;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import util.FloatList;
import util.IntList;
import util.VulkanUtils;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

// [CHANGED: 1] Removed ALL org.lwjgl.opengl.* imports. Replaced with Vulkan 1.0
import static org.lwjgl.assimp.Assimp.aiGetErrorString;
import static org.lwjgl.assimp.Assimp.aiImportFileFromMemory;
import static org.lwjgl.assimp.Assimp.aiProcess_CalcTangentSpace;
import static org.lwjgl.assimp.Assimp.aiProcess_GenSmoothNormals;
import static org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices;
import static org.lwjgl.assimp.Assimp.aiProcess_Triangulate;
import static org.lwjgl.assimp.Assimp.aiReleaseImport;
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
    public static void loadMesh(Mesh mesh) {
        if (meshCount == vertexBuffers.length) expandArrays();

        int id = meshCount;

        // 1. Upload Positions (Vertices) to the GPU
        long[] vData = createBufferFromFloatArray(mesh.positions, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
        vertexBuffers[id] = vData[0];
        vertexMemories[id] = vData[1];

        // 2. Upload Indices to the GPU
        long[] iData = createBufferFromIntArray(mesh.indices, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
        indexBuffers[id] = iData[0];
        indexMemories[id] = iData[1];

        long[] uvData = createBufferFromFloatArray(mesh.textures, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
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
    private static long[] createBufferFromFloatArray(float[] data, int usageFlag) {
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
            copyBuffer(stagingBuffer, gpuBuffer, bufferSize);

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
    private static long[] createBufferFromIntArray(int[] data, int usageFlag) {
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

            copyBuffer(stagingBuffer, gpuBuffer, bufferSize);

            vkDestroyBuffer(device, stagingBuffer, null);
            vkFreeMemory(device, stagingMemory, null);

            return new long[]{gpuBuffer, gpuMemory};
        }
    }

    public static Mesh parseObjFromMemory(byte[] data, String meshName) {
        util.FloatList rawPos = new util.FloatList();
        util.FloatList rawTex = new util.FloatList();
        util.FloatList rawNorm = new util.FloatList();

        util.FloatList outPos = new util.FloatList();
        util.FloatList outTex = new util.FloatList();
        util.FloatList outNorm = new util.FloatList();
        util.IntList outIndices = new util.IntList();

        int[] offset = new int[]{0};

        while (offset[0] < data.length) {
            int i = offset[0];
            while (i < data.length && (data[i] == ' ' || data[i] == '\t' || data[i] == '\r' || data[i] == '\n')) i++;
            if (i >= data.length) break;

            // [THE TAB & SPACE FIX]: Safely checks for 'v\t' or 'v ' without crashing
            if (data[i] == 'v') {
                if (i + 1 < data.length && (data[i + 1] == ' ' || data[i + 1] == '\t')) {
                    offset[0] = i + 1;
                    rawPos.add(parseFloat(data, offset), parseFloat(data, offset), parseFloat(data, offset));
                } else if (i + 2 < data.length && data[i + 1] == 't' && (data[i + 2] == ' ' || data[i + 2] == '\t')) {
                    offset[0] = i + 2;
                    rawTex.add(parseFloat(data, offset), parseFloat(data, offset));
                } else if (i + 2 < data.length && data[i + 1] == 'n' && (data[i + 2] == ' ' || data[i + 2] == '\t')) {
                    offset[0] = i + 2;
                    rawNorm.add(parseFloat(data, offset), parseFloat(data, offset), parseFloat(data, offset));
                } else {
                    while (i < data.length && data[i] != '\n') i++;
                    offset[0] = i;
                }
            } else if (data[i] == 'f' && i + 1 < data.length && (data[i + 1] == ' ' || data[i + 1] == '\t')) {
                offset[0] = i + 1;
                parseFace(data, offset, rawPos, rawTex, rawNorm, outPos, outTex, outNorm, outIndices);
            } else {
                while (i < data.length && data[i] != '\n') i++;
                offset[0] = i;
            }
        }

        Mesh mesh = new Mesh(meshName);
        mesh.positions = outPos.toArray();
        mesh.textures = outTex.toArray();
        mesh.normals = outNorm.toArray();
        mesh.indices = outIndices.toArray();

        calculateTangents(mesh);
        System.out.println("Parsed OBJ from memory! Vertices: " + (mesh.positions.length / 3) + ", Indices: " + mesh.indices.length);
        return mesh;
    }
    /**
     * [NEW: 8] Helper method to record and submit a memory copy command to the GPU
     */
    private static void copyBuffer(long srcBuffer, long dstBuffer, long size) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBuffer commandBuffer = VulkanUtils.beginSingleTimeCommands();

            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack);
            copyRegion.srcOffset(0);
            copyRegion.dstOffset(0);
            copyRegion.size(size);

            vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, copyRegion);

            VulkanUtils.endSingleTimeCommands(commandBuffer);
        }
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

    public static void destroy() {
        VkDevice device = hardware.Display.getDevice();
        for (int i = 0; i < meshCount; i++) {
            vkDestroyBuffer(device, vertexBuffers[i], null);
            vkFreeMemory(device, vertexMemories[i], null);
            vkDestroyBuffer(device, indexBuffers[i], null);
            vkFreeMemory(device, indexMemories[i], null);
            // [CHANGED: Added the missing UV buffers!]
            vkDestroyBuffer(device, uvBuffers[i], null);
            vkFreeMemory(device, uvMemories[i], null);
        }
        meshCount = 0;
    }

    public static void loadAllObjects() {
        File meshDir = new File(System.getProperty("user.home") + File.separator + "framework" + File.separator + "objects");
        if (!meshDir.exists()) {
            System.out.println("Mesh directory does not exist.");
            return;
        }

        File[] files = meshDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".obj"));
        if (files == null) return;

        for (File file : files) {
            String fileName = file.getName();
            String meshName = fileName.substring(0, fileName.lastIndexOf('.'));

            try {
                Mesh mesh = parseObjFromFile(file.getAbsolutePath(), meshName);
                mesh.exportObject();
                System.out.println("Processed and exported: " + meshName);
            } catch (IOException e) {
                System.err.println("Failed to load: " + fileName);
            }
        }
    }

    public static Mesh parseObjFromFile(String filePath, String meshName) throws IOException {
        // Read the entire file instantly into RAM as raw ASCII bytes
        byte[] data = Files.readAllBytes(Paths.get(filePath));

        // Temporary data lists (The file order)
        FloatList rawPos = new FloatList();
        FloatList rawTex = new FloatList();
        FloatList rawNorm = new FloatList();

        // Final flattened arrays for OpenGL VBOs
        FloatList outPos = new FloatList();
        FloatList outTex = new FloatList();
        FloatList outNorm = new FloatList();
        IntList outIndices = new IntList();

        int[] offset = new int[]{0}; // Pointer to track our position in the byte array

        while (offset[0] < data.length) {
            int i = offset[0];

            while (i < data.length && (data[i] == ' ' || data[i] == '\t' || data[i] == '\r' || data[i] == '\n')) i++;
            if (i >= data.length) break;

            if (data[i] == 'v' && data[i + 1] == ' ') {
                offset[0] = i + 2;
                rawPos.add(parseFloat(data, offset), parseFloat(data, offset), parseFloat(data, offset));
            } else if (data[i] == 'v' && data[i + 1] == 't' && data[i + 2] == ' ') {
                offset[0] = i + 3;
                rawTex.add(parseFloat(data, offset), parseFloat(data, offset));
            } else if (data[i] == 'v' && data[i + 1] == 'n' && data[i + 2] == ' ') {
                offset[0] = i + 3;
                rawNorm.add(parseFloat(data, offset), parseFloat(data, offset), parseFloat(data, offset));
            } else if (data[i] == 'f' && data[i + 1] == ' ') {
                offset[0] = i + 2;
                parseFace(data, offset, rawPos, rawTex, rawNorm, outPos, outTex, outNorm, outIndices);
            } else {
                while (i < data.length && data[i] != '\n') i++;
                offset[0] = i;
            }
        }

        Mesh mesh = new Mesh(meshName);
        mesh.positions = outPos.toArray();
        mesh.textures = outTex.toArray();
        mesh.normals = outNorm.toArray();
        mesh.indices = outIndices.toArray();

        // Automatically calculate Tangents for the shader (Zero-Allocation mathematics)
        calculateTangents(mesh);

        System.out.println("Parsed OBJ file: " + filePath);
        System.out.println("Final Output - Vertices: " + mesh.positions.length / 3 + ", Indices: " + mesh.indices.length);

        return mesh;
    }

    /**
     * Mathematically deduces the Tangent vectors by comparing the physical edges of the triangle
     * against the UV coordinate mapping. Essential for Normal Mapping.
     */
    private static void calculateTangents(Mesh mesh) {
        float[] tangents = new float[mesh.positions.length];

        // Process one triangle (3 indices) at a time
        for (int i = 0; i < mesh.indices.length; i += 3) {
            int i0 = mesh.indices[i];
            int i1 = mesh.indices[i + 1];
            int i2 = mesh.indices[i + 2];

            // Get XYZ positions
            float v0x = mesh.positions[i0 * 3], v0y = mesh.positions[i0 * 3 + 1], v0z = mesh.positions[i0 * 3 + 2];
            float v1x = mesh.positions[i1 * 3], v1y = mesh.positions[i1 * 3 + 1], v1z = mesh.positions[i1 * 3 + 2];
            float v2x = mesh.positions[i2 * 3], v2y = mesh.positions[i2 * 3 + 1], v2z = mesh.positions[i2 * 3 + 2];

            // Get UV coordinates
            float uv0x = mesh.textures[i0 * 2], uv0y = mesh.textures[i0 * 2 + 1];
            float uv1x = mesh.textures[i1 * 2], uv1y = mesh.textures[i1 * 2 + 1];
            float uv2x = mesh.textures[i2 * 2], uv2y = mesh.textures[i2 * 2 + 1];

            // Calculate physical edges
            float deltaPos1x = v1x - v0x, deltaPos1y = v1y - v0y, deltaPos1z = v1z - v0z;
            float deltaPos2x = v2x - v0x, deltaPos2y = v2y - v0y, deltaPos2z = v2z - v0z;

            // Calculate UV edges
            float deltaUV1x = uv1x - uv0x, deltaUV1y = uv1y - uv0y;
            float deltaUV2x = uv2x - uv0x, deltaUV2y = uv2y - uv0y;

            // Calculate the fraction. Protect against division by zero if UVs are bad/missing.
            float r = 1.0f / (deltaUV1x * deltaUV2y - deltaUV1y * deltaUV2x);
            if (Float.isInfinite(r) || Float.isNaN(r)) r = 0.0f;

            // Cross multiply
            float tx = (deltaPos1x * deltaUV2y - deltaPos2x * deltaUV1y) * r;
            float ty = (deltaPos1y * deltaUV2y - deltaPos2y * deltaUV1y) * r;
            float tz = (deltaPos1z * deltaUV2y - deltaPos2z * deltaUV1y) * r;

            // Accumulate the tangent vectors back onto the vertices
            tangents[i0 * 3] += tx; tangents[i0 * 3 + 1] += ty; tangents[i0 * 3 + 2] += tz;
            tangents[i1 * 3] += tx; tangents[i1 * 3 + 1] += ty; tangents[i1 * 3 + 2] += tz;
            tangents[i2 * 3] += tx; tangents[i2 * 3 + 1] += ty; tangents[i2 * 3 + 2] += tz;
        }

        // Normalize all tangents at the end so they have a length of exactly 1.0
        for (int i = 0; i < tangents.length; i += 3) {
            float tx = tangents[i], ty = tangents[i + 1], tz = tangents[i + 2];
            float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (len > 0) {
                tangents[i] = tx / len;
                tangents[i + 1] = ty / len;
                tangents[i + 2] = tz / len;
            }
        }

        mesh.tangents = tangents;
    }

    private static void parseFace(byte[] data, int[] offset,
                                  util.FloatList rawPos, util.FloatList rawTex, util.FloatList rawNorm,
                                  util.FloatList outPos, util.FloatList outTex, util.FloatList outNorm, util.IntList outIndices) {
        int startVertexCount = outPos.size() / 3;
        int firstVertexIndex = -1;
        int previousVertexIndex = -1;
        int vertexCount = 0;

        int i = offset[0];
        while (i < data.length && data[i] != '\n' && data[i] != '\r') {
            while (i < data.length && (data[i] == ' ' || data[i] == '\t')) i++;
            if (i >= data.length || data[i] == '\n' || data[i] == '\r') break;

            offset[0] = i;
            int posIdx = parseInt(data, offset) - 1;
            int texIdx = -1;
            int normIdx = -1;

            i = offset[0];
            if (i < data.length && data[i] == '/') {
                i++;
                if (i < data.length && data[i] != '/') {
                    offset[0] = i;
                    texIdx = parseInt(data, offset) - 1;
                    i = offset[0];
                }
                if (i < data.length && data[i] == '/') {
                    i++;
                    offset[0] = i;
                    normIdx = parseInt(data, offset) - 1;
                    i = offset[0];
                }
            }

            // [THE BOUNDS FIX]: Protects the FloatList from crashing!
            if (posIdx >= 0 && (posIdx * 3 + 2) < rawPos.size()) {
                outPos.add(rawPos.get(posIdx * 3), rawPos.get(posIdx * 3 + 1), rawPos.get(posIdx * 3 + 2));
            } else {
                outPos.add(0f, 0f, 0f); // Safe fallback
            }

            if (texIdx >= 0 && (texIdx * 2 + 1) < rawTex.size()) {
                outTex.add(rawTex.get(texIdx * 2), rawTex.get(texIdx * 2 + 1));
            } else {
                outTex.add(0f, 0f);
            }

            if (normIdx >= 0 && (normIdx * 3 + 2) < rawNorm.size()) {
                outNorm.add(rawNorm.get(normIdx * 3), rawNorm.get(normIdx * 3 + 1), rawNorm.get(normIdx * 3 + 2));
            } else {
                outNorm.add(0f, 1f, 0f);
            }

            int currentVertexIndex = startVertexCount + vertexCount;
            vertexCount++;

            if (vertexCount >= 3) {
                outIndices.add(firstVertexIndex);
                outIndices.add(previousVertexIndex);
                outIndices.add(currentVertexIndex);
            } else if (vertexCount == 1) {
                firstVertexIndex = currentVertexIndex;
            }
            previousVertexIndex = currentVertexIndex;
        }
        offset[0] = i;
    }

    /**
     * Extracts a floating-point number directly from an ASCII byte array.
     * Insanely fast. Bypasses the heavy Float.parseFloat() logic.
     */
    private static float parseFloat(byte[] data, int[] offset) {
        int i = offset[0];
        while (i < data.length && (data[i] == ' ' || data[i] == '\t')) i++;

        boolean negative = false;
        if (i < data.length && data[i] == '-') { negative = true; i++; }
        else if (i < data.length && data[i] == '+') { i++; }

        float value = 0;
        while (i < data.length && data[i] >= '0' && data[i] <= '9') {
            value = value * 10 + (data[i] - '0');
            i++;
        }

        if (i < data.length && data[i] == '.') {
            i++;
            float fraction = 0;
            float divisor = 1;
            while (i < data.length && data[i] >= '0' && data[i] <= '9') {
                fraction = fraction * 10 + (data[i] - '0');
                divisor *= 10;
                i++;
            }
            value += fraction / divisor;
        }

        // Handle Scientific Notation (E/e) exported by some 3D software
        if (i < data.length && (data[i] == 'e' || data[i] == 'E')) {
            i++;
            boolean expNegative = false;
            if (i < data.length && data[i] == '-') { expNegative = true; i++; }
            else if (i < data.length && data[i] == '+') { i++; }

            int exp = 0;
            while (i < data.length && data[i] >= '0' && data[i] <= '9') {
                exp = exp * 10 + (data[i] - '0');
                i++;
            }
            value = (float) (value * Math.pow(10, expNegative ? -exp : exp));
        }

        offset[0] = i;
        return negative ? -value : value;
    }

    /**
     * Extracts an integer directly from an ASCII byte array.
     */
    private static int parseInt(byte[] data, int[] offset) {
        int i = offset[0];
        while (i < data.length && (data[i] == ' ' || data[i] == '\t')) i++;

        boolean negative = false;
        if (i < data.length && data[i] == '-') { negative = true; i++; }
        else if (i < data.length && data[i] == '+') { i++; }

        int value = 0;
        while (i < data.length && data[i] >= '0' && data[i] <= '9') {
            value = value * 10 + (data[i] - '0');
            i++;
        }
        offset[0] = i;
        return negative ? -value : value;
    }

    /**
     * Standard Wavefront Loader (Default).
     * Assumes Y-Up geometry and standard bottom-left UVs that need flipping for Vulkan.
     */
    public static model.Mesh loadObject(String path) {
        // Default: Not Z-Up (false), Flip UVs for Vulkan (true)
        return loadObject(path, false, true);
    }

    /**
     * Advanced Loader for custom formats (like 3ds Max).
     * @param isZUp True if the model was exported from Z-Up software (3ds Max, Blender default).
     * @param flipUV True to invert the vertical texture coordinates.
     */
    public static model.Mesh loadObject(String path, boolean isZUp, boolean flipUV) {
        System.out.println("Assimp Streaming: " + path + " | Z-Up: " + isZUp + " | FlipUV: " + flipUV);

        String meshName = path.substring(path.lastIndexOf('/') + 1);
        if (meshName.contains(".")) meshName = meshName.substring(0, meshName.lastIndexOf('.'));

        // 1. Fetch Fat-JAR asset
        java.nio.ByteBuffer fileBuffer = resources.Resources.streamToOffHeap(path);
        if (fileBuffer == null) throw new RuntimeException("streamToOffHeap failed to locate: " + path);

        int flags = aiProcess_Triangulate | aiProcess_JoinIdenticalVertices | aiProcess_CalcTangentSpace | aiProcess_GenSmoothNormals;
        AIScene scene = aiImportFileFromMemory(fileBuffer, flags, (CharSequence) "");

        if (scene == null || scene.mMeshes() == null) {
            org.lwjgl.system.MemoryUtil.memFree(fileBuffer);
            throw new RuntimeException("Assimp failed to parse: " + aiGetErrorString());
        }

        AIMesh aiMesh = AIMesh.create(scene.mMeshes().get(0));
        int vertexCount = aiMesh.mNumVertices();

        model.Mesh mesh = new model.Mesh(meshName);
        mesh.positions = new float[vertexCount * 3];
        mesh.textures = new float[vertexCount * 2];
        mesh.normals = new float[vertexCount * 3];

        // 2. Extract Positions (Dynamic Axis Swapping)
        AIVector3D.Buffer aiVertices = aiMesh.mVertices();
        for (int i = 0; i < vertexCount; i++) {
            AIVector3D v = aiVertices.get(i);
            mesh.positions[i * 3] = v.x();
            if (isZUp) {
                mesh.positions[i * 3 + 1] = -v.z(); // 3ds Max Z-Up to Vulkan Y-Down
                mesh.positions[i * 3 + 2] = -v.y();
            } else {
                mesh.positions[i * 3 + 1] = -v.y(); // Standard Y-Up to Vulkan Y-Down
                mesh.positions[i * 3 + 2] = v.z();
            }
        }

        // 3. Extract Normals (Matching the Position Swizzle)
        AIVector3D.Buffer aiNormals = aiMesh.mNormals();
        if (aiNormals != null) {
            for (int i = 0; i < vertexCount; i++) {
                AIVector3D n = aiNormals.get(i);
                mesh.normals[i * 3] = n.x();
                if (isZUp) {
                    mesh.normals[i * 3 + 1] = -n.z();
                    mesh.normals[i * 3 + 2] = -n.y();
                } else {
                    mesh.normals[i * 3 + 1] = -n.y();
                    mesh.normals[i * 3 + 2] = n.z();
                }
            }
        }

        // 4. Extract Textures (Dynamic UV Flipping)
        AIVector3D.Buffer texCoords = aiMesh.mTextureCoords(0);
        if (texCoords != null) {
            for (int i = 0; i < vertexCount; i++) {
                AIVector3D t = texCoords.get(i);
                mesh.textures[i * 2] = t.x();
                // [THE FIX]: If 3ds Max already baked the UVs top-left, we can toggle the flip off!
                mesh.textures[i * 2 + 1] = flipUV ? (1.0f - t.y()) : t.y();
            }
        }

        // 5. Extract Indices (Winding order is flipped because we negated the Vulkan Y-Axis)
        int faceCount = aiMesh.mNumFaces();
        AIFace.Buffer faces = aiMesh.mFaces();
        mesh.indices = new int[faceCount * 3];

        int indexCount = 0;
        for (int i = 0; i < faceCount; i++) {
            AIFace face = faces.get(i);
            if (face.mNumIndices() == 3) {
                mesh.indices[indexCount++] = face.mIndices().get(0);
                mesh.indices[indexCount++] = face.mIndices().get(2);
                mesh.indices[indexCount++] = face.mIndices().get(1);
            }
        }

        aiReleaseImport(scene);
        org.lwjgl.system.MemoryUtil.memFree(fileBuffer);

        MeshLoader.loadMesh(mesh);
        return mesh;
    }

    // --- GETTERS FOR RENDERER ---
    public static long getVertexBuffer(int id) { return vertexBuffers[id]; }
    public static long getIndexBuffer(int id) { return indexBuffers[id]; }
    public static int getIndexCount(int id) { return indexCounts[id]; }
    public static long getUvBuffer(int id) { return uvBuffers[id]; }
}