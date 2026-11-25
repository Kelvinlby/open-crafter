#version 330
#extension GL_ARB_separate_shader_objects : require

// Final flow output, top row first: +y flow points down, HUD/menu pixels of the reference
// frame get zero flow and are never occluded.

uniform sampler2D FlowWork;
uniform sampler2D PrevHudMask;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 flowOut;
layout(location = 1) out vec4 occludedOut;

void main() {
    ivec2 size = textureSize(FlowWork, 0);
    ivec2 texel = clamp(ivec2(texCoord * vec2(size)), ivec2(0), size - 1);
    vec4 work = texelFetch(FlowWork, texel, 0);
    bool hud = texelFetch(PrevHudMask, texel, 0).r > 0.5;
    flowOut = hud ? vec4(0.0) : vec4(work.x, -work.y, 0.0, 0.0);
    // R8_UNORM stores round(v * 255): 1/255 becomes the published value 1.
    occludedOut = vec4(!hud && work.z > 0.5 ? 1.0 / 255.0 : 0.0);
}
