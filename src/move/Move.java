package move;

import lang.FastMath;

public class Move {

    // --- CURVE CONSTANTS ---
    public static final int NORMAL = 0;
    public static final int EASE_IN = 1;
    public static final int EASE_OUT = 2;
    public static final int EASE_IN_OUT = 3;
    public static final int ELASTIC_IN = 4;
    public static final int ELASTIC_OUT = 5;
    public static final int ELASTIC_IN_OUT = 6;
    public static final int BOUNCE_IN = 7;
    public static final int BOUNCE_OUT = 8;
    public static final int BOUNCE_IN_OUT = 9;
    public static final int EXPONENTIAL_IN = 10;
    public static final int EXPONENTIAL_OUT = 11;
    public static final int EXPONENTIAL_IN_OUT = 12;
    public static final int SINE_IN = 13;
    public static final int SINE_OUT = 14;
    public static final int SINE_IN_OUT = 15;

    // --- INTERNAL STATE ---
    public boolean active = false;
    public int type = NORMAL;
    public float time = 0f, duration = 0f;
    public float startX, startY, startZ;
    public float endX, endY, endZ;

    // --- MODIFIERS ---
    public float power = 1.0f;
    public float dividend = 1.0f;

    // Chainable modifiers that only affect THIS component
    public Move setEaseAmount(float power) { this.power = power; return this; }
    public Move setBounceAmount(float power, float dividend) { this.power = power; this.dividend = dividend; return this; }
    public Move setExponentialAmount(float power) { this.power = power; return this; }
    public Move setElasticAmount(float power) { this.power = power; return this; }
    public Move setSineAmount(float power) { this.power = power; return this; }

    public void start(int type, float sx, float sy, float sz, float ex, float ey, float ez, float dur) {
        this.type = type;
        this.startX = sx; this.startY = sy; this.startZ = sz;
        this.endX = ex; this.endY = ey; this.endZ = ez;
        this.duration = dur;
        this.time = 0f;
        this.active = true;
    }

    public float getEased(float t) {
        // Clamp t between 0 and 1 just in case delta overshoots slightly
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;

        return switch(type) {

            // --- EASE (Power-based) ---
            case EASE_IN -> (float) Math.pow(t, power + 1);
            case EASE_OUT -> 1.0f - (float) Math.pow(1.0f - t, power + 1);
            case EASE_IN_OUT -> t < 0.5f ? (float) Math.pow(2 * t, power + 1) / 2.0f
                    : 1.0f - (float) Math.pow(-2 * t + 2, power + 1) / 2.0f;

            // --- SINE ---
            case SINE_IN -> 1.0f - FastMath.cos32((t * FastMath.PI) / 2.0f);
            case SINE_OUT -> FastMath.sin32((t * FastMath.PI) / 2.0f);
            case SINE_IN_OUT -> -(FastMath.cos32(FastMath.PI * t) - 1.0f) / 2.0f;

            // --- EXPONENTIAL ---
            case EXPONENTIAL_IN -> (float) Math.pow(power == 1.0f ? 2.0f : power, 10 * (t - 1));
            case EXPONENTIAL_OUT -> 1.0f - (float) Math.pow(power == 1.0f ? 2.0f : power, -10 * t);
            case EXPONENTIAL_IN_OUT -> {
                if(t < 0.5f) yield 0.5f * (float) Math.pow(power == 1.0f ? 2.0f : power, 20 * t - 10);
                yield 1.0f - 0.5f * (float) Math.pow(power == 1.0f ? 2.0f : power, -20 * t + 10);
            }

            // --- BOUNCE ---
            case BOUNCE_OUT -> calcBounceOut(t);
            case BOUNCE_IN ->
                // Inverse of Bounce Out
                    1.0f - calcBounceOut(1.0f - t);
            case BOUNCE_IN_OUT -> {
                if(t < 0.5f) yield (1.0f - calcBounceOut(1.0f - 2.0f * t)) / 2.0f;
                yield (1.0f + calcBounceOut(2.0f * t - 1.0f)) / 2.0f;
            }

            // --- ELASTIC ---
            case ELASTIC_IN -> calcElasticIn(t);
            case ELASTIC_OUT -> 1.0f - calcElasticIn(1.0f - t);
            case ELASTIC_IN_OUT -> {
                if(t < 0.5f) yield calcElasticIn(t * 2.0f) * 0.5f;
                yield 1.0f - (calcElasticIn((1.0f - t) * 2.0f) * 0.5f);
            }
            default -> t;
        };
    }

    // Helper method to keep the math clean inside the switch statement
    private float calcBounceOut(float t) {
        float n1 = 7.5625f * power;
        float d1 = 2.75f * dividend;
        if (t < 1.0f / d1) {
            return n1 * t * t;
        } else if (t < 2.0f / d1) {
            return n1 * (t -= 1.5f / d1) * t + 0.75f;
        } else if (t < 2.5f / d1) {
            return n1 * (t -= 2.25f / d1) * t + 0.9375f;
        } else {
            return n1 * (t -= 2.625f / d1) * t + 0.984375f;
        }
    }

    // Standard Penner elastic math, injected with your custom power variables
    private float calcElasticIn(float t) {
        if (t == 0 || t == 1) return t;
        float c4 = (2f * FastMath.PI) / 3f;
        return -(float) (Math.pow(2f, 10f * (t - 1f)) * FastMath.sin32((t * 10f - 10.75f) * c4 * power));
    }
}