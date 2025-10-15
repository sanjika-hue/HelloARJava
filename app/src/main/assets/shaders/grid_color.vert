#version 300 es
precision mediump float;

layout(location = 0) in vec3 a_Position;  // ← CRITICAL: Add layout location

uniform mat4 u_ModelViewProjection;

void main() {
    gl_Position = u_ModelViewProjection * vec4(a_Position, 1.0);
}