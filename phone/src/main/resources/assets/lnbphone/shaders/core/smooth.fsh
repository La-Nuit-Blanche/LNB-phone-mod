#version 150

// Vanilla's position_tex_color, without its "alpha < 0.1: discard": the smooth layer's faint overlays (4 to 8%),
// shadow tails and the lightest anti-aliased edge pixels live below that cut. Only fully transparent texels are
// dropped, so they still leave the depth buffer alone.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
