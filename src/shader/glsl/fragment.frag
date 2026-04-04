#version 450
#extension GL_EXT_nonuniform_qualifier : require

layout(location = 0) in vec2 fragTexCoord;
layout(location = 1) in flat int fragTextureId;

layout(location = 0) out vec4 outColor;

// BINDING 0: The Global Phonebook of 4096 Textures
layout(binding = 0) uniform sampler2D textures[];

void main() {
    // Zero-overhead texture sampling
    outColor = texture(textures[nonuniformEXT(fragTextureId)], fragTexCoord);

    // Safety check: Discard completely transparent pixels so they don't corrupt the depth buffer
    if (outColor.a < 0.01) {
        discard;
    }
}