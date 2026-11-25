#version 330
#extension GL_ARB_separate_shader_objects : require

// GUI elements for the GUI flow passes, in framebuffer pixels (y down). The motion pass draws
// each element where it was in the reference frame; the cover pass (COVER) where it is now.

layout(std140) uniform VisionGui {
    mat4 GuiProj;    // orthographic, pixels -> clip space, same convention as vanilla's GUI
    vec4 GuiScreen;  // width, height
};

layout(location = 0) in vec2 PrevPos;
layout(location = 1) in vec2 CurPos;
layout(location = 2) in vec2 UV0;
layout(location = 3) in float Alpha;
layout(location = 4) in vec4 Clip;      // current scissor: left, top, right, bottom (pixels)
layout(location = 5) in float Vanished;

layout(location = 0) out vec2 texCoord0;
layout(location = 1) out float alpha;
layout(location = 2) out vec2 prevPos;
layout(location = 3) out vec2 curPos;
layout(location = 4) flat out vec4 clip;
layout(location = 5) flat out float vanished;

void main() {
#ifdef COVER
    vec2 at = CurPos;
#else
    vec2 at = PrevPos;
#endif
    gl_Position = GuiProj * vec4(at, -6000.0, 1.0);
    texCoord0 = UV0;
    alpha = Alpha;
    prevPos = PrevPos;
    curPos = CurPos;
    clip = Clip;
    vanished = Vanished;
}
