#version 300 es
precision mediump float;

uniform mat4 u_ModelViewProjection;
in vec4 a_Position;

void main() {
    gl_Position = u_ModelViewProjection * a_Position;
}
