#version 330

// Clover Client motion blur: this frame mixed with the previous (already blurred) frame.
uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

layout(std140) uniform MotionBlurConfig {
    float BlendFactor;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 now = texture(InSampler, texCoord).rgb;
    vec3 before = texture(PrevSampler, texCoord).rgb;
    fragColor = vec4(mix(now, before, BlendFactor), 1.0);
}
