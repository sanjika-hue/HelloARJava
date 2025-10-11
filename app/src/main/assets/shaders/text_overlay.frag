precision mediump float;
uniform sampler2D u_Texture;
uniform vec4 u_Color;
varying vec2 v_TexCoord;
void main() {
    vec4 texColor = texture2D(u_Texture, v_TexCoord);
    gl_FragColor = vec4(u_Color.rgb, texColor.a * u_Color.a);
}