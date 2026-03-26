package lang;

import java.nio.FloatBuffer;

/**
 * High-performance, independent 3D Vector.
 * Zero allocations, monomorphic dispatch.
 */
public final class Vec3 {
    public float x;
    public float y;
    public float z;

    public static final Vec3 xAxis = new Vec3(1, 0, 0);
    public static final Vec3 yAxis = new Vec3(0, 1, 0);
    public static final Vec3 zAxis = new Vec3(0, 0, 1);

    public Vec3() {}

    public Vec3(float xyz) {
        this.x = xyz;
        this.y = xyz;
        this.z = xyz;
    }

    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3(Vec3 src) {
        this.x = src.x;
        this.y = src.y;
        this.z = src.z;
    }

    public Vec3(Vec2 xy, float z) {
        this.x = xy.x;
        this.y = xy.y;
        this.z = z;
    }

    public Vec3 set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Vec3 set(Vec3 src) {
        this.x = src.x;
        this.y = src.y;
        this.z = src.z;
        return this;
    }

    // ==========================================
    // PRIMITIVE MATH (Fastest)
    // ==========================================

    public Vec3 translate(float x, float y, float z) {
        this.x += x;
        this.y += y;
        this.z += z;
        return this;
    }

    public Vec3 add(float x, float y, float z) {
        this.x += x;
        this.y += y;
        this.z += z;
        return this;
    }

    public Vec3 sub(float x, float y, float z) {
        this.x -= x;
        this.y -= y;
        this.z -= z;
        return this;
    }

    public Vec3 scale(float scale) {
        this.x *= scale;
        this.y *= scale;
        this.z *= scale;
        return this;
    }

    // ==========================================
    // OBJECT MATH (Zero Allocation)
    // ==========================================

    public Vec3 add(Vec3 right) {
        return add(this, right, this);
    }

    public static Vec3 add(Vec3 left, Vec3 right, Vec3 dest) {
        dest.x = left.x + right.x;
        dest.y = left.y + right.y;
        dest.z = left.z + right.z;
        return dest;
    }

    public Vec3 sub(Vec3 right) {
        return sub(this, right, this);
    }

    public static Vec3 sub(Vec3 left, Vec3 right, Vec3 dest) {
        dest.x = left.x - right.x;
        dest.y = left.y - right.y;
        dest.z = left.z - right.z;
        return dest;
    }

    public Vec3 cross(Vec3 right) {
        return cross(this, right, this);
    }

    public static Vec3 cross(Vec3 left, Vec3 right, Vec3 dest) {
        float lx = left.x, ly = left.y, lz = left.z;
        float rx = right.x, ry = right.y, rz = right.z;
        dest.x = ly * rz - lz * ry;
        dest.y = rz * lx - rx * lz;
        dest.z = lx * ry - ly * rx;
        return dest;
    }

    // ==========================================
    // VECTOR OPERATIONS
    // ==========================================

    public float lengthSquared() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    public float length() {
        return (float) Math.sqrt(lengthSquared());
    }

    public float lengthFrom(Vec3 location) {
        float dx = this.x - location.x;
        float dy = this.y - location.y;
        float dz = this.z - location.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public Vec3 negate() {
        return negate(this);
    }

    public Vec3 negate(Vec3 dest) {
        dest.x = -this.x;
        dest.y = -this.y;
        dest.z = -this.z;
        return dest;
    }

    public Vec3 normalize() {
        return normalize(this);
    }

    public Vec3 normalize(Vec3 dest) {
        float invLength = 1.0f / this.length();
        dest.x = this.x * invLength;
        dest.y = this.y * invLength;
        dest.z = this.z * invLength;
        return dest;
    }

    public static float dot(Vec3 left, Vec3 right) {
        return left.x * right.x + left.y * right.y + left.z * right.z;
    }

    public static float angle(Vec3 a, Vec3 b) {
        float dls = dot(a, b) / (a.length() * b.length());
        if (dls < -1.0f) dls = -1.0f;
        else if (dls > 1.0f) dls = 1.0f;
        return (float) Math.acos(dls);
    }

    // ==========================================
    // ROTATIONS (Using FastMath)
    // ==========================================

    public Vec3 rotateX(float angle) {
        return rotateX(angle, this);
    }

    public Vec3 rotateX(float angle, Vec3 dest) {
        float sin = FastMath.sin32(angle);
        float cos = FastMath.cos32(angle);
        float ny = this.y * cos - this.z * sin;
        float nz = this.y * sin + this.z * cos;
        dest.x = this.x;
        dest.y = ny;
        dest.z = nz;
        return dest;
    }

    public Vec3 rotateY(float angle) {
        return rotateY(angle, this);
    }

    public Vec3 rotateY(float angle, Vec3 dest) {
        float sin = FastMath.sin32(angle);
        float cos = FastMath.cos32(angle);
        float nx = this.x * cos + this.z * sin;
        float nz = -this.x * sin + this.z * cos;
        dest.x = nx;
        dest.y = this.y;
        dest.z = nz;
        return dest;
    }

    public Vec3 rotateZ(float angle) {
        return rotateZ(angle, this);
    }

    public Vec3 rotateZ(float angle, Vec3 dest) {
        float sin = FastMath.sin32(angle);
        float cos = FastMath.cos32(angle);
        float nx = this.x * cos - this.y * sin;
        float ny = this.x * sin + this.y * cos;
        dest.x = nx;
        dest.y = ny;
        dest.z = this.z;
        return dest;
    }

    // ==========================================
    // MEMORY BUFFERING
    // ==========================================

    public Vec3 load(FloatBuffer buf) {
        this.x = buf.get();
        this.y = buf.get();
        this.z = buf.get();
        return this;
    }

    public Vec3 store(FloatBuffer buf) {
        buf.put(this.x);
        buf.put(this.y);
        buf.put(this.z);
        return this;
    }

    // ==========================================
    // UTILITIES
    // ==========================================

    public boolean isWithin(Vec3 min, Vec3 max) {
        return x >= min.x && x <= max.x &&
                y >= min.y && y <= max.y &&
                z >= min.z && z <= max.z;
    }

    @Override
    public String toString() {
        return "Vec3[" + this.x + ", " + this.y + ", " + this.z + ']';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || this.getClass() != obj.getClass()) return false;
        Vec3 other = (Vec3) obj;
        return this.x == other.x && this.y == other.y && this.z == other.z;
    }
}