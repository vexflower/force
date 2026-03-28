package util;

import java.util.Arrays;

/**
 * A high-performance, contiguous, auto-expanding list for primitive integers.
 * Ideal for storing OpenGL indices or Entity IDs without Object overhead.
 */
public final class IntList {
    private int[] data;
    private int size;

    public IntList() {
        this(128);
    }

    public IntList(int initialCapacity) {
        data = new int[initialCapacity];
        size = 0;
    }

    public void add(int value) {
        if (size == data.length) {
            expand();
        }
        data[size++] = value;
    }

    public int get(int index) {
        if (index >= size || index < 0) throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        return data[index];
    }

    // Inside util/IntList.java

    // [CHANGED: Added set method so the Scene can update Entity properties]
    public void set(int index, int value) {
        if (index >= size || index < 0) throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        data[index] = value;
    }

    public int size() {
        return size;
    }

    public void clear() {
        size = 0; // Zero allocations on clear
    }

    public int[] toArray() {
        return Arrays.copyOf(data, size);
    }

    private void expand() {
        int[] newData = new int[data.length * 2];
        System.arraycopy(data, 0, newData, 0, size);
        data = newData;
    }
}