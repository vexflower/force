package model;

public class Material {
    // 0 means no texture assigned
    public int diffuseTextureId = 0;
    public int normalMapTextureId = 0;
    public int specularMapTextureId = 0;

    // Primitive math values for the shader
    public float shineDamper = 1.0f;
    public float reflectivity = 0.0f;
    public boolean hasTransparency = false;
}