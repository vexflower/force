#version 450

// The 64-byte Push Constant we send from VKShader.java!
layout(push_constant) uniform PushConstants {
    mat4 transformationMatrix;
} push;

// Hardcoded vertices so we don't need Vulkan VBO memory allocators yet
vec2 positions[3] = vec2[](
vec2(0.0, -0.5),
vec2(0.5, 0.5),
vec2(-0.5, 0.5)
);

// Hardcoded vibrant colors!
vec3 colors[3] = vec3[](
vec3(1.0, 0.0, 0.0), // Red
vec3(0.0, 1.0, 0.0), // Green
vec3(0.0, 0.0, 1.0)  // Blue
);

// Sent to the fragment shader
layout(location = 0) out vec3 fragColor;

void main() {
    // Apply our Java Mat4 to the hardcoded coordinates
    gl_Position = push.transformationMatrix * vec4(positions[gl_VertexIndex], 0.0, 1.0);
    fragColor = colors[gl_VertexIndex];
}