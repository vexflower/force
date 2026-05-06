package lang;

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

    // Creates a 3D Camera Lens
    // Creates a Left-Handed, Y-Up 3D Camera Lens mapped to Vulkan NDC
    public Mat4 perspective(float fov, float aspect, float zNear, float zFar) {
        float halfFovRad = (fov * GeomMath.DEG_TO_RAD) * 0.5f;

        float s = FastMath.sin32(halfFovRad);
        float c = FastMath.cos32(halfFovRad);
        float tanHalfFov = s / c;

        setZero();

        m00 = 1.0f / (aspect * tanHalfFov);
        m11 = -(1.0f / tanHalfFov);
        m22 = zFar / (zFar - zNear);
        m23 = 1.0f;
        m32 = -(zFar * zNear) / (zFar - zNear);

        return this;
    }

    // Transforms pixel coordinates into Vulkan Screen Space
    public Mat4 ortho(float left, float right, float bottom, float top, float zNear, float zFar) {
        identity();
        m00 = 2.0f / (right - left);
        m11 = 2.0f / (bottom - top);
        m22 = -2.0f / (zFar - zNear);

        m30 = -(right + left) / (right - left);
        m31 = -(bottom + top) / (bottom - top);
        m32 = -(zFar + zNear) / (zFar - zNear);
        return this;
    }


    public static void mul(Mat4 left, Mat4 right, Mat4 dest) {

        final float l00 = left.m00, l01 = left.m01, l02 = left.m02, l03 = left.m03;
        final float l10 = left.m10, l11 = left.m11, l12 = left.m12, l13 = left.m13;
        final float l20 = left.m20, l21 = left.m21, l22 = left.m22, l23 = left.m23;
        final float l30 = left.m30, l31 = left.m31, l32 = left.m32, l33 = left.m33;

        final float r00 = right.m00, r01 = right.m01, r02 = right.m02, r03 = right.m03;
        final float r10 = right.m10, r11 = right.m11, r12 = right.m12, r13 = right.m13;
        final float r20 = right.m20, r21 = right.m21, r22 = right.m22, r23 = right.m23;
        final float r30 = right.m30, r31 = right.m31, r32 = right.m32, r33 = right.m33;

        float m00_1 = l00 * r00;
        float m00_2 = l10 * r01;
        float m00_3 = l20 * r02;
        float m00_4 = l30 * r03;
        dest.m00 = (m00_1 + m00_2) + (m00_3 + m00_4);

        float m01_1 = l01 * r00;
        float m01_2 = l11 * r01;
        float m01_3 = l21 * r02;
        float m01_4 = l31 * r03;
        dest.m01 = (m01_1 + m01_2) + (m01_3 + m01_4);

        float m02_1 = l02 * r00;
        float m02_2 = l12 * r01;
        float m02_3 = l22 * r02;
        float m02_4 = l32 * r03;
        dest.m02 = (m02_1 + m02_2) + (m02_3 + m02_4);

        float m03_1 = l03 * r00;
        float m03_2 = l13 * r01;
        float m03_3 = l23 * r02;
        float m03_4 = l33 * r03;
        dest.m03 = (m03_1 + m03_2) + (m03_3 + m03_4);

        float m10_1 = l00 * r10;
        float m10_2 = l10 * r11;
        float m10_3 = l20 * r12;
        float m10_4 = l30 * r13;
        dest.m10 = (m10_1 + m10_2) + (m10_3 + m10_4);

        float m11_1 = l01 * r10;
        float m11_2 = l11 * r11;
        float m11_3 = l21 * r12;
        float m11_4 = l31 * r13;
        dest.m11 = (m11_1 + m11_2) + (m11_3 + m11_4);

        float m12_1 = l02 * r10;
        float m12_2 = l12 * r11;
        float m12_3 = l22 * r12;
        float m12_4 = l32 * r13;
        dest.m12 = (m12_1 + m12_2) + (m12_3 + m12_4);

        float m13_1 = l03 * r10;
        float m13_2 = l13 * r11;
        float m13_3 = l23 * r12;
        float m13_4 = l33 * r13;
        dest.m13 = (m13_1 + m13_2) + (m13_3 + m13_4);

        float m20_1 = l00 * r20;
        float m20_2 = l10 * r21;
        float m20_3 = l20 * r22;
        float m20_4 = l30 * r23;
        dest.m20 = (m20_1 + m20_2) + (m20_3 + m20_4);

        float m21_1 = l01 * r20;
        float m21_2 = l11 * r21;
        float m21_3 = l21 * r22;
        float m21_4 = l31 * r23;
        dest.m21 = (m21_1 + m21_2) + (m21_3 + m21_4);

        float m22_1 = l02 * r20;
        float m22_2 = l12 * r21;
        float m22_3 = l22 * r22;
        float m22_4 = l32 * r23;
        dest.m22 = (m22_1 + m22_2) + (m22_3 + m22_4);

        float m23_1 = l03 * r20;
        float m23_2 = l13 * r21;
        float m23_3 = l23 * r22;
        float m23_4 = l33 * r23;
        dest.m23 = (m23_1 + m23_2) + (m23_3 + m23_4);

        float m30_1 = l00 * r30;
        float m30_2 = l10 * r31;
        float m30_3 = l20 * r32;
        float m30_4 = l30 * r33;
        dest.m30 = (m30_1 + m30_2) + (m30_3 + m30_4);

        float m31_1 = l01 * r30;
        float m31_2 = l11 * r31;
        float m31_3 = l21 * r32;
        float m31_4 = l31 * r33;
        dest.m31 = (m31_1 + m31_2) + (m31_3 + m31_4);

        float m32_1 = l02 * r30;
        float m32_2 = l12 * r31;
        float m32_3 = l22 * r32;
        float m32_4 = l32 * r33;
        dest.m32 = (m32_1 + m32_2) + (m32_3 + m32_4);

        float m33_1 = l03 * r30;
        float m33_2 = l13 * r31;
        float m33_3 = l23 * r32;
        float m33_4 = l33 * r33;
        dest.m33 = (m33_1 + m33_2) + (m33_3 + m33_4);
    }

    public Mat4 mul(Mat4 right, Mat4 dest) {
        mul(this, right, dest);
        return dest;
    }

    public Mat4 mul(Mat4 right) {

        float m00_p1 = this.m00 * right.m00 + this.m01 * right.m10;
        float m00_p2 = this.m02 * right.m20 + this.m03 * right.m30;
        float nm00 = m00_p1 + m00_p2;

        float m01_p1 = this.m00 * right.m01 + this.m01 * right.m11;
        float m01_p2 = this.m02 * right.m21 + this.m03 * right.m31;
        float nm01 = m01_p1 + m01_p2;

        float m02_p1 = this.m00 * right.m02 + this.m01 * right.m12;
        float m02_p2 = this.m02 * right.m22 + this.m03 * right.m32;
        float nm02 = m02_p1 + m02_p2;

        float m03_p1 = this.m00 * right.m03 + this.m01 * right.m13;
        float m03_p2 = this.m02 * right.m23 + this.m03 * right.m33;
        float nm03 = m03_p1 + m03_p2;

        float m10_p1 = this.m10 * right.m00 + this.m11 * right.m10;
        float m10_p2 = this.m12 * right.m20 + this.m13 * right.m30;
        float nm10 = m10_p1 + m10_p2;

        float m11_p1 = this.m10 * right.m01 + this.m11 * right.m11;
        float m11_p2 = this.m12 * right.m21 + this.m13 * right.m31;
        float nm11 = m11_p1 + m11_p2;

        float m12_p1 = this.m10 * right.m02 + this.m11 * right.m12;
        float m12_p2 = this.m12 * right.m22 + this.m13 * right.m32;
        float nm12 = m12_p1 + m12_p2;

        float m13_p1 = this.m10 * right.m03 + this.m11 * right.m13;
        float m13_p2 = this.m12 * right.m23 + this.m13 * right.m33;
        float nm13 = m13_p1 + m13_p2;

        float m20_p1 = this.m20 * right.m00 + this.m21 * right.m10;
        float m20_p2 = this.m22 * right.m20 + this.m23 * right.m30;
        float nm20 = m20_p1 + m20_p2;

        float m21_p1 = this.m20 * right.m01 + this.m21 * right.m11;
        float m21_p2 = this.m22 * right.m21 + this.m23 * right.m31;
        float nm21 = m21_p1 + m21_p2;

        float m22_p1 = this.m20 * right.m02 + this.m21 * right.m12;
        float m22_p2 = this.m22 * right.m22 + this.m23 * right.m32;
        float nm22 = m22_p1 + m22_p2;

        float m23_p1 = this.m20 * right.m03 + this.m21 * right.m13;
        float m23_p2 = this.m22 * right.m23 + this.m23 * right.m33;
        float nm23 = m23_p1 + m23_p2;

        float m30_p1 = this.m30 * right.m00 + this.m31 * right.m10;
        float m30_p2 = this.m32 * right.m20 + this.m33 * right.m30;
        float nm30 = m30_p1 + m30_p2;

        float m31_p1 = this.m30 * right.m01 + this.m31 * right.m11;
        float m31_p2 = this.m32 * right.m21 + this.m33 * right.m31;
        float nm31 = m31_p1 + m31_p2;

        float m32_p1 = this.m30 * right.m02 + this.m31 * right.m12;
        float m32_p2 = this.m32 * right.m22 + this.m33 * right.m32;
        float nm32 = m32_p1 + m32_p2;

        float m33_p1 = this.m30 * right.m03 + this.m31 * right.m13;
        float m33_p2 = this.m32 * right.m23 + this.m33 * right.m33;
        float nm33 = m33_p1 + m33_p2;

        this.m00 = nm00; this.m01 = nm01; this.m02 = nm02; this.m03 = nm03;
        this.m10 = nm10; this.m11 = nm11; this.m12 = nm12; this.m13 = nm13;
        this.m20 = nm20; this.m21 = nm21; this.m22 = nm22; this.m23 = nm23;
        this.m30 = nm30; this.m31 = nm31; this.m32 = nm32; this.m33 = nm33;

        return this;
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
    // DIRECT NATIVE C-POINTER UPLOAD (Zero GC)
    // ========================================================================
    public void store(long ptr) {
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr,      m00);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 4,  m01);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 8,  m02);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 12, m03);

        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 16, m10);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 20, m11);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 24, m12);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 28, m13);

        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 32, m20);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 36, m21);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 40, m22);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 44, m23);

        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 48, m30);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 52, m31);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 56, m32);
        org.lwjgl.system.MemoryUtil.memPutFloat(ptr + 60, m33);
    }

    // Zero-allocation dump straight into a primitive float array
    // ... [Inside Mat4.java, add this:] ...
    public Mat4 store(util.CFloatList list) {
        list.add(m00); list.add(m01); list.add(m02); list.add(m03);
        list.add(m10); list.add(m11); list.add(m12); list.add(m13);
        list.add(m20); list.add(m21); list.add(m22); list.add(m23);
        list.add(m30); list.add(m31); list.add(m32); list.add(m33);
        return this;
    }

    @Override
    public String toString() {
        return String.format("%f %f %f %f\n%f %f %f %f\n%f %f %f %f\n%f %f %f %f\n",
                m00, m10, m20, m30, m01, m11, m21, m31, m02, m12, m22, m32, m03, m13, m23, m33);
    }
}