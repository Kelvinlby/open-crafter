#version 330
#extension GL_ARB_separate_shader_objects : require

// Replays a GUI draw into the HUD mask. Attributes are bound by name, so one shader serves
// every GUI vertex format; HAS_UV0 / HAS_COLOR select the optional inputs.

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
#ifdef HAS_UV0
layout(location = 1) in vec2 UV0;
layout(location = 0) out vec2 texCoord0;
#endif
#ifdef HAS_COLOR
layout(location = 2) in vec4 Color;
layout(location = 1) out vec4 vertexColor;
#endif

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
#ifdef HAS_UV0
    texCoord0 = UV0;
#endif
#ifdef HAS_COLOR
    vertexColor = Color;
#endif
}
