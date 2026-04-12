package util;

import org.lwjgl.system.MemoryUtil;
import java.nio.LongBuffer;

public final class CLongList extends CMemory {

    public CLongList() {
        this(128);
    }

    public CLongList(int initialCapacity) {
        this.capacity = initialCapacity;
        this.size = 0;
        this.ptr = MemoryUtil.nmemAlloc(this.capacity * 8L); // 8 bytes per element
    }

    public void add(long value) {
        if (size >= capacity) expand(capacity * 2);
        MemoryUtil.memPutLong(ptr + (size * 8L), value);
        size++;
    }

    // Quick helper for your Scene.java hash checking
    public boolean contains(long value) {
        for (int i = 0; i < size; i++) {
            if (MemoryUtil.memGetLong(ptr + (i * 8L)) == value) return true;
        }
        return false;
    }

    public long get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
        return MemoryUtil.memGetLong(ptr + (index * 8L));
    }

    public void set(int index, long value) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
        MemoryUtil.memPutLong(ptr + (index * 8L), value);
    }

    private void expand(int newCapacity) {
        this.capacity = newCapacity;
        this.ptr = MemoryUtil.nmemRealloc(this.ptr, this.capacity * 8L);
    }

    public void clear() {
        this.size = 0;
    }

    public LongBuffer buffer() {
        return MemoryUtil.memLongBuffer(ptr, size);
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