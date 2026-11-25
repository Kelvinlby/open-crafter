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
uniform sampler2D CurHudMask;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 result;

// The sky is at infinity: only the view direction matters, camera translation does not.
vec4 skyClip(vec2 ndc) {
    vec3 ray = vision_unproject(PrevInvProj, ndc, 0.75) - vision_unproject(PrevInvProj, ndc, 0.25);
    ray = normalize(ray.z < 0.0 ? ray : -ray);
    vec3 worldDir = (PrevInvView * vec4(ray, 0.0)).xyz;
    return CurProj * vec4((CurView * vec4(worldDir, 0.0)).xyz, 0.0);
}

vec4 surfaceClip(vec2 ndc, float depth, out float distance) {
    vec3 prevView = vision_unproject(PrevInvProj, ndc, depth);
    vec3 cameraRelative = (PrevInvView * vec4(prevView, 1.0)).xyz + CameraDelta.xyz;
    vec3 curView = (CurView * vec4(cameraRelative, 1.0)).xyz;
    distance = -curView.z;
    return CurProj * vec4(curView, 1.0);
}

bool isOccludedAt(vec2 pixel, float distance) {
    return !vision_insideScreen(pixel)
        || !vision_matchesDepth(CurWorldDepth, CurInvProj, pixel, distance, DepthInfo.z)
        || vision_handCovers(CurHandDepth, pixel, DepthInfo.z)
        || vision_covered(CurHudMask, pixel);
}

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
    float depth = texelFetch(PrevWorldDepth, texel, 0).r;
    vec2 ndc = vision_pixelToNdc(gl_FragCoord.xy);
    float distance = FAR_AWAY;
    vec4 clip = vision_isClear(depth, DepthInfo.y) ? skyClip(ndc) : surfaceClip(ndc, depth, distance);
    if (clip.w <= 1.0e-6) {
        result = vision_occludedResult();
        return;
    }
    vec2 pixel = vision_ndcToPixel(clip.xy / clip.w);
    result = vision_result(pixel - gl_FragCoord.xy, isOccludedAt(pixel, distance));
}
