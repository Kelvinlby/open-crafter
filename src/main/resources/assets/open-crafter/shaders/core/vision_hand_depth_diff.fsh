#version 330
#extension GL_ARB_separate_shader_objects : require

// Shader-pack hand capture: keeps the depth only where the hand pass changed it, so the result
// covers exactly the hand's visible pixels (alpha-tested item gaps included) and nothing else.

uniform sampler2D InSampler;     // depth after the hand pass
uniform sampler2D PreHandDepth;  // R32F copy taken right before it

layout(location = 0) out vec4 fragColor;

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);
    float after = texelFetch(InSampler, texel, 0).r;
    float before = texelFetch(PreHandDepth, texel, 0).r;
    if (after == before) {
        discard;
    }
    fragColor = vec4(after, 0.0, 0.0, 0.0);
}
