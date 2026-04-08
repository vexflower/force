#version 450

layout(push_constant) uniform PushConstants {
    mat4 vpMatrix;
    int renderType;
    int instanceOffset;
    int vertexOffset;
    int textureId;
    vec2 screenSize;
    vec2 padding;
} push;

struct EntityData {
    mat4 modelMatrix;
    float diffuseId;
    float vertexOffset;
    float isVisible;
    float isTransparent;
    float celShade;
    float reflectivity;
    float shineDamper;
    float padding;
};

layout(std430, binding = 1) readonly buffer EntityBuffer {
    EntityData entities[];
};

struct Vertex {
    vec4 pos_uvX;
    vec4 uvY_normal;
};

layout(std430, binding = 2) readonly buffer GlobalVertexBuffer {
    Vertex globalVertices[];
};

layout(location = 0) out vec2 fragTexCoord;
layout(location = 1) out flat int fragTextureId;
// ---> NEW: Pipe the material data out to the Fragment Shader <---
layout(location = 2) out flat float fragIsTransparent;
layout(location = 3) out flat float fragCelShade;
layout(location = 4) out flat float fragReflectivity;
layout(location = 5) out flat float fragShineDamper;

void main() {
    if (push.renderType == 1) {
        // --- 2D UI PIXEL PERFECT MODE ---
        Vertex v = globalVertices[push.vertexOffset + gl_VertexIndex];
        vec3 inPosition = v.pos_uvX.xyz;
        vec2 inTexCoord = vec2(v.pos_uvX.w, v.uvY_normal.x);

        vec4 pixelPos = push.vpMatrix * vec4(inPosition, 1.0);
        float ndcX = (pixelPos.x / push.screenSize.x) * 2.0 - 1.0;
        float ndcY = (pixelPos.y / push.screenSize.y) * 2.0 - 1.0;

        gl_Position = vec4(ndcX, ndcY, 0.0, 1.0);
        fragTexCoord = inTexCoord;
        fragTextureId = push.textureId;

        // Zero-out material properties for UI elements
        fragIsTransparent = 0.0;
        fragCelShade = 0.0;
        fragReflectivity = 0.0;
        fragShineDamper = 0.0;

    } else {
        // --- 3D BINDLESS MODE ---
        int globalIndex = push.instanceOffset + gl_InstanceIndex;
        EntityData myData = entities[globalIndex];

        if (myData.isVisible < 0.5) {
            gl_Position = vec4(0.0);
            return;
        }

        int vOffset = floatBitsToInt(myData.vertexOffset);
        Vertex v = globalVertices[vOffset + gl_VertexIndex];

        vec3 inPosition = v.pos_uvX.xyz;
        vec2 inTexCoord = vec2(v.pos_uvX.w, v.uvY_normal.x);

        gl_Position = push.vpMatrix * myData.modelMatrix * vec4(inPosition, 1.0);

        fragTexCoord = inTexCoord;
        fragTextureId = floatBitsToInt(myData.diffuseId);

        // ---> NEW: Grab the SSBO data and pass it along! <---
        fragIsTransparent = myData.isTransparent;
        fragCelShade = myData.celShade;
        fragReflectivity = myData.reflectivity;
        fragShineDamper = myData.shineDamper;
    }
}