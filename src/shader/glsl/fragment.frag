#version 450
#extension GL_EXT_nonuniform_qualifier : require

layout(location = 0) in vec2 fragTexCoord;
layout(location = 1) in flat int fragTextureId;
// ---> NEW: Catch the material data from the Vertex Shader <---
layout(location = 2) in flat float fragIsTransparent;
layout(location = 3) in flat float fragCelShade;
layout(location = 4) in flat float fragReflectivity;
layout(location = 5) in flat float fragShineDamper;

layout(location = 0) out vec4 outColor;

layout(binding = 0) uniform sampler2D textures[];

void main() {
    vec4 textureColor = texture(textures[nonuniformEXT(fragTextureId)], fragTexCoord);

    // Use the dynamic transparency flag from our Java Material object
    if (fragIsTransparent > 0.5 && textureColor.a < 0.1) {
        discard;
    }

    // If celShade is set to 1.0 in Java, posterize the colors to look "blocky"
    if (fragCelShade > 0.5) {
        float levels = 4.0;
        textureColor.rgb = floor(textureColor.rgb * levels) / levels;
    }

    // THE FIX: Use fragShineDamper so the SPIR-V compiler doesn't delete it!
    textureColor.rgb += vec3(fragReflectivity * 0.25) * fragShineDamper;

    outColor = textureColor;
}