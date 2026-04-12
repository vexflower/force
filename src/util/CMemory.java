package util;

public abstract class CMemory implements AutoCloseable {
    protected long ptr;
    protected int size;
    protected int capacity;

    public long address() {
        return ptr;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * MUST be called to prevent native memory leaks!
     */
    public abstract void free();

    @Override
    public void close() {
        free();
    }
}