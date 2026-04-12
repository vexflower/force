package util;

import org.lwjgl.system.MemoryUtil;
import java.nio.ShortBuffer;

public final class CShortList extends CMemory {

    public CShortList() {
        this(128);
    }

    public CShortList(int initialCapacity) {
        this.capacity = initialCapacity;
        this.size = 0;
        this.ptr = MemoryUtil.nmemAlloc(this.capacity * 2L); // 2 bytes per element
    }

    public void add(short value) {
        if (size >= capacity) expand(capacity * 2);
        MemoryUtil.memPutShort(ptr + (size * 2L), value);
        size++;
    }

    public short get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
        return MemoryUtil.memGetShort(ptr + (index * 2L));
    }

    public void set(int index, short value) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
        MemoryUtil.memPutShort(ptr + (index * 2L), value);
    }

    private void expand(int newCapacity) {
        this.capacity = newCapacity;
        this.ptr = MemoryUtil.nmemRealloc(this.ptr, this.capacity * 2L);
    }

    public void clear() {
        this.size = 0;
    }

    public ShortBuffer buffer() {
        return MemoryUtil.memShortBuffer(ptr, size);
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