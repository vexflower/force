package loader;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL46.*;

/**
 * High-performance ModelLoader.
 * Replaces object-based tracking with primitive arrays to avoid auto-boxing.
 */
public final class MeshLoader
{

    // Custom primitive arrays to avoid LinkList<Integer> boxing memory leaks
    private static int[] vaos = new int[128];
    private static int vaoCount = 0;

    private static int[] vbos = new int[512];
    private static int vboCount = 0;

    private static int[] textures = new int[128];
    private static int textureCount = 0;

    private static int createVao() {
        int vaoId = glGenVertexArrays();
        if (vaoCount == vaos.length) expandVaos();
        vaos[vaoCount++] = vaoId;
        glBindVertexArray(vaoId);
        return vaoId;
    }

    private static void storeDataInAttributeList(int attributeNumber, int vertexLength, float[] data) {
        int vboId = glGenBuffers();
        if (vboCount == vbos.length) expandVbos();
        vbos[vboCount++] = vboId;

        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        // Use MemoryUtil to allocate a direct C-buffer (Bypasses Java Heap limit)
        FloatBuffer buffer = MemoryUtil.memAllocFloat(data.length);
        buffer.put(data).flip();

        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        glVertexAttribPointer(attributeNumber, vertexLength, GL_FLOAT, false, 0, 0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        // Must manually free direct memory!
        MemoryUtil.memFree(buffer);
    }

    private static void bindIndicesBuffer(int[] indices) {
        int vboId = glGenBuffers();
        if (vboCount == vbos.length) expandVbos();
        vbos[vboCount++] = vboId;

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboId);

        IntBuffer buffer = MemoryUtil.memAllocInt(indices.length);
        buffer.put(indices).flip();

        glBufferData(GL_ELEMENT_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);

        MemoryUtil.memFree(buffer);
    }

    public static void addTexture(int textureId) {
        if (textureCount == textures.length) expandTextures();
        textures[textureCount++] = textureId;
    }

    public static void destroy() {
        for (int i = 0; i < vaoCount; i++) glDeleteVertexArrays(vaos[i]);
        for (int i = 0; i < vboCount; i++) glDeleteBuffers(vbos[i]);
        for (int i = 0; i < textureCount; i++) glDeleteTextures(textures[i]);

        vaoCount = 0;
        vboCount = 0;
        textureCount = 0;
    }

    // --- Array Resizing Utilities ---
    private static void expandVaos() {
        int[] newArr = new int[vaos.length * 2];
        System.arraycopy(vaos, 0, newArr, 0, vaos.length);
        vaos = newArr;
    }
    private static void expandVbos() {
        int[] newArr = new int[vbos.length * 2];
        System.arraycopy(vbos, 0, newArr, 0, vbos.length);
        vbos = newArr;
    }
    private static void expandTextures() {
        int[] newArr = new int[textures.length * 2];
        System.arraycopy(textures, 0, newArr, 0, textures.length);
        textures = newArr;
    }
}