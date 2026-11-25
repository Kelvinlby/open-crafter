#version 330
#extension GL_ARB_separate_shader_objects : require

// Copies a depth attachment (any depth format) into an R32F color texture, texel for texel.

uniform sampler2D InSampler;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(texelFetch(InSampler, ivec2(gl_FragCoord.xy), 0).r, 0.0, 0.0, 0.0);
}
