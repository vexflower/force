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

    // 1 / (2 * PI) - Precalculated to avoid division in the hot loop
    private static final float INV_PI2 = 0.15915494f;

    // Constants for the Parabola / Taylor hybrid curve fit
    private static final float B = 1.27323954f;  // 4 / PI
    private static final float C = -0.40528473f; // -4 / (PI^2)
    private static final float P = 0.225f;       // Precision curve weight

    private FastMath() {} // Prevent instantiation

    /**
     * Ultra-fast 32-bit sine approximation.
     * 100% Cache-Miss proof. Uses only CPU registers.
     */
    public static float sin32(float x) {
        // 1. Wrap angle to [-PI, PI] range
        // We use multiplication by INV_PI2 instead of division for speed
        x = x - PI2 * Math.round(x * INV_PI2);

        // 2. Compute Parabola approximation
        float y = B * x + C * x * Math.abs(x);

        // 3. Extra precision pass to smooth out the curve
        return P * (y * Math.abs(y) - y) + y;
    }

    /**
     * Ultra-fast 32-bit cosine approximation.
     */
    public static float cos32(float x) {
        // Cosine is mathematically identical to Sine shifted by PI / 2
        return sin32(x + HALF_PI);
    }

    /**
     * Fast 32-bit tangent approximation.
     */
    public static float tan32(float x) {
        // Tangent is simply sine over cosine.
        // Note: For extreme performance, ensure cos32(x) is not exactly 0.
        return sin32(x) / cos32(x);
    }

}