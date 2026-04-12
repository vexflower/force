package loader;

import mesh.Mesh;
import mesh.MeshDecimator;
import mesh.MeshOptimizer;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * High-performance Vulkan MeshLoader.
 * Reads from Assimp into temporary arrays, interleaves directly to C-Memory,
 * then triggers Procedural LOD generation.
 */
public final class MeshLoader {

    private static int meshCount = 0;

    public static void loadMesh(Mesh mesh) {
        GeomRegistry.appendMesh(mesh);
        mesh.vaoId = meshCount++;
        MeshRegistry.register(mesh.name, mesh);
    }

    public static void destroy() {
        meshCount = 0;
    }

    public static Mesh loadObject(String path, boolean isZUp, boolean flipUV) {
        System.out.println("Assimp Streaming: " + path);

        String meshName = path.substring(path.lastIndexOf('/') + 1);
        if (meshName.contains(".")) meshName = meshName.substring(0, meshName.lastIndexOf('.'));

        ByteBuffer fileBuffer = resources.Resources.streamToOffHeap(path);

        int flags = org.lwjgl.assimp.Assimp.aiProcess_Triangulate |
                org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices |
                org.lwjgl.assimp.Assimp.aiProcess_CalcTangentSpace |
                org.lwjgl.assimp.Assimp.aiProcess_GenSmoothNormals |
                org.lwjgl.assimp.Assimp.aiProcess_MakeLeftHanded;

        AIScene scene = org.lwjgl.assimp.Assimp.aiImportFileFromMemory(fileBuffer, flags, (CharSequence) "");

        if (scene == null || scene.mMeshes() == null) {
            MemoryUtil.memFree(fileBuffer);
            throw new RuntimeException("Assimp failed to parse: " + org.lwjgl.assimp.Assimp.aiGetErrorString());
        }

        AIMesh aiMesh = AIMesh.create(scene.mMeshes().get(0));
        int vertexCount = aiMesh.mNumVertices();

        Mesh mesh = new Mesh(meshName);

        // THE FIX: Use local temporary arrays, NOT mesh.positions
        float[] rawPositions = new float[vertexCount * 3];
        float[] rawTextures = new float[vertexCount * 2];
        float[] rawNormals = new float[vertexCount * 3];

        // --- 1. EXTRACT AND SWIZZLE VERTICES ---
        AIVector3D.Buffer aiVertices = aiMesh.mVertices();
        for (int i = 0; i < vertexCount; i++) {
            AIVector3D v = aiVertices.get(i);
            rawPositions[i * 3] = v.x();

            if (isZUp) {
                rawPositions[i * 3 + 1] = -v.z();
                rawPositions[i * 3 + 2] = v.y();
            } else {
                rawPositions[i * 3 + 1] = v.y();
                rawPositions[i * 3 + 2] = v.z();
            }
        }

        // --- 2. EXTRACT AND SWIZZLE NORMALS ---
        AIVector3D.Buffer aiNormals = aiMesh.mNormals();
        if (aiNormals != null) {
            for (int i = 0; i < vertexCount; i++) {
                AIVector3D n = aiNormals.get(i);
                rawNormals[i * 3] = n.x();

                if (isZUp) {
                    rawNormals[i * 3 + 1] = -n.z();
                    rawNormals[i * 3 + 2] = n.y();
                } else {
                    rawNormals[i * 3 + 1] = n.y();
                    rawNormals[i * 3 + 2] = n.z();
                }
            }
        }

        AIVector3D.Buffer texCoords = aiMesh.mTextureCoords(0);
        if (texCoords != null) {
            for (int i = 0; i < vertexCount; i++) {
                AIVector3D t = texCoords.get(i);
                rawTextures[i * 2] = t.x();
                rawTextures[i * 2 + 1] = flipUV ? (1.0f - t.y()) : t.y();
            }
        }

        // ====================================================================
        // --- 3. AAA METRIC NORMALIZATION (Fit to 1x1x1 Unit Cube) ---
        // ====================================================================
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        // Step A: Find bounding box
        for (int i = 0; i < vertexCount; i++) {
            float x = rawPositions[i * 3];
            float y = rawPositions[i * 3 + 1];
            float z = rawPositions[i * 3 + 2];

            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }

        float centerX = (minX + maxX) / 2.0f;
        float centerY = (minY + maxY) / 2.0f;
        float centerZ = (minZ + maxZ) / 2.0f;

        float sizeX = maxX - minX;
        float sizeY = maxY - minY;
        float sizeZ = maxZ - minZ;

        float maxDimension = Math.max(sizeX, Math.max(sizeY, sizeZ));
        if (maxDimension == 0) maxDimension = 1.0f;

        // Step C: Allocate Off-Heap memory for LOD 0 (8 floats per vertex)
        mesh.lodVertexCounts[0] = vertexCount;
        mesh.lodVertexPtrs[0] = MemoryUtil.nmemAlloc(vertexCount * 8L * 4L);
        mesh.lodDistancesSq[0] = 0f;

        // Interleave and normalize directly into C-memory!
        for (int i = 0; i < vertexCount; i++) {
            long vPtr = mesh.lodVertexPtrs[0] + (i * 8L * 4L);

            // Positions (Normalized)
            float nx = (rawPositions[i * 3] - centerX) / maxDimension;
            float ny = (rawPositions[i * 3 + 1] - centerY) / maxDimension;
            float nz = (rawPositions[i * 3 + 2] - centerZ) / maxDimension;
            MemoryUtil.memPutFloat(vPtr, nx);
            MemoryUtil.memPutFloat(vPtr + 4, ny);
            MemoryUtil.memPutFloat(vPtr + 8, nz);

            // Textures
            MemoryUtil.memPutFloat(vPtr + 12, rawTextures[i * 2]);
            MemoryUtil.memPutFloat(vPtr + 16, rawTextures[i * 2 + 1]);

            // Normals
            MemoryUtil.memPutFloat(vPtr + 20, rawNormals[i * 3]);
            MemoryUtil.memPutFloat(vPtr + 24, rawNormals[i * 3 + 1]);
            MemoryUtil.memPutFloat(vPtr + 28, rawNormals[i * 3 + 2]);
        }

        // Extract Indices directly to Off-Heap
        int faceCount = aiMesh.mNumFaces();
        AIFace.Buffer faces = aiMesh.mFaces();

        mesh.lodIndexCounts[0] = faceCount * 3;
        mesh.lodIndexPtrs[0] = MemoryUtil.nmemAlloc(mesh.lodIndexCounts[0] * 4L);

        int indexOffset = 0;
        for (int i = 0; i < faceCount; i++) {
            AIFace face = faces.get(i);
            if (face.mNumIndices() == 3) {
                MemoryUtil.memPutInt(mesh.lodIndexPtrs[0] + (indexOffset++ * 4L), face.mIndices().get(0));
                MemoryUtil.memPutInt(mesh.lodIndexPtrs[0] + (indexOffset++ * 4L), face.mIndices().get(1));
                MemoryUtil.memPutInt(mesh.lodIndexPtrs[0] + (indexOffset++ * 4L), face.mIndices().get(2));
            }
        }

        org.lwjgl.assimp.Assimp.aiReleaseImport(scene);
        MemoryUtil.memFree(fileBuffer);

        MeshOptimizer.generateLODs(mesh);

        loadMesh(mesh);
        return mesh;
    }
}