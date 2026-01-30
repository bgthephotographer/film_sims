#version 300 es
in vec4 aPosition;
in vec2 aTexCoord;
out vec2 vTexCoord;

uniform vec2 uScale;    // Aspect ratio correction
uniform float uZoom;    // User zoom factor
uniform vec2 uOffset;   // User pan offset

void main() {
    // Apply aspect ratio scale first, then zoom, then offset
    vec2 pos = vec2(aPosition.x * uScale.x, aPosition.y * uScale.y);
    pos = pos * uZoom;
    pos = pos + uOffset;
    
    gl_Position = vec4(pos, 0.0, 1.0);
    vTexCoord = aTexCoord;
}