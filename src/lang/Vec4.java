package lang;

import lang.Vec3;

import java.nio.FloatBuffer;

/**
 * High-performance, independent 4D Vector.
 * Zero allocations, monomorphic dispatch.
 */
public final class Vec4 {
    public float x;
    public float y;
    public float z;
    public float w;

    public Vec4() {}

    public Vec4(float xyzw) {
        this.x = xyzw;
        this.y = xyzw;
        this.z = xyzw;
        this.w = xyzw;
    }

    public Vec4(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    public Vec4(Vec4 src) {
        this.x = src.x;
        this.y = src.y;
        this.z = src.z;
        this.w = src.w;
    }

    public Vec4(Vec3 position, float w) {
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        this.w = w;
    }

    public Vec4 set(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        return this;
    }

    public Vec4 set(Vec4 src) {
        this.x = src.x;
        this.y = src.y;
        this.z = src.z;
        this.w = src.w;
        return this;
    }

    // ==========================================
    // PRIMITIVE MATH (Fastest)
    // ==========================================

    public Vec4 translate(float x, float y, float z, float w) {
        this.x += x;
        this.y += y;
        this.z += z;
        this.w += w;
        return this;
    }

    public Vec4 add(float x, float y, float z, float w) {
        this.x += x;
        this.y += y;
        this.z += z;
        this.w += w;
        return this;
    }

    public Vec4 sub(float x, float y, float z, float w) {
        this.x -= x;
        this.y -= y;
        this.z -= z;
        this.w -= w;
        return this;
    }

    public Vec4 scale(float scale) {
        this.x *= scale;
        this.y *= scale;
        this.z *= scale;
        this.w *= scale;
        return this;
    }

    // ==========================================
    // OBJECT MATH (Zero Allocation)
    // ==========================================

    public Vec4 add(Vec4 right) {
        return add(this, right, this);
    }

    public static Vec4 add(Vec4 left, Vec4 right, Vec4 dest) {
        dest.x = left.x + right.x;
        dest.y = left.y + right.y;
        dest.z = left.z + right.z;
        dest.w = left.w + right.w;
        return dest;
    }

    public Vec4 sub(Vec4 right) {
        return sub(this, right, this);
    }

    public static Vec4 sub(Vec4 left, Vec4 right, Vec4 dest) {
        dest.x = left.x - right.x;
        dest.y = left.y - right.y;
        dest.z = left.z - right.z;
        dest.w = left.w - right.w;
        return dest;
    }

    // ==========================================
    // VECTOR OPERATIONS
    // ==========================================

    public float lengthSquared() {
        return this.x * this.x + this.y * this.y + this.z * this.z + this.w * this.w;
    }

    public float length() {
        return (float) Math.sqrt(lengthSquared());
    }

    public Vec4 negate() {
        return negate(this);
    }

    public Vec4 negate(Vec4 dest) {
        dest.x = -this.x;
        dest.y = -this.y;
        dest.z = -this.z;
        dest.w = -this.w;
        return dest;
    }

    public Vec4 normalize() {
        return normalize(this);
    }

    public Vec4 normalize(Vec4 dest) {
        float invLength = 1.0f / this.length();
        dest.x = this.x * invLength;
        dest.y = this.y * invLength;
        dest.z = this.z * invLength;
        dest.w = this.w * invLength;
        return dest;
    }

    public static float dot(Vec4 left, Vec4 right) {
        return left.x * right.x + left.y * right.y + left.z * right.z + left.w * right.w;
    }

    public static float angle(Vec4 a, Vec4 b) {
        float dls = dot(a, b) / (a.length() * b.length());
        if (dls < -1.0f) return (float) Math.PI;
        else if (dls > 1.0f) return 0.0f;
        return (float) Math.acos(dls);
    }

    // ==========================================
    // MEMORY BUFFERING
    // ==========================================

    public Vec4 load(FloatBuffer buf) {
        this.x = buf.get();
        this.y = buf.get();
        this.z = buf.get();
        this.w = buf.get();
        return this;
    }

    public Vec4 store(FloatBuffer buf) {
        buf.put(this.x);
        buf.put(this.y);
        buf.put(this.z);
        buf.put(this.w);
        return this;
    }

    // ==========================================
    // UTILITIES
    // ==========================================

    /**
     * Extracts X, Y, Z without allocating a new object.
     */
    public Vec3 xyz(Vec3 dest) {
        dest.set(this.x, this.y, this.z);
        return dest;
    }

    public boolean isWithin(Vec4 min, Vec4 max) {
        return x >= min.x && x <= max.x &&
                y >= min.y && y <= max.y &&
                z >= min.z && z <= max.z &&
                w >= min.w && w <= max.w;
    }

    @Override
    public String toString() {
        return "Vec4[" + this.x + ", " + this.y + ", " + this.z + ", " + this.w + ']';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || this.getClass() != obj.getClass()) return false;
        Vec4 other = (Vec4) obj;
        return this.x == other.x && this.y == other.y && this.z == other.z && this.w == other.w;
    }
}