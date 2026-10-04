package mchorse.bbs_mod.utils.iris;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ShaderCurvesSourceTest
{
    private Map<String, ShaderCurves.ShaderVariable> candidates(String... names)
    {
        Map<String, ShaderCurves.ShaderVariable> values = new LinkedHashMap<>();
        for (String name : names) values.put(name, new ShaderCurves.ShaderVariable(name, "0.5", false));
        return values;
    }

    @Test public void animatedOptionsPreserveDefinitionsCommentsAndIdentifierBoundaries()
    {
        String source = "#version 120\n#define EXPOSURE 0.5 // [0.0 0.5 1.0]\n// EXPOSURE\n/* EXPOSURE */\nvoid main(){ float EXPOSURE_EXTRA = EXPOSURE; }\n";
        String result = ShaderCurves.rewrite(source, candidates("EXPOSURE"), false);
        assertTrue(result.contains("uniform float bbs_EXPOSURE;"));
        assertTrue(result.contains("#define EXPOSURE 0.5"));
        assertTrue(result.contains("// EXPOSURE"));
        assertTrue(result.contains("/* EXPOSURE */"));
        assertTrue(result.contains("EXPOSURE_EXTRA = bbs_EXPOSURE;"));
    }

    @Test public void preprocessorArrayDimensionsAndSwitchLabelsStayConstant()
    {
        String source = "#version 120\n#define QUALITY 1\n#define SAMPLES 4\n#define MODE 2\n#define SCALE 0.5\n#if QUALITY == 1\n#endif\nfloat samples[SAMPLES];\nvoid main(){switch(1){case MODE: break;} gl_FragColor=vec4(SCALE);}";
        String result = ShaderCurves.rewrite(source, candidates("QUALITY", "SAMPLES", "MODE", "SCALE"), false);
        assertFalse(result.contains("uniform float bbs_QUALITY"));
        assertFalse(result.contains("uniform float bbs_SAMPLES"));
        assertFalse(result.contains("uniform float bbs_MODE"));
        assertTrue(result.contains("vec4(bbs_SCALE)"));
    }

    @Test public void macroAliasesRemainCompileTimeAndConstDependenciesPropagate()
    {
        String source = "#version 120\n#define AMBIENT 0.5\n#define QUALITY 2\n#define ALIAS QUALITY\nconst float first = AMBIENT;\nconst float second = first*2.0;\nfloat f(const float inputValue){return inputValue+second;}";
        String result = ShaderCurves.rewrite(source, candidates("AMBIENT", "QUALITY"), false);
        assertFalse(result.contains("bbs_QUALITY"));
        assertTrue(result.contains("float first = bbs_AMBIENT;"));
        assertTrue(result.contains("float second = first*2.0;"));
        assertTrue(result.contains("f(const float inputValue)"));
    }

    @Test public void packReloadDropsVariablesAndProgramLocations()
    {
        ShaderCurves.rewrite("#version 120\n#define AMBIENT 0.5\nvoid main(){gl_FragColor=vec4(AMBIENT);}", candidates("AMBIENT"), true);
        assertTrue(ShaderCurves.variableMap.containsKey("AMBIENT"));
        ShaderCurves.reset();
        assertTrue(ShaderCurves.variableMap.isEmpty());
        assertTrue(ShaderCurves.getPrograms().isEmpty());
    }
}
