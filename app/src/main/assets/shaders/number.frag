#version 300 es

precision mediump float;

uniform sampler2D u_Texture;

in vec2 v_TexCoord;
out vec4 fragColor;

void main() {
    fragColor = texture(u_Texture, v_TexCoord);

    // Discard fully transparent pixels
    if (fragColor.a < 0.1) {
        discard;
    }
}