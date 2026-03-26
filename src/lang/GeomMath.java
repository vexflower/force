package lang;

/**
 * High-performance Geometry and Matrix Math utility.
 * Completely unrolled algebraic matrix calculations for zero-allocation and max speed.
 * Replaces chained Matrix multiplications with direct assignment.
 */
public final class GeomMath {

    // Precalculated multiplier to convert degrees to radians without division
    private static final float DEG_TO_RAD = 0.0174532925f;

    /**
     * No one uses this constructor
     */
    private GeomMath() {}

    // =========================================================================================
    // 3D TRANSFORMATION MATRICES (Unrolled T * R * S)
    // =========================================================================================

    public static Mat4 createTransformationMatrix(Vec3 position, Vec3 rotation, float scale, Mat4 dest) {
        return createTransformationMatrix(
                position.x, position.y, position.z,
                rotation.x, rotation.y, rotation.z,
                scale, scale, scale,
                dest
        );
    }

    public static Mat4 createTransformationMatrix(Vec2 position, Vec3 rotation, Vec2 scale, Mat4 dest) {
        return createTransformationMatrix(
                position.x, position.y, 0.0f,
                rotation.x, rotation.y, rotation.z,
                scale.x, scale.y, 1.0f,
                dest
        );
    }

    /**
     * The holy grail of matrix optimization.
     * Instead of multiplying Identity * Translate * RotX * RotY * RotZ * Scale,
     * we algebraically calculate the final 16 floats and write them directly.
     * Reduces ~150 multiplications down to 21.
     */
    public static Mat4 createTransformationMatrix(
            float posX, float posY, float posZ,
            float rotX, float rotY, float rotZ,
            float scaleX, float scaleY, float scaleZ,
            Mat4 dest) {

        if (dest == null) dest = new Mat4(); // Fallback, but ideally caller provides dest

        // 1. Convert degrees to radians
        float rx = rotX * DEG_TO_RAD;
        float ry = rotY * DEG_TO_RAD;
        float rz = rotZ * DEG_TO_RAD;

        // 2. Fetch fast Sine/Cosine
        float cx = FastMath.cos32(rx);
        float sx = FastMath.sin32(rx);
        float cy = FastMath.cos32(ry);
        float sy = FastMath.sin32(ry);
        float cz = FastMath.cos32(rz);
        float sz = FastMath.sin32(rz);

        // 3. Compute Rotation Matrix (R = Rx * Ry * Rz)
        float r00 = cy * cz;
        float r01 = cy * sz;
        float r02 = -sy;

        float r10 = sx * sy * cz - cx * sz;
        float r11 = sx * sy * sz + cx * cz;
        float r12 = sx * cy;

        float r20 = cx * sy * cz + sx * sz;
        float r21 = cx * sy * sz - sx * cz;
        float r22 = cx * cy;

        // 4. Scale and write rotation to the first 3 columns
        dest.m00 = r00 * scaleX;
        dest.m01 = r01 * scaleX;
        dest.m02 = r02 * scaleX;
        dest.m03 = 0.0f;

        dest.m10 = r10 * scaleY;
        dest.m11 = r11 * scaleY;
        dest.m12 = r12 * scaleY;
        dest.m13 = 0.0f;

        dest.m20 = r20 * scaleZ;
        dest.m21 = r21 * scaleZ;
        dest.m22 = r22 * scaleZ;
        dest.m23 = 0.0f;

        // 5. Write translation directly into the 4th column
        dest.m30 = posX;
        dest.m31 = posY;
        dest.m32 = posZ;
        dest.m33 = 1.0f;

        return dest;
    }

    // =========================================================================================
    // 2D TRANSFORMATION MATRICES
    // =========================================================================================

    public static Mat4 createTransformationMatrix(float posX, float posY, float rotX, float rotY, float rotZ, float scaleX, float scaleY, Mat4 dest) {
        return createTransformationMatrix(posX, posY, 0.0f, rotX, rotY, rotZ, scaleX, scaleY, 1.0f, dest);
    }

