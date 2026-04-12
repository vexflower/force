package environment;

import util.CFloatList;
import util.CIntList;

// The AAA "Entity Manager" Concept
public class RendererManager {
    // Transform Data (16 floats per entity for a Mat4)
    public static final CFloatList transforms = new CFloatList(10000 * 16);

    // Mesh & Material Data
    public static final CIntList meshIds = new CIntList(10000);
    public static final CIntList diffuseTextureIds = new CIntList(10000);
    public static final CIntList normalTextureIds = new CIntList(10000);
    public static final CIntList specularTextureIds = new CIntList(10000);
    public static final CIntList materialTextureIds = new CIntList(10000);
    public static final CIntList brushTextureIds = new CIntList(10000);
    public static final CIntList particleIds = new CIntList(2000);
    public static final CIntList particleTextureIds = new CIntList(10000);

    // Material Properties
    public static final CFloatList shineDampers = new CFloatList(10000);
    public static final CFloatList reflectivities = new CFloatList(10000);
    public static final CFloatList transparencies = new CFloatList(10000);
    public static final CFloatList blurriness = new CFloatList(10000);
    public static final CFloatList celShades = new CFloatList(10000);
    public static final CFloatList contrasts = new CFloatList(10000);
    public static final CFloatList shadows = new CFloatList(10000);

    // Add these helper methods to the bottom of your RendererManager class
    private static int nextEntityId = 0;

    public static int createEntity() {
        int id = nextEntityId++;

        // Add 16 zeros for the Mat4 transform
        for(int i = 0; i < 16; i++) transforms.add(0f);

        // YOU MISSED THESE! We must add a default to EVERY list so 'size' stays synced with 'id'
        meshIds.add(0);
        diffuseTextureIds.add(0);
        normalTextureIds.add(0);
        specularTextureIds.add(0);
        materialTextureIds.add(0);
        brushTextureIds.add(0);
        particleIds.add(0);
        particleTextureIds.add(0);

        shineDampers.add(1.0f);
        reflectivities.add(0.0f);
        transparencies.add(1.0f);
        blurriness.add(0.0f);
        celShades.add(0.0f);
        contrasts.add(1.0f);
        shadows.add(1.0f);

        return id;
    }

    public static void destroy() {
        transforms.free();
        meshIds.free(); diffuseTextureIds.free(); normalTextureIds.free();
        specularTextureIds.free(); materialTextureIds.free(); brushTextureIds.free();
        particleIds.free(); particleTextureIds.free();

        shineDampers.free(); reflectivities.free(); transparencies.free();
        blurriness.free(); celShades.free(); contrasts.free(); shadows.free();
    }

}