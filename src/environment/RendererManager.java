package environment;

import util.FloatList;
import util.IntList;

// The AAA "Entity Manager" Concept
public class RendererManager {
    // The index in these lists IS the Entity ID.

    // Transform Data (16 floats per entity for a Mat4)
    public static FloatList transforms = new FloatList(10000 * 16);

    // Mesh & Material Data (Primitive IDs referencing your Bindless Array!)
    public static IntList meshIds = new IntList(10000);
    public static IntList diffuseTextureIds = new IntList(10000);
    public static IntList normalTextureIds = new IntList(10000);
    public static IntList specularTextureIds = new IntList(10000);
    public static IntList materialTextureIds = new IntList(10000);
    public static IntList brushTextureIds = new IntList(10000);
    public static IntList particleIds = new IntList(2000); // first index be the actual id of particle, second index would be how many particle textures for textureids
    public static IntList particleTextureIds = new IntList(10000);

    // Material Properties
    public static FloatList shineDampers = new FloatList(10000);
    public static FloatList reflectivities = new FloatList(10000);
    public static FloatList transparencies = new FloatList(10000);
    public static FloatList blurriness = new FloatList(10000);
    public static FloatList celShades = new FloatList(10000);
    public static FloatList contrasts = new FloatList(10000);
    public static FloatList shadows = new FloatList(10000);

    // Add these helper methods to the bottom of your RendererManager class
    private static int nextEntityId = 0;

    public static int createEntity() {
        int id = nextEntityId++;

        // Initialize default values for this new entity to prevent crashes
        // Add 16 zeros for the Mat4 transform
        for(int i = 0; i < 16; i++) transforms.add(0f);

        meshIds.add(0);
        diffuseTextureIds.add(0);
        // ... add defaults for your other lists if needed ...

        return id;
    }

}