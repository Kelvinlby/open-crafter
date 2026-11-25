#version 330
#extension GL_ARB_separate_shader_objects : require

#include <open-crafter:vision_flow.glsl>

// Writes the flow of a recorded moving surface, but only where that surface is the one that
// was visible in the reference frame (its distance matches the reference depth texel).

uniform sampler2D PrevDepth;       // reference world depth, or reference hand depth with HAND
uniform sampler2D CurHandDepth;
#ifndef HAND
uniform sampler2D CurWorldDepth;
#endif

layout(location = 0) in vec4 curPosition;
layout(location = 1) in float prevDistance;

layout(location = 0) out vec4 result;

#ifdef HAND
#define PREV_INV_PROJ PrevInvHandProj
#define CUR_PROJ CurHandProj
#else
#define PREV_INV_PROJ PrevInvProj
#define CUR_PROJ CurProj
#endif

bool wasVisible() {
    float depth = texelFetch(PrevDepth, ivec2(gl_FragCoord.xy), 0).r;
    if (vision_isClear(depth, DepthInfo.y)) {
        return false;
    }
    float visibleDistance = -vision_unproject(PREV_INV_PROJ, vision_pixelToNdc(gl_FragCoord.xy), depth).z;
    return abs(visibleDistance - prevDistance) <= 0.01 + 0.005 * visibleDistance;
}

bool isOccludedAt(vec2 pixel, float distance) {
    if (!vision_insideScreen(pixel)) {
        return true;
    }
#ifdef HAND
    return !vision_matchesDepth(CurHandDepth, CurInvHandProj, pixel, distance, DepthInfo.z);
#else
    return vision_handCovers(CurHandDepth, pixel, DepthInfo.z)
        || !vision_matchesDepth(CurWorldDepth, CurInvProj, pixel, distance, DepthInfo.z);
#endif
}

void main() {
    if (!wasVisible()) {
        discard;
    }
    if (curPosition.w < 0.5) {
        result = vision_occludedResult();
        return;
    }
    vec4 clip = CUR_PROJ * vec4(curPosition.xyz, 1.0);
    if (clip.w <= 1.0e-6) {
        result = vision_occludedResult();
        return;
    }
    vec2 pixel = vision_ndcToPixel(clip.xy / clip.w);
    result = vision_result(pixel - gl_FragCoord.xy, isOccludedAt(pixel, -curPosition.z));
}
