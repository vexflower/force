package mesh;

import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.system.MemoryUtil;
import resources.Resources;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class MeshParser {

    private static final int MAX_TRIANGLES = 128;
    private static final int MAGIC_NUMBER = 0x4D53484C;

    private static class Meshlet {
        int indexOffset, indexCount;
        float bX, bY, bZ, bRadius;
        float cX, cY, cZ, cCutoff;
    }

    public static void main(String[] args) {
        // Pass both the source and the target paths relative to your Vault root
        parseAndBake("\\obj\\fox.obj", "\\meshes\\fox.meshl", true, false);
    }

    public static void parseAndBake(String inputObjPath, String outputPath, boolean isZUp, boolean flipUV) {
        // 1. Resolve absolute physical path for the input OBJ
        String fullInputPath = resources.Resources.ENGINE_BASE_PATH + inputObjPath;
        System.out.println("Reading External OBJ: " + fullInputPath);

        File file = new File(fullInputPath);
        if (!file.exists()) {
            throw new RuntimeException("OBJ file not found in Vault: " + fullInputPath);
        }

        // 2. Direct Disk Read (Bypassing streamToOffHeap/classpath)
        ByteBuffer fileBuffer;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel channel = raf.getChannel()) {

            // Allocate native memory and read the outside file directly
            fileBuffer = MemoryUtil.memAlloc((int) channel.size());
            channel.read(fileBuffer);
            fileBuffer.flip();

        } catch (Exception e) {
            throw new RuntimeException("Critical failure reading external asset: " + e.getMessage());
        }

        // 3. Process with Assimp
        int flags = org.lwjgl.assimp.Assimp.aiProcess_Triangulate |
                org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices |
                org.lwjgl.assimp.Assimp.aiProcess_CalcTangentSpace |
                org.lwjgl.assimp.Assimp.aiProcess_GenSmoothNormals |
                org.lwjgl.assimp.Assimp.aiProcess_MakeLeftHanded;

        AIScene scene = org.lwjgl.assimp.Assimp.aiImportFileFromMemory(fileBuffer, flags, (CharSequence) "");
        if (scene == null || scene.mMeshes() == null) {
            MemoryUtil.memFree(fileBuffer);
            throw new RuntimeException("Assimp failed to parse memory buffer.");
        }

        int numMeshes = scene.mNumMeshes();
        PointerBuffer aiMeshes = scene.mMeshes();

        // [Pre-pass and Normalization Logic remain exactly as you have them]
        int totalVertices = 0;
        int totalFaces = 0;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        for (int m = 0; m < numMeshes; m++) {
            AIMesh aiMesh = AIMesh.create(aiMeshes.get(m));
            totalVertices += aiMesh.mNumVertices();
            totalFaces += aiMesh.mNumFaces();

            AIVector3D.Buffer aiVerts = aiMesh.mVertices();
            for (int i = 0; i < aiMesh.mNumVertices(); i++) {
                AIVector3D v = aiVerts.get(i);
                float px = v.x(), py = isZUp ? -v.z() : v.y(), pz = isZUp ? v.y() : v.z();
                if (px < minX) minX = px; if (px > maxX) maxX = px;
                if (py < minY) minY = py; if (py > maxY) maxY = py;
                if (pz < minZ) minZ = pz; if (pz > maxZ) maxZ = pz;
            }
        }

        float cx = (minX + maxX) / 2f, cy = (minY + maxY) / 2f, cz = (minZ + maxZ) / 2f;
        float maxDim = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        if (maxDim == 0) maxDim = 1.0f;

        float[] positions = new float[totalVertices * 3];
        float[] uvs = new float[totalVertices * 2];
        float[] normals = new float[totalVertices * 3];
        float[] tangents = new float[totalVertices * 3];
        int[] indices = new int[totalFaces * 3];

        int vOffset = 0;
        int iOffset = 0;

        for (int m = 0; m < numMeshes; m++) {
            AIMesh aiMesh = AIMesh.create(aiMeshes.get(m));
            int vertexCount = aiMesh.mNumVertices();
            int faceCount = aiMesh.mNumFaces();

            AIVector3D.Buffer aiVerts = aiMesh.mVertices();
            AIVector3D.Buffer aiNorms = aiMesh.mNormals();
            AIVector3D.Buffer aiTex = aiMesh.mTextureCoords(0);
            AIVector3D.Buffer aiTangs = aiMesh.mTangents();

            for (int i = 0; i < vertexCount; i++) {
                AIVector3D v = aiVerts.get(i);
                positions[(vOffset + i)*3]   = (v.x() - cx) / maxDim;
                positions[(vOffset + i)*3+1] = ((isZUp ? -v.z() : v.y()) - cy) / maxDim;
                positions[(vOffset + i)*3+2] = ((isZUp ? v.y() : v.z()) - cz) / maxDim;

                if (aiTex != null) {
                    AIVector3D t = aiTex.get(i);
                    uvs[(vOffset + i)*2]   = t.x();
                    uvs[(vOffset + i)*2+1] = flipUV ? 1f - t.y() : t.y();
                }

                if (aiNorms != null) {
                    AIVector3D n = aiNorms.get(i);
                    normals[(vOffset + i)*3]   = n.x();
                    normals[(vOffset + i)*3+1] = isZUp ? -n.z() : n.y();
                    normals[(vOffset + i)*3+2] = isZUp ? n.y() : n.z();
                }

                if (aiTangs != null) {
                    AIVector3D tg = aiTangs.get(i);
                    tangents[(vOffset + i)*3]   = tg.x();
                    tangents[(vOffset + i)*3+1] = isZUp ? -tg.z() : tg.y();
                    tangents[(vOffset + i)*3+2] = isZUp ? tg.y() : tg.z();
                }
            }

            AIFace.Buffer faces = aiMesh.mFaces();
            for (int i = 0; i < faceCount; i++) {
                AIFace face = faces.get(i);
                if (face.mNumIndices() == 3) {
                    indices[(iOffset + i)*3]   = face.mIndices().get(0) + vOffset;
                    indices[(iOffset + i)*3+1] = face.mIndices().get(1) + vOffset;
                    indices[(iOffset + i)*3+2] = face.mIndices().get(2) + vOffset;
                }
            }

            vOffset += vertexCount;
            iOffset += faceCount;
        }

        List<Meshlet> meshlets = new ArrayList<>();
        int unassignedTriangles = totalFaces;
        int nextIndex = 0;

        while (unassignedTriangles > 0) {
            Meshlet m = new Meshlet();
            m.indexOffset = nextIndex;
            int trisToTake = Math.min(MAX_TRIANGLES, unassignedTriangles);
            m.indexCount = trisToTake * 3;

            float mMinX = Float.MAX_VALUE, mMinY = Float.MAX_VALUE, mMinZ = Float.MAX_VALUE;
            float mMaxX = -Float.MAX_VALUE, mMaxY = -Float.MAX_VALUE, mMaxZ = -Float.MAX_VALUE;

            for (int t = 0; t < trisToTake; t++) {
                for (int corner = 0; corner < 3; corner++) {
                    int vId = indices[nextIndex + (t * 3) + corner];
                    float vx = positions[vId * 3];
                    float vy = positions[vId * 3 + 1];
                    float vz = positions[vId * 3 + 2];

                    if (vx < mMinX) mMinX = vx; if (vx > mMaxX) mMaxX = vx;
                    if (vy < mMinY) mMinY = vy; if (vy > mMaxY) mMaxY = vy;
                    if (vz < mMinZ) mMinZ = vz; if (vz > mMaxZ) mMaxZ = vz;
                }
            }

            m.bX = (mMinX + mMaxX) * 0.5f; m.bY = (mMinY + mMaxY) * 0.5f; m.bZ = (mMinZ + mMaxZ) * 0.5f;
            float dx = mMaxX - mMinX, dy = mMaxY - mMinY, dz = mMaxZ - mMinZ;
            m.bRadius = (float) Math.sqrt(dx*dx + dy*dy + dz*dz) * 0.5f;

            float axisX = 0, axisY = 0, axisZ = 0;
            for (int t = 0; t < trisToTake; t++) {
                for (int corner = 0; corner < 3; corner++) {
                    int vId = indices[nextIndex + (t * 3) + corner];
                    axisX += normals[vId * 3]; axisY += normals[vId * 3 + 1]; axisZ += normals[vId * 3 + 2];
                }
            }
            float length = (float) Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
            if (length > 0.0001f) { axisX /= length; axisY /= length; axisZ /= length; }
            else { axisY = 1.0f; }

            float maxDot = 1.0f;
            for (int t = 0; t < trisToTake; t++) {
                for (int corner = 0; corner < 3; corner++) {
                    int vId = indices[nextIndex + (t * 3) + corner];
                    float nx = normals[vId * 3], ny = normals[vId * 3 + 1], nz = normals[vId * 3 + 2];
                    float dot = (nx * axisX) + (ny * axisY) + (nz * axisZ);
                    if (dot < maxDot) maxDot = dot;
                }
            }

            float maxAngle = (float) Math.acos(Math.max(-1.0f, Math.min(1.0f, maxDot)));
            if (maxAngle >= Math.PI / 2.0) { m.cCutoff = 2.0f; }
            else { m.cCutoff = (float) Math.sin(maxAngle); }
            m.cX = axisX; m.cY = axisY; m.cZ = axisZ;

            meshlets.add(m);
            nextIndex += trisToTake * 3;
            unassignedTriangles -= trisToTake;
        }

        // 4. Bake to the meshl output path
        writeBinaryFile(outputPath, positions, uvs, normals, tangents, indices, meshlets);

        org.lwjgl.assimp.Assimp.aiReleaseImport(scene);
        MemoryUtil.memFree(fileBuffer);
        System.out.println("Successfully baked " + meshlets.size() + " unified meshlets to Vault.");
    }

    private static void writeBinaryFile(String path, float[] pos, float[] uvs, float[] norms, float[] tangs, int[] inds, List<Meshlet> meshlets) {
        try {
            path = Resources.ENGINE_BASE_PATH + path;
            File file = new File(path);
            File parentDir = file.getParentFile();

            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            int headerSize = 16;
            int posSize = pos.length * 4;
            int uvSize = uvs.length * 4;
            int normSize = norms.length * 4;
            int tangSize = tangs.length * 4;
            int indSize = inds.length * 4;
            int meshletSize = meshlets.size() * 48; // Ensure 48-byte stride

            ByteBuffer buffer = ByteBuffer.allocateDirect(headerSize + posSize + uvSize + normSize + tangSize + indSize + meshletSize);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            buffer.putInt(MAGIC_NUMBER);
            buffer.putInt(pos.length / 3);
            buffer.putInt(inds.length);
            buffer.putInt(meshlets.size());

            buffer.asFloatBuffer().put(pos); buffer.position(buffer.position() + posSize);
            buffer.asFloatBuffer().put(uvs); buffer.position(buffer.position() + uvSize);
            buffer.asFloatBuffer().put(norms); buffer.position(buffer.position() + normSize);
            buffer.asFloatBuffer().put(tangs); buffer.position(buffer.position() + tangSize);
            buffer.asIntBuffer().put(inds); buffer.position(buffer.position() + indSize);

            for (Meshlet m : meshlets) {
                buffer.putInt(m.indexOffset);
                buffer.putInt(m.indexCount);
                buffer.putInt(0); // Padding
                buffer.putInt(0); // Padding
                buffer.putFloat(m.bX); buffer.putFloat(m.bY); buffer.putFloat(m.bZ); buffer.putFloat(m.bRadius);
                buffer.putFloat(m.cX); buffer.putFloat(m.cY); buffer.putFloat(m.cZ); buffer.putFloat(m.cCutoff);
            }

            buffer.flip();
            try (FileOutputStream fos = new FileOutputStream(file); FileChannel channel = fos.getChannel()) {
                channel.write(buffer);
            }
        } catch (Exception e) {
            System.err.println("Bake Error: " + e.getMessage());
        }
    }
}