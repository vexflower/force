package util;

import java.util.Arrays;

/**
 * A high-performance, contiguous, auto-expanding list for primitive floats.
 * Bypasses the severe auto-boxing penalties of ArrayList<Float> and the
 * cache-miss penalties of Linked Lists.
 */
public final class FloatList {
    private float[] data;
    private int size;

    public FloatList() {
        this(128); // Default capacity
    }

    public FloatList(int initialCapacity) {
        data = new float[initialCapacity];
        size = 0;
    }

    public void add(float value) {
        if (size == data.length) {
            expand();
        }
        data[size++] = value;
    }

    public void add(float x, float y)
    {
        if(size + 2 > data.length) {
            expand(size + 3);
            data[size++] = x;
            data[size++] = y;
        }
    }

    public void add(float x, float y, float z) {
        if (size + 3 > data.length) {
            expand(size + 3);
        }
        data[size++] = x;
        data[size++] = y;
        data[size++] = z;
    }

    public float get(int index) {
        if (index >= size || index < 0) throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        return data[index];
    }

    public void set(int index, float value) {
        if (index >= size || index < 0) throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        data[index] = value;
    }

    public int size() {
        return size;
    }

    public void clear() {
        size = 0; // We don't overwrite the array, we just reset the pointer! Zero GC!
    }

    public float[] toArray() {
        return Arrays.copyOf(data, size);
    }

    private void expand() {
        expand(data.length * 2);
    }

    private void expand(int minCapacity) {
        int newCapacity = Math.max(data.length * 2, minCapacity);
        float[] newData = new float[newCapacity];
        System.arraycopy(data, 0, newData, 0, size);
        data = newData;
    }

    public boolean contains(float value) {
        for (int i = 0; i < size; i++) {
            if (data[i] == value) return true;
        }
        return false;
    }
}