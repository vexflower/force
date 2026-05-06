#version 450

layout(push_constant) uniform PushConstants { mat4 vpMatrix; int renderType; int instanceOffset; int vertexOffset; int textureId; vec2 screenSize; } push;

struct EntityData { mat4 modelMatrix; float diffuseId; float meshletOffset; float meshletCount; float isVisible; float isTransparent; float celShade; float refl; float shine; };
layout(std430, binding = 1) readonly buffer InstanceBuffer { EntityData entities[]; };

layout(std430, binding = 2) readonly buffer PosBuf { float positions[]; };
layout(std430, binding = 3) readonly buffer UvBuf { float uvs[]; };

layout(location = 0) out vec2 fragTexCoord;
layout(location = 1) out flat int fragTextureId;
layout(location = 2) out flat float fragIsTransparent;
layout(location = 3) out flat float fragCelShade;
layout(location = 4) out flat float fragReflectivity;
layout(location = 5) out flat float fragShineDamper;

void main() {
    if (push.renderType == 1) {
        // gl_VertexIndex is the index from the index buffer + vertexOffset
        int vId = gl_VertexIndex;

        vec3 pos = vec3(positions[vId*3], positions[vId*3+1], positions[vId*3+2]);
        vec2 uv = vec2(uvs[vId*2], uvs[vId*2+1]);

        // 1. Apply the local transform (Position/Scale in pixels)
        vec4 pixelPos = push.vpMatrix * vec4(pos, 1.0);

        // 2. Map from Pixel Space to Vulkan NDC (-1 to 1)
        // We set Z to 0.0 to ensure UI is drawn in front of the 3D scene
        gl_Position = vec4(
        (pixelPos.x / push.screenSize.x) * 2.0 - 1.0,
        (pixelPos.y / push.screenSize.y) * 2.0 - 1.0,
        0.0,
        1.0
        );

        fragTexCoord = uv;
        fragTextureId = push.textureId;

        // Set all lighting/material values to zero for UI
        fragIsTransparent = 0.0;
        fragCelShade = 0.0;
        fragReflectivity = 0.0;
        fragShineDamper = 0.0;
    } else {
        // ---> THE FIX: gl_InstanceIndex is now perfectly synced to the Triple Buffer!
        EntityData myData = entities[gl_InstanceIndex];
        if (myData.isVisible < 0.5) { gl_Position = vec4(0.0); return; }

        // FIXED: Vulkan already handles the offset via vkCmdDrawIndexedIndirect!
        int vId = gl_VertexIndex;

        vec3 pos = vec3(positions[vId*3], positions[vId*3+1], positions[vId*3+2]);
        vec2 uv = vec2(uvs[vId*2], uvs[vId*2+1]);

        gl_Position = push.vpMatrix * myData.modelMatrix * vec4(pos, 1.0);

        fragTexCoord = uv;
        fragTextureId = floatBitsToInt(myData.diffuseId);
        fragIsTransparent = myData.isTransparent; fragCelShade = myData.celShade; fragReflectivity = myData.refl; fragShineDamper = myData.shine;
    }
}