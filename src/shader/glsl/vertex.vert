#version 450

// [CHANGED: Expanded to hold the Mat4 (64 bytes) + the Texture ID (4 bytes)]
layout(push_constant) uniform PushConstants {
    mat4 transformationMatrix;
    int textureId;
} push;

// [CHANGED: Pulling directly from your MeshLoader's buffers!]
layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec2 inTexCoord;

// Pass these to the fragment shader
layout(location = 0) out vec2 fragTexCoord;
layout(location = 1) out flat int fragTextureId; // 'flat' means don't interpolate integers!

void main() {
    gl_Position = push.transformationMatrix * vec4(inPosition, 1.0);
    fragTexCoord = inTexCoord;
    fragTextureId = push.textureId;
}