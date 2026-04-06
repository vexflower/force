package lang;

/**
 * High-performance Geometry and Matrix Math utility.
 * Completely unrolled algebraic matrix calculations for zero-allocation and max speed.
 * Replaces chained Matrix multiplications with direct assignment.
 */
public final class GeomMath {

    // =========================================================================================
    // PRECALCULATED CONSTANTS
    // =========================================================================================
    public static final float PI = 3.1415927f;
    public static final float HALF_PI = 1.5707964f;
    public static final float TWO_PI = 6.2831855f;
    public static final float EPSILON = 0.000002f;

    // Precalculated multipliers to convert without division
    public static final float DEG_TO_RAD = 0.0174532925f; // PI / 180.0f
    public static final float RAD_TO_DEG = 57.2957795f;   // 180.0f / PI

    /**
     * Prevent instantiation
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

        float r00 = cy * cz;
        float r01 = cy * sz;
        float r02 = -sy;

        float r10 = sx * sy * cz - cx * sz;
        float r11 = sx * sy * sz + cx * cz;
        float r12 = sx * cy;

        float r20 = cx * sy * cz + sx * sz;
        float r21 = cx * sy * sz - sx * cz;
        float r22 = cx * cy;

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

    public static Mat4 createViewMatrix(float posX, float posY, float posZ, float pitch, float yaw, float roll, Mat4 dest) {
        if (dest == null) dest = new Mat4();

        float pitchRad = pitch * DEG_TO_RAD;
        float yawRad = yaw * DEG_TO_RAD;

        float cp = FastMath.cos32(pitchRad);
        float sp = FastMath.sin32(pitchRad);
        float cy = FastMath.cos32(yawRad);
        float sy = FastMath.sin32(yawRad);

        // Left-Handed FPS View Matrix (Locks the Up Vector so you can't roll)
        float m00 = cy;             float m01 = sy * sp;      float m02 = sy * cp;
        float m10 = 0.0f;           float m11 = cp;           float m12 = -sp;
        float m20 = -sy;            float m21 = cy * sp;      float m22 = cy * cp;

        dest.m00 = m00; dest.m01 = m01; dest.m02 = m02; dest.m03 = 0.0f;
        dest.m10 = m10; dest.m11 = m11; dest.m12 = m12; dest.m13 = 0.0f;
        dest.m20 = m20; dest.m21 = m21; dest.m22 = m22; dest.m23 = 0.0f;

        // Translate the world opposite to the camera's position
        dest.m30 = -(m00 * posX + m10 * posY + m20 * posZ);
        dest.m31 = -(m01 * posX + m11 * posY + m21 * posZ);
        dest.m32 = -(m02 * posX + m12 * posY + m22 * posZ);
        dest.m33 = 1.0f;

        return dest;
    }

    // =========================================================================================
    // BASIC ARITHMETIC & TRIGONOMETRY WRAPPERS
    // =========================================================================================

    public static float toRadians(float degrees) {
        return degrees * DEG_TO_RAD;
    }

    public static float toDegrees(float radians) {
        return radians * RAD_TO_DEG;
    }

    public static int abs(int n) {
        return (n ^ (n >> 31)) - (n >> 31);
    }

    public static float abs(float value) {
        return Float.intBitsToFloat(Float.floatToIntBits(value) & 0x7fffffff);
    }

    public static float pow(float base, float exponent) {
        return (float) Math.pow(base, exponent);
    }

    public static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    public static float cosFromSin(float sin, float angle) {
        int quadrant = sin >= 0 ? (angle >= 0 ? 1 : 4) : (angle >= 0 ? 2 : 3);
        float cosSquared = 1 - sin * sin;
        return (float) (Math.sqrt(cosSquared) * (quadrant == 1 || quadrant == 4 ? 1 : -1));
    }

    public static double sin(double angle) {
        return FastMath.sin32((float) angle);
    }

    public static float sin(float angle) {
        return FastMath.sin32(angle);
    }

    public static float cos(float angle) {
        return FastMath.cos32(angle);
    }

    // =========================================================================================
    // ADVANCED 3D SPACE & GEOMETRY ALGORITHMS
    // =========================================================================================

    /**
     * Fast Inverse Square Root (The Quake III Algorithm).
     */
    public static float invSqrt(float x) {
        float halfx = 0.5f * x;
        int i = Float.floatToIntBits(x);
        i = 0x5f3759df - (i >> 1);
        x = Float.intBitsToFloat(i);
        x = x * (1.5f - (halfx * x * x));
        return x;
    }

