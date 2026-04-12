package util;

import org.lwjgl.system.MemoryUtil;
import java.nio.IntBuffer;

public final class CIntList extends CMemory {

    public CIntList() {
        this(128);
    }

    public CIntList(int initialCapacity) {
        this.capacity = initialCapacity;
        this.size = 0;
        this.ptr = MemoryUtil.nmemAlloc(this.capacity * 4L);
    }

    public void add(int value) {
        if (size >= capacity) expand(capacity * 2);
        MemoryUtil.memPutInt(ptr + (size * 4L), value);
        size++;
    }

    public int get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
        return MemoryUtil.memGetInt(ptr + (index * 4L));
    }

    public void set(int index, int value) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
        MemoryUtil.memPutInt(ptr + (index * 4L), value);
    }

    private void expand(int newCapacity) {
        this.capacity = newCapacity;
        this.ptr = MemoryUtil.nmemRealloc(this.ptr, this.capacity * 4L);
    }

    public void clear() {
        this.size = 0;
    }

    public IntBuffer buffer() {
        return MemoryUtil.memIntBuffer(ptr, size);
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