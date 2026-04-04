package util;

/**
 * A high-performance generic list for high-level engine objects (Entities, UI, etc.).
 * Designed to replace java.util.ArrayList by removing safe-fail overhead
 * and introducing O(1) fast removals.
 */
public final class FastList<E> {
    private Object[] data;
    private int size;

    public FastList() {
        this(16);
    }

    public FastList(int initialCapacity) {
        data = new Object[initialCapacity];
        size = 0;
    }

    public void add(E element) {
        if (size == data.length) {
            expand();
        }
        data[size++] = element;
    }

    @SuppressWarnings("unchecked")
    public E get(int index) {
        if (index >= size || index < 0) throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        return (E) data[index];
    }

    /**
     * Standard removal. Shifts all subsequent elements to the left. O(n)
     * Use this when the ORDER of the list MUST be preserved (e.g., UI rendering order).
     */
    @SuppressWarnings("unchecked")
    public E remove(int index) {
        if (index >= size || index < 0) throw new IndexOutOfBoundsException();
        E removed = (E) data[index];

        int numMoved = size - index - 1;
        if (numMoved > 0) {
            System.arraycopy(data, index + 1, data, index, numMoved);
        }

        // Nullify the old last element to let the GC reclaim it
        data[--size] = null;
        return removed;
    }

    /**
     * ENGINE SPECIFIC TRICK: Fast Removal. O(1)
     * Replaces the removed element with the LAST element in the list.
     * Use this when the order of the list DOES NOT matter (e.g., ticking particles, physics entities).
     */
    @SuppressWarnings("unchecked")
    public E removeFast(int index) {
        if (index >= size || index < 0) throw new IndexOutOfBoundsException();
        E removed = (E) data[index];

        // Move the last element into the removed slot
        data[index] = data[size - 1];

        // Clear the old last slot
        data[--size] = null;

        return removed;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        // We MUST nullify object references here so the GC can reclaim them.
        for (int i = 0; i < size; i++) {
            data[i] = null;
        }
        size = 0;
    }

    private void expand() {
        int newCapacity = data.length * 2;
        Object[] newData = new Object[newCapacity];
        System.arraycopy(data, 0, newData, 0, size);
        data = newData;
    }

    public boolean contains(E element) {
        if (element == null) {
            for (int i = 0; i < size; i++) {
                if (data[i] == null) return true;
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (element.equals(data[i])) return true;
            }
        }
        return false;
    }
}