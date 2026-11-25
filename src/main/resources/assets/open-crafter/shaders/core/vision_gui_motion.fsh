#version 330
#extension GL_ARB_separate_shader_objects : require

// A GUI fragment counts where its alpha reaches 0.1, like the HUD mask.
// Motion pass: (dx, dy) in pixels (y down), occluded flag, 1. Cover pass: 1.

layout(std140) uniform VisionGui {
    mat4 GuiProj;
    vec4 GuiScreen;
};

#ifdef HAS_UV0
uniform sampler2D Sampler0;
#endif

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in float alpha;
layout(location = 2) in vec2 prevPos;
layout(location = 3) in vec2 curPos;
layout(location = 4) flat in vec4 clip;
layout(location = 5) flat in float vanished;

layout(location = 0) out vec4 fragColor;

void main() {
    float a = alpha;
#ifdef HAS_UV0
    a *= texture(Sampler0, texCoord0).a;
#endif
    if (a < 0.1) {
        discard;
    }
#ifdef COVER
    fragColor = vec4(1.0);
#else
    bool offScreen = any(lessThan(curPos, vec2(0.0))) || any(greaterThanEqual(curPos, GuiScreen.xy));
    bool clipped = any(lessThan(curPos, clip.xy)) || any(greaterThanEqual(curPos, clip.zw));
    bool hidden = vanished > 0.5 || offScreen || clipped;
    fragColor = vec4(curPos - prevPos, hidden ? 1.0 : 0.0, 1.0);
#endif
}
