#version 300 es
precision mediump float;

uniform sampler2D u_Texture;
uniform vec4 u_Color;

in vec2 v_TexCoord;
out vec4 fragColor;

void main() {
    vec4 texColor = texture(u_Texture, v_TexCoord);
    fragColor = texColor * u_Color;
}