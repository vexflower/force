package lang;
/**
 * High-performance, 32-bit polynomial math approximations.
 * Replaces Lookup Tables (LUTs) to avoid L1 Cache Misses.
 * Uses the Bhaskara I approximation with an extra precision pass.
 */
public final class FastMath {

    public static final float PI = 3.1415927f;
    public static final float HALF_PI = 1.5707964f;
    public static final float PI2 = 6.2831855f;
    private static final float INV_PI2 = 0.15915494f;

    // Polynomial constants
    private static final float B = 1.27323954f;  // 4 / PI
    private static final float C = -0.40528473f; // -4 / (PI^2)
    private static final float P = 0.225f;       // Precision weight

    // Precalculated multipliers to convert without division
    public static final float DEG_TO_RAD = 0.0174532925f; // PI / 180.0f
    public static final float RAD_TO_DEG = 57.2957795f;   // 180.0f / PI


    private FastMath() {}

    /**
     * Branchless Absolute Value
     */
    public static float abs(float x) {
        return Float.intBitsToFloat(Float.floatToRawIntBits(x) & 0x7FFFFFFF);
    }

    /**
     * Faster Rounding using the 16384 trick.
     * Returns float to avoid cast-back latency in math expressions.
     */
    public static float round(float x) {
        return (float) ((int) (x + 16384.5f) - 16384);
    }

    public static float sin32(float x) {
        // Use the internal fastRound to avoid Math.round overhead
        x = x - PI2 * round(x * INV_PI2);

        // Inline the abs logic for speed
        final float absX = Float.intBitsToFloat(Float.floatToRawIntBits(x) & 0x7FFFFFFF);
        float y = B * x + C * x * absX;

        float absY = Float.intBitsToFloat(Float.floatToRawIntBits(y) & 0x7FFFFFFF);
        return P * (y * absY - y) + y;
    }

    public static float cos32(float x) {
        // Shift to cosine and wrap
        x = x + HALF_PI;
        x = x - PI2 * round(x * INV_PI2);

        float absX = Float.intBitsToFloat(Float.floatToRawIntBits(x) & 0x7FFFFFFF);
        float y = B * x + C * x * absX;

        float absY = Float.intBitsToFloat(Float.floatToRawIntBits(y) & 0x7FFFFFFF);
        return P * (y * absY - y) + y;
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

    public static float pow(float base, float exponent)
    {
        return (float) Math.pow(base, exponent);
    }

    public static float clamp(float val, float min, float max)
    {
        return Math.max(min, Math.min(max, val));
    }

    public static float cosFromSin(float sin, float angle)
    {
        int quadrant = sin >= 0 ? (angle >= 0 ? 1 : 4) : (angle >= 0 ? 2 : 3);
        float cosSquared = 1 - sin * sin;
        return (float) (Math.sqrt(cosSquared) * (quadrant == 1 || quadrant == 4 ? 1 : -1));
    }

    /**
     * Optimized Tan: Computes sine and cosine in a single pass
     * to share the wrapping and bit-masking costs.
     */
    public static float tan32(float x) {
        // Wrap once
        float xSin = x - PI2 * round(x * INV_PI2);

        // Sin Part
        float absXSin = Float.intBitsToFloat(Float.floatToRawIntBits(xSin) & 0x7FFFFFFF);
        float ySin = B * xSin + C * xSin * absXSin;
        float sin = P * (ySin * Float.intBitsToFloat(Float.floatToRawIntBits(ySin) & 0x7FFFFFFF) - ySin) + ySin;

        // Cos Part (Shifted x)
        float xCos = x + HALF_PI;
        xCos = xCos - PI2 * round(xCos * INV_PI2);
        float absXCos = Float.intBitsToFloat(Float.floatToRawIntBits(xCos) & 0x7FFFFFFF);
        float yCos = B * xCos + C * xCos * absXCos;
        float cos = P * (yCos * Float.intBitsToFloat(Float.floatToRawIntBits(yCos) & 0x7FFFFFFF) - yCos) + yCos;

        return sin / cos;
    }
}