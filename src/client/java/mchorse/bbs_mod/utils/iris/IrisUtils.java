package mchorse.bbs_mod.utils.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.DataPath;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.LanguageMap;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuContainer;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuElement;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuElementScreen;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuLinkElement;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuOptionElement;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.FloatCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.IntCachedUniform;
import net.irisshaders.iris.vertices.ImmediateState;
import net.irisshaders.iris.vertices.IrisExtendedBufferBuilder;
import net.minecraft.client.render.BufferBuilder;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything BBS asks of Iris, in one class that is only ever touched when the Iris mod is actually
 * present ({@code BBSRendering.iris}). Loading it without Iris on the classpath would throw, so every
 * caller has to go through {@link mchorse.bbs_mod.client.BBSRendering}, which gates on that flag —
 * class loading is lazy, so a gated call site never resolves these names on a plain install.
 *
 * <p>Deliberately thin. The 1.21.1 integration also carried PBR texture wrappers, which lean on Iris
 * internals the 1.21.5+ rewrite reshaped and stay decoupled for now. Shader curves — the pack's own
 * {@code #define} options driven by a camera clip — are back; see {@link ShaderCurves}.
 */
public class IrisUtils
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The pack's properties, captured while {@code ShaderPack} builds itself.
     *
     * <p>There is no getter for them, and asking {@code Iris.getCurrentPack()} is too late: the GLSL
     * sources are preprocessed inside that same constructor, and {@link ShaderCurves} has to know the
     * pack's {@code sliders} list by then to decide which {@code #define} it may turn into a uniform.
     * So {@code ShaderPackMixin} hands them over the moment the field is written.</p>
     */
    private static ShaderProperties properties;

    private static boolean warnedNoProperties;

    public static void setShaderProperties(ShaderProperties shaderProperties)
    {
        properties = shaderProperties;
    }

    /** Options the pack declares as sliders — the only {@code #define}s BBS offers as curves. */
    public static List<String> getSliderProperties()
    {
        if (properties == null)
        {
            /* Not an error before a pack is loaded, but if it holds while sources are being
             * preprocessed the hook missed and every curve silently disappears — say so once. */
            if (!warnedNoProperties)
            {
                warnedNoProperties = true;

                LOGGER.info("[BBS shaders] no shaderpack properties captured yet — curves have nothing to filter against");
            }

            return Collections.emptyList();
        }

        return properties.getSliderOptions();
    }

    /**
     * The pack's own option translations, flattened to {@code option.<id>} to
     * {@code "Screen > Sub > Name"}, so the curve picker can show a pack option under the name its own
     * menu uses instead of the raw macro.
     */
    public static Map<String, String> getShadersLanguageMap(String language)
    {
        if (Iris.getCurrentPack().isEmpty())
        {
            return Collections.emptyMap();
        }

        Map<String, String> map = new HashMap<>();
        ShaderPack shaderPack = Iris.getCurrentPack().get();
        LanguageMap languageMap = shaderPack.getLanguageMap();

        Map<String, String> target = languageMap.getTranslations(language);
        Map<String, String> fallback = languageMap.getTranslations("en_us");
        final String prefix = "option.";

        Map<String, DataPath> pathMap = new HashMap<>();

        collectPaths(pathMap, shaderPack.getMenuContainer(), shaderPack.getMenuContainer().mainScreen, Collections.emptyList());
        fillInPaths(map, fallback, pathMap, prefix);
        fillInPaths(map, target, pathMap, prefix);

        return map;
    }

    private static void fillInPaths(Map<String, String> map, Map<String, String> language, Map<String, DataPath> pathMap, String prefix)
    {
        if (language == null)
        {
            return;
        }

        for (Map.Entry<String, String> entry : language.entrySet())
        {
            if (entry.getKey().startsWith(prefix))
            {
                String optionId = entry.getKey().substring(prefix.length());
                DataPath path = pathMap.get(optionId);
                String value = entry.getValue();

                if (path != null)
                {
                    List<String> translations = new ArrayList<>();

                    for (int i = 0, c = path.strings.size(); i < c; i++)
                    {
                        String string = path.strings.get(i);

                        if (i == c - 1) translations.add(value);
                        else translations.add(language.getOrDefault("screen." + string, string));
                    }

                    value = String.join(" > ", translations);
                }

                map.put(entry.getKey(), value);
            }
        }
    }

    private static void collectPaths(Map<String, DataPath> pathMap, OptionMenuContainer container, OptionMenuElementScreen mainScreen, List<String> prefix)
    {
        for (OptionMenuElement element : mainScreen.elements)
        {
            if (element instanceof OptionMenuOptionElement option)
            {
                ArrayList<String> strings = new ArrayList<>(prefix);

                strings.add(option.optionId);
                pathMap.put(option.optionId, new DataPath(strings));
            }
            else if (element instanceof OptionMenuLinkElement link)
            {
                OptionMenuElementScreen screen = container.subScreens.get(link.targetScreenId);

                if (screen != null)
                {
                    ArrayList<String> strings = new ArrayList<>(prefix);

                    strings.add(link.targetScreenId);
                    collectPaths(pathMap, container, screen, strings);
                }
            }
        }
    }

    /**
     * Publish every parsed curve variable as a custom Iris uniform, appended to the pack's own
     * {@code uniformOrder} right after it is built. {@link ShaderCurves} already rewrote each macro
     * reference in the source into {@code bbs_<name>} and declared it as a uniform, so from Iris's side
     * these are ordinary custom uniforms: it resolves a location per program and pushes them per frame.
     *
     * <p>{@code ShaderVariable.getValue()} consumes the value the curve clip left for this frame and
     * falls back to the pack's own default afterwards, so a clip that stops writing releases the option
     * instead of freezing it.</p>
     */
    public static void addUniforms(List<CachedUniform> list, Map<String, ShaderCurves.ShaderVariable> variableMap)
    {
        for (ShaderCurves.ShaderVariable value : variableMap.values())
        {
            if (value.integer)
            {
                list.add(new IntCachedUniform(value.uniformName, UniformUpdateFrequency.PER_FRAME, () -> (int) value.getValue()));
            }
            else
            {
                list.add(new FloatCachedUniform(value.uniformName, UniformUpdateFrequency.PER_FRAME, value::getValue));
            }
        }
    }

    public static boolean isShaderPackEnabled()
    {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    public static boolean isShadowPass()
    {
        return IrisApi.getInstance().isRenderingShadowPass();
    }

    /**
     * Tell Iris whether the main render target is bound.
     *
     * <p>It gates {@code IrisRenderingPipeline.shouldOverrideShaders()}, which is
     * {@code isRenderingWorld && isMainBound}, and that in turn decides
     * {@code MixinCompiledShaderProgram.iris$shouldSkipThis()}:
     *
     * <pre>return !(this instanceof ExtendedShader) &amp;&amp; !(this instanceof FallbackShader) &amp;&amp; shouldOverrideShaders();</pre>
     *
     * <p>Read it plainly: while the world is being drawn into the main target, Iris SKIPS every
     * program that is not one of its own. BBS draws its forms with its own programs, so under a
     * shaderpack the draws were thrown away — some forms gone, the ones that ride vanilla layers still
     * there, which is what "some show, some look transparent" was.
     *
     * <p>Iris keeps this flag itself from every render-target bind (MixinRenderTarget), so it is only
     * ever ours for the span we set it.
     */
    public static void setMainBound(boolean bound)
    {
        WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();

        if (pipeline != null)
        {
            pipeline.setIsMainBound(bound);
        }
    }

    /**
     * Match the vertex layout of an upload to the buffer actually being uploaded.
     *
     * <p>Iris picks that layout twice. A buffer's own format is chosen when the render layer
     * begins: it gains tangents and mid-texture coordinates while the level renders, and stays
     * plain vanilla anywhere else (a form editor viewport, an item in a GUI). The vertex array's
     * layout is chosen again inside {@link net.minecraft.client.render.VertexFormat#setupState()},
     * and there Iris reads a flag instead — a plain entity format is set up with the
     * <em>extended</em> stride whenever that flag is up. The two agree only because Iris drops the
     * flag for the duration of the immediate provider's draw, the one place vanilla ever uploads
     * a buffer of the second kind.</p>
     *
     * <p>So anything that ends and uploads such a buffer by itself has to keep the pair honest,
     * or the vertex array reads 36 byte vertices at the extended stride and the geometry tears
     * into a fan of stretched triangles. The buffer knows which of the two it is, so ask it
     * rather than repeating Iris' reasoning about it.</p>
     */
    public static boolean beginBufferUpload(BufferBuilder builder)
    {
        boolean extended = ImmediateState.renderWithExtendedVertexFormat;

        if (builder instanceof IrisExtendedBufferBuilder buffer && !buffer.iris$extending())
        {
            ImmediateState.renderWithExtendedVertexFormat = false;
        }

        return extended;
    }

    public static void endBufferUpload(boolean extended)
    {
        ImmediateState.renderWithExtendedVertexFormat = extended;
    }

    /**
     * Tell Iris which of the shaderpack's programs a BBS pipeline should be drawn with.
     *
     * <p>The substitution itself is dynamic — {@code MixinShaderManager_Overrides} consults the
     * assignment map every time a pass resolves its program — so assigning at registration time works
     * no matter when the pack loads. Iris throws if a pipeline is assigned twice, so assignment
     * happens once per pipeline, at the point each is registered.
     */
    public static void assignPipeline(RenderPipeline pipeline, BBSRendering.IrisProgramKind kind)
    {
        IrisApi.getInstance().assignPipeline(pipeline, translate(kind));
    }

    /**
     * Give {@code pipeline} the exact shaderpack treatment Iris already gives {@code prototype}, a
     * vanilla pipeline — the strictly better alternative to naming a program kind, for three reasons
     * read straight out of {@code IrisPipelines}:
     *
     * <ul>
     *   <li><b>It covers the shadow pass.</b> {@code assignPipeline} writes only {@code coreShaderMap};
     *       {@code coreShaderMapShadow} keeps its vanilla entries only, so an assigned pipeline resolves
     *       to a null key while shadows render and the geometry casts none. {@code copyPipeline} copies
     *       BOTH maps.</li>
     *   <li><b>It keeps Iris's own dynamic choice.</b> The vanilla entries are functions, not constants:
     *       the entity ones return a HAND_* key while the hand renderer is active and a BLOCK_ENTITY_*
     *       key inside the block-entity phase. Copying carries that function over; naming a kind freezes
     *       one key for every phase.</li>
     *   <li><b>It sidesteps {@code ShaderKey.findBestMatch}.</b> That helper returns the FIRST enum
     *       constant matching the program family, which for ENTITIES is {@code ENTITIES_ALPHA} — whose
     *       alpha test is {@code VERTEX_ALPHA}, compiled as
     *       {@code if (!(fragColor.a > iris_vertexColorAlpha)) discard;}. That reads the vertex alpha as
     *       a THRESHOLD, so a fully opaque form (alpha == 1) discards every one of its fragments and
     *       vanishes, while alpha == 0.99 lets the opaque texels through. That was the "forms disappear
     *       under shaders, but show at 99% opacity" bug.</li>
     * </ul>
     *
     * <p>Unlike {@code assignPipeline} this one does not throw when a pipeline is already known, so it
     * is safe to call more than once for the same pipeline.
     */
    public static void copyPipeline(RenderPipeline prototype, RenderPipeline pipeline)
    {
        IrisPipelines.copyPipeline(prototype, pipeline);
    }

    /**
     * Which of the pack's programs a kind means. Chosen to follow the VERTEX FORMAT, the way vanilla's
     * own pipelines map: a pack's entity program reads colour, both light coordinates and a normal, so
     * handing it geometry that carries only position and a colour would have it read attributes that
     * are not there. Iris guesses this itself for anything shaped like an entity draw ("Found *decent*
     * program match ... ENTITIES_ALPHA" in the log) and gives up on the plainer formats ("Missing
     * program ... in override list") — those are the ones that have to be named here.
     */
    private static IrisProgram translate(BBSRendering.IrisProgramKind kind)
    {
        return switch (kind)
        {
            case ENTITY -> IrisProgram.ENTITIES;
            case ENTITY_TRANSLUCENT -> IrisProgram.ENTITIES_TRANSLUCENT;
            case PARTICLE -> IrisProgram.PARTICLES;
            case TEXTURED -> IrisProgram.TEXTURED;
            case LINES -> IrisProgram.LINES;
            case BASIC -> IrisProgram.BASIC;
        };
    }
}