    /**
     * Calculates the height interpolation of a 2D point on a 3D triangle.
     * Excellent for terrain height generation.
     */
    public static float barryCentric(Vec3 p1, Vec3 p2, Vec3 p3, Vec2 pos) {
        float det = (p2.z - p3.z) * (p1.x - p3.x) + (p3.x - p2.x) * (p1.z - p3.z);
        float l1 = ((p2.z - p3.z) * (pos.x - p3.x) + (p3.x - p2.x) * (pos.y - p3.z)) / det;
        float l2 = ((p3.z - p1.z) * (pos.x - p3.x) + (p1.x - p3.x) * (pos.y - p3.z)) / det;
        float l3 = 1.0f - l1 - l2;
        return l1 * p1.y + l2 * p2.y + l3 * p3.y;
    }

    /**
     * Calculates the Barycentric coordinates (u, v, w) of a 3D point P relative to triangle ABC.
     * Brutally unrolled for ZERO allocations. Ugly, but infinitely faster.
     * @param dest The Vec3 to store the (u, v, w) weights.
     */
    public static void barycentric(Vec3 a, Vec3 b, Vec3 c, Vec3 p, Vec3 dest) {

        // 1. Unroll v0 = b - a
        float v0x = b.x - a.x;
        float v0y = b.y - a.y;
        float v0z = b.z - a.z;

        // 2. Unroll v1 = c - a
        float v1x = c.x - a.x;
        float v1y = c.y - a.y;
        float v1z = c.z - a.z;

        // 3. Unroll v2 = p - a
        float v2x = p.x - a.x;
        float v2y = p.y - a.y;
        float v2z = p.z - a.z;

        // 4. Unroll Dot Products
        float d00 = (v0x * v0x) + (v0y * v0y) + (v0z * v0z);
        float d01 = (v0x * v1x) + (v0y * v1y) + (v0z * v1z);
        float d11 = (v1x * v1x) + (v1y * v1y) + (v1z * v1z);
        float d20 = (v2x * v0x) + (v2y * v0y) + (v2z * v0z);
        float d21 = (v2x * v1x) + (v2y * v1y) + (v2z * v1z);

        // 5. Calculate Denominator
        float denominator = (d00 * d11) - (d01 * d01);

        // Prevent division by zero if triangle is degenerate
        if (denominator > -EPSILON && denominator < EPSILON) {
            dest.x = 0.0f;
            dest.y = 0.0f;
            dest.z = 0.0f;
            return;
        }

        // 6. Calculate weights (using inverse multiplication instead of double division)
        float invDenominator = 1.0f / denominator;
        float v = ((d11 * d20) - (d01 * d21)) * invDenominator;
        float w = ((d00 * d21) - (d01 * d20)) * invDenominator;
        float u = 1.0f - v - w;

        // 7. Direct assignment to destination
        dest.x = u;
        dest.y = v;
        dest.z = w;
    }

    /**
     * Möller–Trumbore Ray-Triangle Intersection.
     * @return the distance 't' along the ray where the intersection occurs. Returns -1 if no hit.
     */
    public static float intersectRayTriangle(Vec3 rayOrigin, Vec3 rayDir, Vec3 v0, Vec3 v1, Vec3 v2) {
        float edge1X = v1.x - v0.x;
        float edge1Y = v1.y - v0.y;
        float edge1Z = v1.z - v0.z;

        float edge2X = v2.x - v0.x;
        float edge2Y = v2.y - v0.y;
        float edge2Z = v2.z - v0.z;

        float hX = rayDir.y * edge2Z - rayDir.z * edge2Y;
        float hY = rayDir.z * edge2X - rayDir.x * edge2Z;
        float hZ = rayDir.x * edge2Y - rayDir.y * edge2X;

        float det = edge1X * hX + edge1Y * hY + edge1Z * hZ;

        if (det > -EPSILON && det < EPSILON) {
            return -1f;
        }

        float invDet = 1.0f / det;

        float sX = rayOrigin.x - v0.x;
        float sY = rayOrigin.y - v0.y;
        float sZ = rayOrigin.z - v0.z;

        float u = (sX * hX + sY * hY + sZ * hZ) * invDet;
        if (u < 0.0f || u > 1.0f) {
            return -1f;
        }

        float qX = sY * edge1Z - sZ * edge1Y;
        float qY = sZ * edge1X - sX * edge1Z;
        float qZ = sX * edge1Y - sY * edge1X;

        float v = (rayDir.x * qX + rayDir.y * qY + rayDir.z * qZ) * invDet;
        if (v < 0.0f || u + v > 1.0f) {
            return -1f;
        }

        float t = (edge2X * qX + edge2Y * qY + edge2Z * qZ) * invDet;

        if (t > EPSILON) {
            return t;
        }

        return -1f;
    }
}