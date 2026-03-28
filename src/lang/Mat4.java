package lang;

import util.FloatList;

import java.nio.FloatBuffer;

/**
 * High-Performance, Zero-Allocation 4x4 Matrix.
 * Optimized for LWJGL/Vulkan/OpenGL.
 */
public class Mat4 {

    public float m00, m01, m02, m03;
    public float m10, m11, m12, m13;
    public float m20, m21, m22, m23;
    public float m30, m31, m32, m33;

    public Mat4() {
        identity();
    }

    public Mat4(Mat4 src) {
        load(src);
    }

    public Mat4 identity() {
        m00 = 1.0f; m01 = 0.0f; m02 = 0.0f; m03 = 0.0f;
        m10 = 0.0f; m11 = 1.0f; m12 = 0.0f; m13 = 0.0f;
        m20 = 0.0f; m21 = 0.0f; m22 = 1.0f; m23 = 0.0f;
        m30 = 0.0f; m31 = 0.0f; m32 = 0.0f; m33 = 1.0f;
        return this;
    }

    public Mat4 setZero() {
        m00 = 0.0f; m01 = 0.0f; m02 = 0.0f; m03 = 0.0f;
        m10 = 0.0f; m11 = 0.0f; m12 = 0.0f; m13 = 0.0f;
        m20 = 0.0f; m21 = 0.0f; m22 = 0.0f; m23 = 0.0f;
        m30 = 0.0f; m31 = 0.0f; m32 = 0.0f; m33 = 0.0f;
        return this;
    }

    public Mat4 load(Mat4 src) {
        m00 = src.m00; m01 = src.m01; m02 = src.m02; m03 = src.m03;
        m10 = src.m10; m11 = src.m11; m12 = src.m12; m13 = src.m13;
        m20 = src.m20; m21 = src.m21; m22 = src.m22; m23 = src.m23;
        m30 = src.m30; m31 = src.m31; m32 = src.m32; m33 = src.m33;
        return this;
    }

    // ========================================================================
    // STATIC ENGINE ROOM (The Absolute Fastest Path)
    // ========================================================================

    public static void mul(Mat4 left, Mat4 right, Mat4 dest) {
        float l00 = left.m00, l01 = left.m01, l02 = left.m02, l03 = left.m03;
        float l10 = left.m10, l11 = left.m11, l12 = left.m12, l13 = left.m13;
        float l20 = left.m20, l21 = left.m21, l22 = left.m22, l23 = left.m23;
        float l30 = left.m30, l31 = left.m31, l32 = left.m32, l33 = left.m33;

        float r00 = right.m00, r01 = right.m01, r02 = right.m02, r03 = right.m03;
        float r10 = right.m10, r11 = right.m11, r12 = right.m12, r13 = right.m13;
        float r20 = right.m20, r21 = right.m21, r22 = right.m22, r23 = right.m23;
        float r30 = right.m30, r31 = right.m31, r32 = right.m32, r33 = right.m33;

        dest.m00 = l00 * r00 + l10 * r01 + l20 * r02 + l30 * r03;
        dest.m01 = l01 * r00 + l11 * r01 + l21 * r02 + l31 * r03;
        dest.m02 = l02 * r00 + l12 * r01 + l22 * r02 + l32 * r03;
        dest.m03 = l03 * r00 + l13 * r01 + l23 * r02 + l33 * r03;

        dest.m10 = l00 * r10 + l10 * r11 + l20 * r12 + l30 * r13;
        dest.m11 = l01 * r10 + l11 * r11 + l21 * r12 + l31 * r13;
        dest.m12 = l02 * r10 + l12 * r11 + l22 * r12 + l32 * r13;
        dest.m13 = l03 * r10 + l13 * r11 + l23 * r12 + l33 * r13;

        dest.m20 = l00 * r20 + l10 * r21 + l20 * r22 + l30 * r23;
        dest.m21 = l01 * r20 + l11 * r21 + l21 * r22 + l31 * r23;
        dest.m22 = l02 * r20 + l12 * r21 + l22 * r22 + l32 * r23;
        dest.m23 = l03 * r20 + l13 * r21 + l23 * r22 + l33 * r23;

        dest.m30 = l00 * r30 + l10 * r31 + l20 * r32 + l30 * r33;
        dest.m31 = l01 * r30 + l11 * r31 + l21 * r32 + l31 * r33;
        dest.m32 = l02 * r30 + l12 * r31 + l22 * r32 + l32 * r33;
        dest.m33 = l03 * r30 + l13 * r31 + l23 * r32 + l33 * r33;
    }

    // ========================================================================
    // INSTANCE WRAPPERS
    // ========================================================================

    public Mat4 mul(Mat4 right) {
        mul(this, right, this);
        return this;
    }

    public Mat4 mul(Mat4 right, Mat4 dest) {
        mul(this, right, dest);
        return dest;
    }

    public Mat4 translate(float x, float y, float z) {
        m30 += m00 * x + m10 * y + m20 * z;
        m31 += m01 * x + m11 * y + m21 * z;
        m32 += m02 * x + m12 * y + m22 * z;
        m33 += m03 * x + m13 * y + m23 * z;
        return this;
    }

    // Note: Changed angle parameter from 'double' to 'float' to prevent casting overhead
    public Mat4 rotate(float angle, float x, float y, float z) {
        return rotate(angle, x, y, z, this);
    }

