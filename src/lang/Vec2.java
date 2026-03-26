package lang;

import java.nio.FloatBuffer;

/**
 * High-performance, independent 2D Vector.
 * Zero allocations, monomorphic dispatch.
 */
public final class Vec2 {
    public float x;
    public float y;

    public Vec2() {}

    public Vec2(float xy) {
        this.x = xy;
        this.y = xy;
    }

    public Vec2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Vec2(Vec2 src) {
        this.x = src.x;
        this.y = src.y;
    }

    public Vec2 set(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public Vec2 set(Vec2 src) {
        this.x = src.x;
        this.y = src.y;
        return this;
    }

    // ==========================================
    // PRIMITIVE MATH (Fastest)
    // ==========================================

    public Vec2 translate(float x, float y) {
        this.x += x;
        this.y += y;
        return this;
    }

    public Vec2 add(float x, float y) {
        this.x += x;
        this.y += y;
        return this;
    }

    public Vec2 sub(float x, float y) {
        this.x -= x;
        this.y -= y;
        return this;
    }

    public Vec2 scale(float scale) {
        this.x *= scale;
        this.y *= scale;
        return this;
    }

    // ==========================================
    // OBJECT MATH (Zero Allocation)
    // ==========================================

    public Vec2 add(Vec2 right) {
        return add(this, right, this);
    }

    public static Vec2 add(Vec2 left, Vec2 right, Vec2 dest) {
        dest.x = left.x + right.x;
        dest.y = left.y + right.y;
        return dest;
    }

    public Vec2 sub(Vec2 right) {
        return sub(this, right, this);
    }

    public static Vec2 sub(Vec2 left, Vec2 right, Vec2 dest) {
        dest.x = left.x - right.x;
        dest.y = left.y - right.y;
        return dest;
    }

    // ==========================================
    // VECTOR OPERATIONS
    // ==========================================

    public float lengthSquared() {
        return this.x * this.x + this.y * this.y;
    }

    public float length() {
        return (float) Math.sqrt(lengthSquared());
    }

    public Vec2 negate() {
        return negate(this);
    }

    public Vec2 negate(Vec2 dest) {
        dest.x = -this.x;
        dest.y = -this.y;
        return dest;
    }

    public Vec2 normalize() {
        return normalize(this);
    }

    public Vec2 normalize(Vec2 dest) {
        float invLength = 1.0f / this.length();
        dest.x = this.x * invLength;
        dest.y = this.y * invLength;
        return dest;
    }

    public static float dot(Vec2 left, Vec2 right) {
        return left.x * right.x + left.y * right.y;
    }

    public static float angle(Vec2 a, Vec2 b) {
        float dls = dot(a, b) / (a.length() * b.length());
        if (dls < -1.0f) dls = -1.0f;
        else if (dls > 1.0f) dls = 1.0f;
        return (float) Math.acos(dls);
    }

    // ==========================================
    // MEMORY BUFFERING
    // ==========================================

    public Vec2 load(FloatBuffer buf) {
        this.x = buf.get();
        this.y = buf.get();
        return this;
    }

    public Vec2 store(FloatBuffer buf) {
        buf.put(this.x);
        buf.put(this.y);
        return this;
    }

    // ==========================================
    // UTILITIES
    // ==========================================

    public boolean isWithin(Vec2 min, Vec2 max) {
        return x >= min.x && x <= max.x && y >= min.y && y <= max.y;
    }

    @Override
    public String toString() {
        return "Vec2[" + this.x + ", " + this.y + ']';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || this.getClass() != obj.getClass()) return false;
        Vec2 other = (Vec2) obj;
        return this.x == other.x && this.y == other.y;
    }
}