#version 330
#extension GL_ARB_separate_shader_objects : require

// Full-screen triangle. Both renderpearl backends store row 0 of a render target at
// NDC y = -1 (the bottom of the image). With FLIP_Y the pass writes the image top row
// first, which is the layout every published stream uses.
layout(location = 0) out vec2 texCoord;

void main() {
    vec2 uv = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
#ifdef FLIP_Y
    texCoord = vec2(uv.x, 1.0 - uv.y);
#else
    texCoord = uv;
#endif
}
