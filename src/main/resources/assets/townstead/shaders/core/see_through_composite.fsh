#version 150

// See-through parts composed on their own: colour already blended (premultiplied), depth of their nearest face.
uniform sampler2D PartsSampler;
uniform sampler2D PartsDepthSampler;
// The frame's depth now, see-through blocks and particles included.
uniform sampler2D SceneDepthSampler;
// How much of a part shows where something see-through lies in front of it; 1 leaves it whole.
uniform float Under;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(PartsSampler, texCoord);
    if (color.a <= 0.0) discard;
    float depth = texture(PartsDepthSampler, texCoord).r;
    if (Under < 1.0 && texture(SceneDepthSampler, texCoord).r < depth) color *= Under;
    fragColor = color;
    gl_FragDepth = depth;
}
