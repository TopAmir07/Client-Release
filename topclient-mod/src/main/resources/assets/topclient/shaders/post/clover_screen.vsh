#version 330

// Clover Client: full-screen pass that does not need any vertex data.
// Works whether Minecraft draws the pass as a quad or as one big triangle.
out vec2 texCoord;

void main() {
    vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
    texCoord = uv;
}
