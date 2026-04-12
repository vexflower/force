package util;

import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;

public final class CFloatList extends CMemory {

    public CFloatList() {
        this(128); // Default capacity
    }

    public CFloatList(int initialCapacity) {
        this.capacity = initialCapacity;
        this.size = 0;
        // Allocate raw memory: capacity * 4 bytes per float
        this.ptr = MemoryUtil.nmemAlloc(this.capacity * 4L);
    }

    public void add(float value) {
        if (size >= capacity) expand(capacity * 2);
        MemoryUtil.memPutFloat(ptr + (size * 4L), value);
        size++;
    }

    public void add(float x, float y, float z) {
        if (size + 3 > capacity) expand(Math.max(capacity * 2, size + 3));
        MemoryUtil.memPutFloat(ptr + (size * 4L), x);
        MemoryUtil.memPutFloat(ptr + ((size + 1) * 4L), y);
        MemoryUtil.memPutFloat(ptr + ((size + 2) * 4L), z);
        size += 3;
    }

    public float get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
        return MemoryUtil.memGetFloat(ptr + (index * 4L));
    }

    public void set(int index, float value) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
        MemoryUtil.memPutFloat(ptr + (index * 4L), value);
    }

    private void expand(int newCapacity) {
        this.capacity = newCapacity;
        // nmemRealloc safely copies the old C-memory to a new, larger block and frees the old one!
        this.ptr = MemoryUtil.nmemRealloc(this.ptr, this.capacity * 4L);
    }

    public void clear() {
        this.size = 0; // Just reset the pointer, data is overwritten next time
    }

    /**
     * Wraps the native memory in a Java FloatBuffer for fast Vulkan uploads.
     * ZERO allocations on the buffer data itself.
     */
    public FloatBuffer buffer() {
        return MemoryUtil.memFloatBuffer(ptr, size);
    }

    @Override
    public void free() {
        if (ptr != MemoryUtil.NULL) {
            MemoryUtil.nmemFree(ptr);
            ptr = MemoryUtil.NULL;
            size = 0;
            capacity = 0;
        }
    }
}