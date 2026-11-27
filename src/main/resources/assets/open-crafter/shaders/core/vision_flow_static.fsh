#version 330
#extension GL_ARB_separate_shader_objects : require

#include <open-crafter:vision_flow.glsl>

// Flow of rigid world geometry and sky: reproject the reference frame's world depth with the
// reference camera, then project with the current camera. Pixels covered by the reference
// frame's first-person hand are marked occluded here and overwritten by the hand pass.

uniform sampler2D PrevWorldDepth;
uniform sampler2D PrevHandDepth;
uniform sampler2D CurWorldDepth;
uniform sampler2D CurHandDepth;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 result;

void main() {
    if (Flags.x < 0.5) {
        result = vision_result(vec2(0.0), false);
        return;
    }
    if (Flags.y < 0.5) {
        result = vision_occludedResult();
        return;
    }
    ivec2 texel = ivec2(gl_FragCoord.xy);
    if (!vision_isClear(texelFetch(PrevHandDepth, texel, 0).r, DepthInfo.y)) {
        result = vision_occludedResult();
        return;
    }
    result = vision_staticFlow(PrevWorldDepth, CurWorldDepth, CurHandDepth, gl_FragCoord.xy);
}
