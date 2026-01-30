#version 300 es
precision mediump float;

in vec2 vTexCoord;
out vec4 outColor;

uniform sampler2D uInputTexture;
uniform mediump sampler3D uLutTexture;
uniform sampler2D uGrainTexture;
uniform float uIntensity; // 0.0 = original, 1.0 = full LUT effect
uniform float uGrainIntensity; // 0.0 = no grain, 1.0 = full grain
uniform float uGrainScale; // Grain texture tiling scale
uniform float uTime; // For grain animation (optional)

void main() {
    vec4 originalColor = texture(uInputTexture, vTexCoord);
    
    // 3D LUT lookup
    vec3 lutColor = texture(uLutTexture, originalColor.rgb).rgb;
    
    // Blend between original and LUT based on intensity
    vec3 finalColor = mix(originalColor.rgb, lutColor, uIntensity);
    
    // Apply film grain if enabled
    if (uGrainIntensity > 0.001) {
        // Sample grain texture with scaling and optional time offset for variation
        vec2 grainCoord = vTexCoord * uGrainScale + vec2(uTime * 0.1);
        vec3 grain = texture(uGrainTexture, grainCoord).rgb;
        
        // Convert grain from 0-1 to -0.5 to 0.5 range for additive blend
        vec3 grainOffset = (grain - 0.5) * 2.0;
        
        // Apply grain with luminance-aware blending (less grain in shadows)
        float luminance = dot(finalColor, vec3(0.299, 0.587, 0.114));
        float grainMask = smoothstep(0.0, 0.3, luminance) * smoothstep(1.0, 0.7, luminance);
        
        finalColor += grainOffset * uGrainIntensity * grainMask * 0.15;
        finalColor = clamp(finalColor, 0.0, 1.0);
    }
    
    outColor = vec4(finalColor, originalColor.a);
}
