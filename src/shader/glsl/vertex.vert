#version 450

// 80 Bytes total
layout(push_constant) uniform PushConstants {
    mat4 transform;    // 64 bytes
    int textureId;     // 4 bytes
    int renderType;    // 4 bytes (0 = Standard 3D, 1 = 2D UI, 2 = Water, etc.)
    vec2 screenSize;   // 8 bytes
} push;

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec2 inTexCoord;

layout(location = 0) out vec2 fragTexCoord;
layout(location = 1) out flat int fragTextureId;

void main() {

    // --- [ ID: 1 ] 2D PIXEL-PERFECT UI ---
    if (push.renderType == 1) {
        vec4 pixelPos = push.transform * vec4(inPosition, 1.0);

        // Convert physical pixels to Vulkan NDC (-1.0 to 1.0)
        float ndcX = (pixelPos.x / push.screenSize.x) * 2.0 - 1.0;
        float ndcY = (pixelPos.y / push.screenSize.y) * 2.0 - 1.0;

        gl_Position = vec4(ndcX, ndcY, 0.0, 1.0);
    }
    // --- [ ID: 2 ] FUTURE WATER SIMULATION ---
    else if (push.renderType == 2) {
        // Example: Apply sin() wave displacement to inPosition.y before multiplying MVP
        vec3 displacedPos = inPosition;
        // displacedPos.y += sin(...)
        gl_Position = push.transform * vec4(displacedPos, 1.0);
    }
    // --- [ ID: 0 ] STANDARD 3D ENTITY (Fallback) ---
    else {
        gl_Position = push.transform * vec4(inPosition, 1.0);
    }

    fragTexCoord = inTexCoord;
    fragTextureId = push.textureId;
}