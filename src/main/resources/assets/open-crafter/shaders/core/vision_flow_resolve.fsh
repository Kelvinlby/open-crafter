#version 330
#extension GL_ARB_separate_shader_objects : require

// Final flow output, top row first, +y flow pointing down.
// - Pixels of GUI elements that moved or vanished (GuiFlow.w = 1) get the element's motion.
// - Other HUD/menu pixels did not move: flow 0, occluded only if a GUI element that moved or
//   appeared covers them in the current frame.
// - World pixels keep the world flow and are also occluded when they end up under such an element.

uniform sampler2D FlowWork;
uniform sampler2D PrevHudMask;
uniform sampler2D GuiFlow;      // render-target grid, (dx, dy) already in pixels with y down
uniform sampler2D CurGuiCover;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 flowOut;
layout(location = 1) out vec4 occludedOut;

bool coveredNow(ivec2 texel, ivec2 size) {
    if (any(lessThan(texel, ivec2(0))) || any(greaterThanEqual(texel, size))) {
        return false;
    }
    return texelFetch(CurGuiCover, texel, 0).r > 0.5;
}

void main() {
    ivec2 size = textureSize(FlowWork, 0);
    ivec2 texel = clamp(ivec2(texCoord * vec2(size)), ivec2(0), size - 1);
    vec4 gui = texelFetch(GuiFlow, texel, 0);
    bool occluded;
    if (gui.w > 0.5) {
        flowOut = vec4(gui.xy, 0.0, 0.0);
        occluded = gui.z > 0.5;
    } else if (texelFetch(PrevHudMask, texel, 0).r > 0.5) {
        flowOut = vec4(0.0);
        occluded = coveredNow(texel, size);
    } else {
        vec4 work = texelFetch(FlowWork, texel, 0);
        flowOut = vec4(work.x, -work.y, 0.0, 0.0);
        occluded = work.z > 0.5 || coveredNow(texel + ivec2(round(work.xy)), size);
    }
    // R8_UNORM stores round(v * 255): 1/255 becomes the published value 1.
    occludedOut = vec4(occluded ? 1.0 / 255.0 : 0.0);
}
