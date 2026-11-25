#version 330
#extension GL_ARB_separate_shader_objects : require

// A GUI fragment counts as covering the world when its alpha reaches ALPHA_THRESHOLD.

#ifdef HAS_UV0
uniform sampler2D Sampler0;
layout(location = 0) in vec2 texCoord0;
#endif
#ifdef HAS_COLOR
layout(location = 1) in vec4 vertexColor;
#endif

layout(location = 0) out vec4 mask;

const float ALPHA_THRESHOLD = 0.1;

void main() {
    float alpha = 1.0;
#ifdef HAS_UV0
    alpha *= texture(Sampler0, texCoord0).a;
#endif
#ifdef HAS_COLOR
    alpha *= vertexColor.a;
#endif
    if (alpha < ALPHA_THRESHOLD) {
        discard;
    }
    mask = vec4(1.0);
}
