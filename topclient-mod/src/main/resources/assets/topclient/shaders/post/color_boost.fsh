#version 330

// TopClient Colour Boost. The slider values live in a tiny 2x1 texture (ParamsSampler)
// that the mod updates whenever you move a slider, so changes show up instantly.
//   pixel 0: r = saturation 0..200%, g = vibrance 0..100, b = contrast -50..50, a = brightness -50..50
//   pixel 1: r = warmth -50..50, g = strength 0..100%, b = tint -50..50
uniform sampler2D InSampler;
uniform sampler2D ParamsSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 p0 = texelFetch(ParamsSampler, ivec2(0, 0), 0);
    vec4 p1 = texelFetch(ParamsSampler, ivec2(1, 0), 0);
    float saturation = p0.r * 2.0;          // 1.0 = unchanged
    float vibrance   = p0.g;                // 0..1
    float contrast   = p0.b - 0.5;          // -0.5..0.5
    float brightness = (p0.a - 0.5) * 0.4;  // -0.2..0.2
    float warmth     = (p1.r - 0.5) * 0.25;
    float strength   = p1.g;
    float tint       = (p1.b - 0.5) * 0.2;

    vec3 base = texture(InSampler, texCoord).rgb;
    vec3 c = base;
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(luma), c, saturation);
    float mx = max(c.r, max(c.g, c.b));
    float mn = min(c.r, min(c.g, c.b));
    float chroma = mx - mn;
    luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(luma), c, 1.0 + vibrance * 1.5 * (1.0 - chroma));
    c = (c - 0.5) * (1.0 + contrast) + 0.5;
    c += brightness;
    c += vec3(warmth, warmth * 0.3 - tint, -warmth + tint * 0.5);
    c = mix(base, clamp(c, 0.0, 1.0), strength);
    fragColor = vec4(c, 1.0);
}
