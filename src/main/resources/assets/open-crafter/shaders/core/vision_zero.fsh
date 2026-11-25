#version 330
#extension GL_ARB_separate_shader_objects : require

// Writes zero everywhere: initialises a render target with a real draw.

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(0.0);
}
