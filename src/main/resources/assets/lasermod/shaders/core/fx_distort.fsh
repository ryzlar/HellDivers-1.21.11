#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:globals.glsl>

// Screen-space refraction for world-anchored distortion volumes (heat haze, spatial lensing).
// The sprite itself sits in the world and is depth tested; it re-draws the scene behind it with bent UVs.
//   Color.r: lens strength, 0.5 = none, > 0.5 pulls the image toward the centre (bulge), < 0.5 pushes it out (pinch)
//   Color.g: heat shimmer strength
//   Color.b: swirl (0.5 = none)
//   Color.a: overall strength

uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;

in vec4 vertexColor;
in vec2 local;

out vec4 fragColor;

float linearDepth(float depth) {
    float ndc = depth * 2.0 - 1.0;
    return ProjMat[3][2] / (ndc + ProjMat[2][2]);
}

void main() {
    float d = length(local);
    if (d >= 1.0) {
        discard;
    }
    vec2 size = vec2(textureSize(SceneColor, 0));
    vec2 uv = gl_FragCoord.xy / size;
    float mask = 1.0 - smoothstep(0.45, 1.0, d);
    float strength = vertexColor.a * mask;

    float lens = (vertexColor.r - 0.5) * 2.0;
    float shimmer = vertexColor.g;
    float swirl = (vertexColor.b - 0.5) * 2.0;
    float time = GameTime * 24000.0;

    // Lens: a radial displacement that is strongest half way out (an Einstein-ring-like band for negative values)
    vec2 offset = -local * lens * 0.08 * sin(d * 3.14159);
    // Swirl: rotate the sample point around the centre, more toward the middle
    float angle = swirl * 1.2 * (1.0 - d) * (1.0 - d);
    vec2 rotated = vec2(local.x * cos(angle) - local.y * sin(angle), local.x * sin(angle) + local.y * cos(angle));
    offset += (rotated - local) * 0.06;
    // Heat shimmer: layered waves rising upward
    vec2 p = uv * vec2(size.x / size.y, 1.0) * 55.0;
    offset += shimmer * 0.0035 * vec2(
            sin(p.y * 1.3 - time * 0.55) + 0.6 * sin(p.x * 0.7 + p.y * 2.1 - time * 0.9),
            cos(p.x * 1.1 - time * 0.45) + 0.6 * sin(p.y * 1.9 - time * 0.8));
    offset *= strength;

    vec2 sampleUv = clamp(uv + offset, vec2(0.001), vec2(0.999));
    // Never pull in something that is in front of the distortion itself (e.g. a hand or a nearby block)
    float self = linearDepth(gl_FragCoord.z);
    if (linearDepth(texture(SceneDepth, sampleUv).r) < self - 0.25) {
        sampleUv = uv;
    }
    vec3 scene = texture(SceneColor, sampleUv).rgb;
    fragColor = vec4(scene, min(1.0, strength * 4.0)) * vec4(ColorModulator.rgb, 1.0);
}
