#version 300 es
precision mediump float;

layout(location = 0) in vec3 a_Position;   // ← CRITICAL: Position at location 0
layout(location = 1) in vec2 a_TexCoord;   // ← CRITICAL: TexCoord at location 1

uniform mat4 u_ModelViewProjection;

out vec2 v_TexCoord;

void main() {
    gl_Position = u_ModelViewProjection * vec4(a_Position, 1.0);
    v_TexCoord = a_TexCoord;
}