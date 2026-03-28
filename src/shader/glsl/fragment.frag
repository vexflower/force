#version 450
#extension GL_EXT_nonuniform_qualifier : require

layout(location = 0) in vec2 fragTexCoord;
layout(location = 1) in flat int fragTextureId;

layout(location = 0) out vec4 outColor;

// [CHANGED: The Global Phonebook of 4096 Textures!]
layout(binding = 0) uniform sampler2D textures[];

void main() {
    // Look up the exact texture using the ID, and sample it using the UVs
    outColor = texture(textures[nonuniformEXT(fragTextureId)], fragTexCoord);
}