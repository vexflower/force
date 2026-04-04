#version 450

// 16 Bytes (4 floats) - Used for global state shifts
layout(push_constant) uniform PushConstants {
    vec2 screenSize;
    int renderType;
    int instanceOffset; // Where in the SSBO this mesh group starts
} push;

// 96 Bytes (24 floats) - Perfectly matches your SceneSnapshot packing!
struct EntityData {
    mat4 transform;
    float texId1;
    float vertexOffset; // Where in the Geometry SSBO this model's vertices start
    float pad1, pad2, pad3, pad4, pad5, pad6; // Padding to maintain alignment
};

// BINDING 1: The 100,000 Entity Mega-Buffer
layout(std430, binding = 1) readonly buffer EntityBuffer {
    EntityData entities[];
};

// 32 Bytes (8 floats) - Packed as vec4s to dodge std430 alignment corruption
struct Vertex {
    vec4 pos_uvX;      // (PosX, PosY, PosZ, UvX)
    vec4 uvY_normal;   // (UvY, NormX, NormY, NormZ)
};

// BINDING 2: The Global Geometry Mega-Buffer
layout(std430, binding = 2) readonly buffer GlobalVertexBuffer {
    Vertex globalVertices[];
};

// Outputs to Fragment Shader
layout(location = 0) out vec2 fragTexCoord;
layout(location = 1) out flat int fragTextureId;

void main() {
    // 1. Fetch the Entity Data
    int globalIndex = push.instanceOffset + gl_InstanceIndex;
    EntityData myData = entities[globalIndex];

    // 2. Fetch the Vertex Data (Programmable Pulling!)
    int vOffset = floatBitsToInt(myData.vertexOffset);
    Vertex v = globalVertices[vOffset + gl_VertexIndex];

    // 3. Unpack the vec4s back into standard vectors
    vec3 inPosition = v.pos_uvX.xyz;
    vec2 inTexCoord = vec2(v.pos_uvX.w, v.uvY_normal.x);
    // vec3 inNormal = v.uvY_normal.yzw; // Ready for when you implement lighting!

    // 4. Calculate Final Position
    if (push.renderType == 1) { // 2D UI Mode
        vec4 pixelPos = myData.transform * vec4(inPosition, 1.0);
        float ndcX = (pixelPos.x / push.screenSize.x) * 2.0 - 1.0;
        float ndcY = (pixelPos.y / push.screenSize.y) * 2.0 - 1.0;
        gl_Position = vec4(ndcX, ndcY, 0.0, 1.0);
    } else { // 3D Mode
        gl_Position = myData.transform * vec4(inPosition, 1.0);
    }

    // 5. Pass data down the pipeline
    fragTexCoord = inTexCoord;
    fragTextureId = floatBitsToInt(myData.texId1);
}