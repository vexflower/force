package loader;

import model.Mesh;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;

/**
 * High-performance Vulkan MeshLoader.
 * Replaces OpenGL VAOs/VBOs with Vulkan VkBuffers and Staging memory.
 */
public final class MeshLoader {

    private static int meshCount = 0;

    /**
     * Replaces the old Vulkan VAO/VBO logic.
     * Simply dumps the parsed mesh into the Global Registry.
     */
    public static void loadMesh(Mesh mesh) {
        GeomRegistry.appendMesh(mesh);
        mesh.vaoId = meshCount++;

        // Auto-Register everything instantly!
        MeshRegistry.register(mesh.name, mesh);
    }

    public static void destroy() {
        // Nothing to do here anymore! GlobalGeometryRegistry handles cleanup.
        meshCount = 0;
    }

    public static Mesh loadObject(String path, boolean isZUp, boolean flipUV) {
        System.out.println("Assimp Streaming: " + path);

        String meshName = path.substring(path.lastIndexOf('/') + 1);
        if (meshName.contains(".")) meshName = meshName.substring(0, meshName.lastIndexOf('.'));

        ByteBuffer fileBuffer = resources.Resources.streamToOffHeap(path);

        // [THE MAGIC BULLET]: We tell Assimp to convert everything to your Left-Handed rule automatically!
        int flags = org.lwjgl.assimp.Assimp.aiProcess_Triangulate |
                org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices |
                org.lwjgl.assimp.Assimp.aiProcess_CalcTangentSpace |
                org.lwjgl.assimp.Assimp.aiProcess_GenSmoothNormals |
                org.lwjgl.assimp.Assimp.aiProcess_MakeLeftHanded; // <--- ADDED

        AIScene scene = org.lwjgl.assimp.Assimp.aiImportFileFromMemory(fileBuffer, flags, (CharSequence) "");

        if (scene == null || scene.mMeshes() == null) {
            org.lwjgl.system.MemoryUtil.memFree(fileBuffer);
            throw new RuntimeException("Assimp failed to parse: " + org.lwjgl.assimp.Assimp.aiGetErrorString());
        }

        AIMesh aiMesh = AIMesh.create(scene.mMeshes().get(0));
        int vertexCount = aiMesh.mNumVertices();

        Mesh mesh = new Mesh(meshName);
        mesh.positions = new float[vertexCount * 3];
        mesh.textures = new float[vertexCount * 2];
        mesh.normals = new float[vertexCount * 3];

        // --- 1. EXTRACT AND SWIZZLE VERTICES ---
        AIVector3D.Buffer aiVertices = aiMesh.mVertices();
        for (int i = 0; i < vertexCount; i++) {
            AIVector3D v = aiVertices.get(i);
            mesh.positions[i * 3] = v.x();

            if (isZUp) {
                mesh.positions[i * 3 + 1] = -v.z();
                mesh.positions[i * 3 + 2] = v.y();
            } else {
                mesh.positions[i * 3 + 1] = v.y();
                mesh.positions[i * 3 + 2] = v.z();
            }
        }

        // --- 2. EXTRACT AND SWIZZLE NORMALS ---
        AIVector3D.Buffer aiNormals = aiMesh.mNormals();
        if (aiNormals != null) {
            for (int i = 0; i < vertexCount; i++) {
                AIVector3D n = aiNormals.get(i);
                mesh.normals[i * 3] = n.x();

                if (isZUp) {
                    mesh.normals[i * 3 + 1] = -n.z();
                    mesh.normals[i * 3 + 2] = n.y();
                } else {
                    mesh.normals[i * 3 + 1] = n.y();
                    mesh.normals[i * 3 + 2] = n.z();
                }
            }
        }

        AIVector3D.Buffer texCoords = aiMesh.mTextureCoords(0);
        if (texCoords != null) {
            for (int i = 0; i < vertexCount; i++) {
                AIVector3D t = texCoords.get(i);
                mesh.textures[i * 2] = t.x();
                mesh.textures[i * 2 + 1] = flipUV ? (1.0f - t.y()) : t.y();
            }
        }

        int faceCount = aiMesh.mNumFaces();
        AIFace.Buffer faces = aiMesh.mFaces();
        mesh.indices = new int[faceCount * 3];

        int indexCount = 0;
        for (int i = 0; i < faceCount; i++) {
            AIFace face = faces.get(i);
            if (face.mNumIndices() == 3) {
                mesh.indices[indexCount++] = face.mIndices().get(0);
                mesh.indices[indexCount++] = face.mIndices().get(1);
                mesh.indices[indexCount++] = face.mIndices().get(2);
            }
        }

        org.lwjgl.assimp.Assimp.aiReleaseImport(scene);
        org.lwjgl.system.MemoryUtil.memFree(fileBuffer);

        loadMesh(mesh); // Pushes it to the Bindless Registry!
        return mesh;
    }

}