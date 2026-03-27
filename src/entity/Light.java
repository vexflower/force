package entity;

/**
 * Pure Data-Oriented Struct for Lighting.
 */
public class Light {
    public float posX, posY, posZ;
    public float r, g, b;
    public float intensity;

    // Attenuation
    public float constant;
    public float linear;
    public float quadratic;

    public Light(float x, float y, float z, float r, float g, float b) {
        this.posX = x; this.posY = y; this.posZ = z;
        this.r = r; this.g = g; this.b = b;
        this.intensity = 1.0f;
        this.constant = 1.0f;
        this.linear = 0.0f;
        this.quadratic = 0.0f;
    }
}