#version 330
#extension GL_ARB_separate_shader_objects : require

#include <open-crafter:vision_flow.glsl>

// Geometry of moving objects (entities, block entities, particles, first-person hand),
// recorded on the CPU for the reference frame and the current frame. Each vertex carries its
// view-space position in both frames; the triangle is rasterised where it was in the reference
// frame, and the current position is interpolated perspective-correctly, which makes it exact
// for any per-triangle affine motion (rigid parts and skinned limbs alike).

layout(location = 0) in vec3 PrevPosition;
layout(location = 1) in vec4 CurPosition;   // w = 1 if the object still exists with the same shape

layout(location = 0) out vec4 curPosition;
layout(location = 1) out float prevDistance;

void main() {
#ifdef HAND
    gl_Position = PrevHandProj * vec4(PrevPosition, 1.0);
#else
    gl_Position = PrevProj * vec4(PrevPosition, 1.0);
#endif
    curPosition = CurPosition;
    prevDistance = -PrevPosition.z;
}
