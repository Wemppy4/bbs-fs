#version 120
uniform sampler2D colortex0;
varying vec2 texcoord;
#define BBS_QA_EXPOSURE 1.0 // [0.5 1.0 1.5 2.0]
#define BBS_QA_BLUE 1.0 // [0.5 1.0 1.5 2.0]
#define BBS_QA_SAMPLES 2 // [1 2 3 4]
#if BBS_QA_SAMPLES > 1
const float qaSamples = 1.0;
#else
const float qaSamples = 0.5;
#endif
void main()
{
    vec4 color = texture2D(colortex0, texcoord);
    color.rgb *= BBS_QA_EXPOSURE * qaSamples;
    color.b *= BBS_QA_BLUE;
    gl_FragColor = color;
}
