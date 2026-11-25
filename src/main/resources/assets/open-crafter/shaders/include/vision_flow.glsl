#ifndef OPEN_CRAFTER_VISION_FLOW_GLSL
#define OPEN_CRAFTER_VISION_FLOW_GLSL

// Shared state of the optical-flow passes. "Prev" is the reference (previously published)
// frame, "Cur" the frame being published. All pixel coordinates here are render-target
// coordinates: row 0 is NDC y = -1 on both backends, +y points up; the resolve pass flips
// rows and the y sign for publishing.
layout(std140) uniform VisionFlow {
    mat4 PrevProj;        // world projection (incl. view bobbing) of the reference frame
    mat4 PrevInvProj;
    mat4 PrevInvView;     // inverse view rotation of the reference frame
    mat4 CurProj;
    mat4 CurInvProj;
    mat4 CurView;         // view rotation of the current frame
    mat4 PrevHandProj;    // first-person hand projection (separate FOV)
    mat4 PrevInvHandProj;
    mat4 CurHandProj;
    mat4 CurInvHandProj;
    vec4 CameraDelta;     // xyz: reference camera position minus current camera position
    vec4 ScreenSize;      // width, height, 1/width, 1/height
    vec4 DepthInfo;       // x: 1 if NDC z == depth else NDC z == 2 depth - 1; y/z: clear depth of prev/cur
    vec4 Flags;           // x: reference frame rendered a level; y: current frame rendered a level
};

const float FAR_AWAY = 1.0e30;

float vision_ndcZ(float depth) {
    return DepthInfo.x > 0.5 ? depth : depth * 2.0 - 1.0;
}

vec2 vision_pixelToNdc(vec2 pixel) {
    return pixel * ScreenSize.zw * 2.0 - 1.0;
}

vec2 vision_ndcToPixel(vec2 ndc) {
    return (ndc * 0.5 + 0.5) * ScreenSize.xy;
}

bool vision_isClear(float depth, float clearDepth) {
    return abs(depth - clearDepth) < 1.0e-7;
}

vec3 vision_unproject(mat4 invProj, vec2 ndcXY, float depth) {
    vec4 view = invProj * vec4(ndcXY, vision_ndcZ(depth), 1.0);
    return view.xyz / view.w;
}

// Distance along the view axis of the surface stored in a depth texel (FAR_AWAY for sky).
float vision_texelDistance(sampler2D depthTex, mat4 invProj, ivec2 texel, float clearDepth) {
    float depth = texelFetch(depthTex, texel, 0).r;
    if (vision_isClear(depth, clearDepth)) {
        return FAR_AWAY;
    }
    vec2 ndc = vision_pixelToNdc(vec2(texel) + 0.5);
    return -vision_unproject(invProj, ndc, depth).z;
}

float vision_tolerance(float distance) {
    return 0.05 + 0.01 * distance;
}

bool vision_insideScreen(vec2 pixel) {
    return pixel.x >= 0.0 && pixel.y >= 0.0 && pixel.x < ScreenSize.x && pixel.y < ScreenSize.y;
}

ivec2 vision_texelAt(vec2 pixel) {
    return clamp(ivec2(floor(pixel)), ivec2(0), ivec2(ScreenSize.xy) - 1);
}

// True when a surface point at view distance `distance` that projects to `pixel` is the visible
// surface of `depthTex`. The reprojected position is sub-pixel, so near a depth discontinuity
// the texel it falls in may belong to the other surface even though the point is visible.
// The point therefore counts as visible when
//  - some texel of the 3x3 neighbourhood of the texel it falls in holds the same surface
//    (distance within tolerance), or
//  - it lies inside the depth range of the 2x2 bilinear footprint (surfaces at grazing angles,
//    whose depth changes by more than the tolerance from one texel to the next).
// A true disocclusion (something now in front over the whole neighbourhood, or the surface
// gone so everything is farther) matches neither.
bool vision_matchesDepth(sampler2D depthTex, mat4 invProj, vec2 pixel, float distance, float clearDepth) {
    ivec2 limit = ivec2(ScreenSize.xy) - 1;
    ivec2 center = vision_texelAt(pixel);
    for (int i = 0; i < 9; i++) {
        ivec2 texel = clamp(center + ivec2(i % 3 - 1, i / 3 - 1), ivec2(0), limit);
        float d = vision_texelDistance(depthTex, invProj, texel, clearDepth);
        if (distance >= FAR_AWAY ? d >= FAR_AWAY : abs(d - distance) <= vision_tolerance(distance)) {
            return true;
        }
    }
    if (distance >= FAR_AWAY) {
        return false;
    }
    ivec2 base = ivec2(floor(pixel - 0.5));
    float nearest = FAR_AWAY;
    float farthest = 0.0;
    for (int i = 0; i < 4; i++) {
        float d = vision_texelDistance(depthTex, invProj, clamp(base + ivec2(i & 1, i >> 1), ivec2(0), limit), clearDepth);
        nearest = min(nearest, d);
        farthest = max(farthest, d);
    }
    return distance >= nearest && distance <= farthest;
}

bool vision_handCovers(sampler2D handDepth, vec2 pixel, float clearDepth) {
    return !vision_isClear(texelFetch(handDepth, vision_texelAt(pixel), 0).r, clearDepth);
}

// Packs a flow result: xy = flow in pixels (+y up), z = 1 if occluded.
vec4 vision_result(vec2 flow, bool occluded) {
    return vec4(flow, occluded ? 1.0 : 0.0, 0.0);
}

vec4 vision_occludedResult() {
    return vec4(0.0, 0.0, 1.0, 0.0);
}

#endif
