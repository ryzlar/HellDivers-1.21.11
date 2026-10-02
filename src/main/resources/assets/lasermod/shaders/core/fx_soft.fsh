#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Lasermod world effects (fire, smoke, energy, shockwaves).
// Soft depth: instead of being sliced where a flat effect surface passes through terrain, the effect fades out
// over `softness` blocks as it approaches the scene behind it, so effects blend into the ground they touch.

uniform sampler2D SceneDepth;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in float softness;

out vec4 fragColor;

float linearDepth(float depth) {
    float ndc = depth * 2.0 - 1.0;
    return ProjMat[3][2] / (ndc + ProjMat[2][2]);
}

void main() {
    vec4 color = vertexColor * ColorModulator;
    if (softness > 0.0) {
        vec2 uv = gl_FragCoord.xy / vec2(textureSize(SceneDepth, 0));
        float scene = linearDepth(texture(SceneDepth, uv).r);
        float self = linearDepth(gl_FragCoord.z);
        float fade = clamp((scene - self) / softness, 0.0, 1.0);
        color.a *= fade * fade * (3.0 - 2.0 * fade);
    }
    color.a *= 1.0 - total_fog_value(sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    if (color.a < 0.002) {
        discard;
    }
    fragColor = color;
}
