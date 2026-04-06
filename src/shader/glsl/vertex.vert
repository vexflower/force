#version 450

// 96 Bytes - Supports both 3D Bindless Offsets AND 2D UI Matrices
layout(push_constant) uniform PushConstants {
    mat4 transform;         // 64 bytes (Used for UI)
    int renderType;         // 4 bytes  (0 = 3D, 1 = UI)
    int instanceOffset;     // 4 bytes  (Used for 3D)
    int vertexOffset;       // 4 bytes  (Used for UI)
    int textureId;          // 4 bytes  (Used for UI)
    vec2 screenSize;        // 8 bytes  (Used for UI)
    vec2 padding;           // 8 bytes  (Padding to reach exactly 96 bytes)
} push;

struct EntityData {
    mat4 transform;
    float texId1;
    float vertexOffset;
    float pad1, pad2, pad3, pad4, pad5, pad6;
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

void main() {
    if (push.renderType == 1) {
        // --- 2D UI PIXEL PERFECT MODE ---
        Vertex v = globalVertices[push.vertexOffset + gl_VertexIndex];
        vec3 inPosition = v.pos_uvX.xyz;
        vec2 inTexCoord = vec2(v.pos_uvX.w, v.uvY_normal.x);

        vec4 pixelPos = push.transform * vec4(inPosition, 1.0);
        float ndcX = (pixelPos.x / push.screenSize.x) * 2.0 - 1.0;
        float ndcY = (pixelPos.y / push.screenSize.y) * 2.0 - 1.0;

        gl_Position = vec4(ndcX, ndcY, 0.0, 1.0);
        fragTexCoord = inTexCoord;
        fragTextureId = push.textureId;

    } else {
        // --- 3D BINDLESS MODE ---
        int globalIndex = push.instanceOffset + gl_InstanceIndex;
        EntityData myData = entities[globalIndex];

        int vOffset = floatBitsToInt(myData.vertexOffset);
        Vertex v = globalVertices[vOffset + gl_VertexIndex];

        vec3 inPosition = v.pos_uvX.xyz;
        vec2 inTexCoord = vec2(v.pos_uvX.w, v.uvY_normal.x);

        gl_Position = myData.transform * vec4(inPosition, 1.0);
        fragTexCoord = inTexCoord;
        fragTextureId = floatBitsToInt(myData.texId1);
    }
}