    public Mat4 rotate(float angle, float x, float y, float z, Mat4 dest) {
        // USE FASTMATH LUT HERE
        float c = FastMath.cos32(angle);
        float s = FastMath.sin32(angle);
        float omc = 1.0f - c;

        float xy = x * y, yz = y * z, xz = x * z;
        float xs = x * s, ys = y * s, zs = z * s;

        float f00 = x * x * omc + c;
        float f01 = xy * omc + zs;
        float f02 = xz * omc - ys;
        float f10 = xy * omc - zs;
        float f11 = y * y * omc + c;
        float f12 = yz * omc + xs;
        float f20 = xz * omc + ys;
        float f21 = yz * omc - xs;
        float f22 = z * z * omc + c;

        float t00 = m00, t01 = m01, t02 = m02, t03 = m03;
        float t10 = m10, t11 = m11, t12 = m12, t13 = m13;
        float t20 = m20, t21 = m21, t22 = m22, t23 = m23;

        dest.m00 = t00 * f00 + t10 * f01 + t20 * f02;
        dest.m01 = t01 * f00 + t11 * f01 + t21 * f02;
        dest.m02 = t02 * f00 + t12 * f01 + t22 * f02;
        dest.m03 = t03 * f00 + t13 * f01 + t23 * f02;

        dest.m10 = t00 * f10 + t10 * f11 + t20 * f12;
        dest.m11 = t01 * f10 + t11 * f11 + t21 * f12;
        dest.m12 = t02 * f10 + t12 * f11 + t22 * f12;
        dest.m13 = t03 * f10 + t13 * f11 + t23 * f12;

        dest.m20 = t00 * f20 + t10 * f21 + t20 * f22;
        dest.m21 = t01 * f20 + t11 * f21 + t21 * f22;
        dest.m22 = t02 * f20 + t12 * f21 + t22 * f22;
        dest.m23 = t03 * f20 + t13 * f21 + t23 * f22;

        if (this != dest) {
            dest.m30 = m30; dest.m31 = m31; dest.m32 = m32; dest.m33 = m33;
        }
        return dest;
    }

    public Mat4 scale(float x, float y, float z) {
        m00 *= x; m01 *= x; m02 *= x; m03 *= x;
        m10 *= y; m11 *= y; m12 *= y; m13 *= y;
        m20 *= z; m21 *= z; m22 *= z; m23 *= z;
        return this;
    }

    public Mat4 rotateX(float angle) {
        float c = FastMath.cos32(angle);
        float s = FastMath.sin32(angle);
        float rm10 = m10, rm11 = m11, rm12 = m12, rm13 = m13;
        float rm20 = m20, rm21 = m21, rm22 = m22, rm23 = m23;

        m10 = rm10 * c + rm20 * s;
        m11 = rm11 * c + rm21 * s;
        m12 = rm12 * c + rm22 * s;
        m13 = rm13 * c + rm23 * s;
        m20 = rm10 * -s + rm20 * c;
        m21 = rm11 * -s + rm21 * c;
        m22 = rm12 * -s + rm22 * c;
        m23 = rm13 * -s + rm23 * c;
        return this;
    }

    public Mat4 rotateY(float angle) {
        float c = FastMath.cos32(angle);
        float s = FastMath.sin32(angle);
        float rm00 = m00, rm01 = m01, rm02 = m02, rm03 = m03;
        float rm20 = m20, rm21 = m21, rm22 = m22, rm23 = m23;

        m00 = rm00 * c + rm20 * -s;
        m01 = rm01 * c + rm21 * -s;
        m02 = rm02 * c + rm22 * -s;
        m03 = rm03 * c + rm23 * -s;
        m20 = rm00 * s + rm20 * c;
        m21 = rm01 * s + rm21 * c;
        m22 = rm02 * s + rm22 * c;
        m23 = rm03 * s + rm23 * c;
        return this;
    }

    public Mat4 rotateZ(float angle) {
        float c = FastMath.cos32(angle);
        float s = FastMath.sin32(angle);
        float rm00 = m00, rm01 = m01, rm02 = m02, rm03 = m03;
        float rm10 = m10, rm11 = m11, rm12 = m12, rm13 = m13;

        m00 = rm00 * c + rm10 * s;
        m01 = rm01 * c + rm11 * s;
        m02 = rm02 * c + rm12 * s;
        m03 = rm03 * c + rm13 * s;
        m10 = rm00 * -s + rm10 * c;
        m11 = rm01 * -s + rm11 * c;
        m12 = rm02 * -s + rm12 * c;
        m13 = rm03 * -s + rm13 * c;
        return this;
    }

    // ========================================================================
    // BUFFER STREAMING
    // ========================================================================

    public Mat4 store(FloatBuffer buf) {
        buf.put(m00); buf.put(m01); buf.put(m02); buf.put(m03);
        buf.put(m10); buf.put(m11); buf.put(m12); buf.put(m13);
        buf.put(m20); buf.put(m21); buf.put(m22); buf.put(m23);
        buf.put(m30); buf.put(m31); buf.put(m32); buf.put(m33);
        return this;
    }

    // ========================================================================
    // [CHANGED: ZERO-ALLOCATION SCENE DUMPING]
    // ========================================================================
    public Mat4 storeIntoFloatList(FloatList list, int offset) {
        list.set(offset, m00); list.set(offset + 1, m01); list.set(offset + 2, m02); list.set(offset + 3, m03);
        list.set(offset + 4, m10); list.set(offset + 5, m11); list.set(offset + 6, m12); list.set(offset + 7, m13);
        list.set(offset + 8, m20); list.set(offset + 9, m21); list.set(offset + 10, m22); list.set(offset + 11, m23);
        list.set(offset + 12, m30); list.set(offset + 13, m31); list.set(offset + 14, m32); list.set(offset + 15, m33);
        return this;
    }

    @Override
    public String toString() {
        return String.format("%f %f %f %f\n%f %f %f %f\n%f %f %f %f\n%f %f %f %f\n",
                m00, m10, m20, m30, m01, m11, m21, m31, m02, m12, m22, m32, m03, m13, m23, m33);
    }
}