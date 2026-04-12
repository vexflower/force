package util;

import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;

public final class CByteList extends CMemory {

    public CByteList() {
        this(128);
    }

    public CByteList(int initialCapacity) {
        this.capacity = initialCapacity;
        this.size = 0;
        this.ptr = MemoryUtil.nmemAlloc(this.capacity * 1L); // 1 byte per element
    }

    public void add(byte value) {
        if (size >= capacity) expand(capacity * 2);
        MemoryUtil.memPutByte(ptr + (size * 1L), value);
        size++;
    }

    public byte get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
        return MemoryUtil.memGetByte(ptr + ((long) index));
    }

    public void set(int index, byte value) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException();
        MemoryUtil.memPutByte(ptr + ((long) index), value);
    }

    private void expand(int newCapacity) {
        this.capacity = newCapacity;
        this.ptr = MemoryUtil.nmemRealloc(this.ptr, this.capacity * 1L);
    }

    public void clear() {
        this.size = 0;
    }

    public ByteBuffer buffer() {
        return MemoryUtil.memByteBuffer(ptr, size);
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