    public static Mat4 createTransformationMatrix(Vec2 translation, Vec2 scale, Mat4 dest) {
        if (dest == null) dest = new Mat4();

        // Pure 2D matrix unrolling (No rotation)
        dest.setZero();
        dest.m00 = scale.x;
        dest.m11 = scale.y;
        dest.m22 = 1.0f;
        dest.m33 = 1.0f;
        dest.m30 = translation.x;
        dest.m31 = translation.y;
        return dest;
    }

    // =========================================================================================
    // VIEW MATRIX (Unrolled R * -T)
    // =========================================================================================

    /**
     * Unrolled View Matrix Calculation.
     * Note: I removed the unused 'src' parameter from your original code.
     */
    public static Mat4 createViewMatrix(float posX, float posY, float posZ, float rotX, float rotY, float rotZ, Mat4 dest) {
        if (dest == null) dest = new Mat4();

        float rx = rotX * DEG_TO_RAD;
        float ry = rotY * DEG_TO_RAD;
        float rz = rotZ * DEG_TO_RAD;

        float cx = FastMath.cos32(rx);
        float sx = FastMath.sin32(rx);
        float cy = FastMath.cos32(ry);
        float sy = FastMath.sin32(ry);
        float cz = FastMath.cos32(rz);
        float sz = FastMath.sin32(rz);

        // View matrix rotations (Rx * Ry * Rz)
        float r00 = cy * cz;
        float r01 = cy * sz;
        float r02 = -sy;

        float r10 = sx * sy * cz - cx * sz;
        float r11 = sx * sy * sz + cx * cz;
        float r12 = sx * cy;

        float r20 = cx * sy * cz + sx * sz;
        float r21 = cx * sy * sz - sx * cz;
        float r22 = cx * cy;

        // Write rotation directly
        dest.m00 = r00; dest.m01 = r01; dest.m02 = r02; dest.m03 = 0.0f;
        dest.m10 = r10; dest.m11 = r11; dest.m12 = r12; dest.m13 = 0.0f;
        dest.m20 = r20; dest.m21 = r21; dest.m22 = r22; dest.m23 = 0.0f;

        // Apply negative translation using the rotation components (Dot product)
        dest.m30 = r00 * (-posX) + r10 * (-posY) + r20 * (-posZ);
        dest.m31 = r01 * (-posX) + r11 * (-posY) + r21 * (-posZ);
        dest.m32 = r02 * (-posX) + r12 * (-posY) + r22 * (-posZ);
        dest.m33 = 1.0f;

        return dest;
    }

    // =========================================================================================
    // GEOMETRY & MATH WRAPPERS
    // =========================================================================================

    /**
     * Calculates the interpolation of a point within a triangle using barycentric coordinates.
     */
    public static float barryCentric(Vec3 p1, Vec3 p2, Vec3 p3, Vec2 pos) {
        float det = (p2.z - p3.z) * (p1.x - p3.x) + (p3.x - p2.x) * (p1.z - p3.z);
        float l1 = ((p2.z - p3.z) * (pos.x - p3.x) + (p3.x - p2.x) * (pos.y - p3.z)) / det;
        float l2 = ((p3.z - p1.z) * (pos.x - p3.x) + (p1.x - p3.x) * (pos.y - p3.z)) / det;
        float l3 = 1.0f - l1 - l2;
        return l1 * p1.y + l2 * p2.y + l3 * p3.y;
    }

    public static int abs(int n) {
        // Bitwise absolute value trick
        return (n ^ (n >> 31)) - (n >> 31);
    }

    public static float cosFromSin(float sin, float angle) {
        int quadrant = sin >= 0 ? (angle >= 0 ? 1 : 4) : (angle >= 0 ? 2 : 3);
        float cosSquared = 1 - sin * sin;
        float cos = (float) (Math.sqrt(cosSquared) * (quadrant == 1 || quadrant == 4 ? 1 : -1));
        return cos;
    }

    // Overridden to route everything to our high-performance 32-bit math
    public static double sin(double angle) {
        return FastMath.sin32((float) angle);
    }

    public static float sin(float angle) {
        return FastMath.sin32(angle);
    }

    public static float cos(float angle) {
        return FastMath.cos32(angle);
    }
}