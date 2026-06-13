package mchorse.bbs_mod.ui.particles.sections;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.math.Constant;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.math.molang.expressions.MolangValue;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.particles.components.appearance.ParticleComponentAppearanceLighting;
import mchorse.bbs_mod.particles.components.appearance.ParticleComponentAppearanceTinting;
import mchorse.bbs_mod.particles.components.appearance.colors.Gradient;
import mchorse.bbs_mod.particles.components.appearance.colors.Solid;
import mchorse.bbs_mod.particles.components.appearance.colors.Tint;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.particles.utils.UIGradientEditor;
import mchorse.bbs_mod.ui.particles.utils.UIMolangExpression;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.Arrays;

public class UIParticleSchemeLightingSection extends UIParticleSchemeSection
{
    public UICirculate mode;
    public UIColor color;
    public UIMolangExpression r;
    public UIMolangExpression g;
    public UIMolangExpression b;
    public UIMolangExpression a;
    public UIToggle lighting;

    public UIGradientEditor gradientEditor;
    public UIElement gradient;
    public UIColor gradientColor;
    public UIMolangExpression gradientInterpolant;

    public UIElement channels;

    private ParticleComponentAppearanceTinting component;
    private Tint[] cache = new Tint[3];
    private int previous;

    public UIParticleSchemeLightingSection(UIParticleSchemePanel parent)
    {
        super(parent);

        this.mode = new UICirculate((b) -> this.changeMode(b.getValue()));
        this.mode.addLabel(UIKeys.SNOWSTORM_LIGHTING_SOLID);
        this.mode.addLabel(UIKeys.SNOWSTORM_LIGHTING_EXPRESSION);
        this.mode.addLabel(UIKeys.SNOWSTORM_LIGHTING_GRADIENT);

        this.color = new UIColor((color) ->
        {
            Solid solid = this.getSolid();
            Color original = this.color.picker.color;

            solid.r = this.set(solid.r, original.r);
            solid.g = this.set(solid.g, original.g);
            solid.b = this.set(solid.b, original.b);
            solid.a = this.set(solid.a, original.a);
            this.editor.dirty();
        });
        this.color.withAlpha();

        this.r = new UIMolangExpression(() -> this.component.color instanceof Solid ? this.getSolid().r : null, (b) ->
        {
            Solid solid = this.getSolid();

            this.editMoLang("lighting.r", (str) -> solid.r = this.parse(str, solid.r), solid.r);
        });
        this.r.icon(Icons.COLOR).barColor(Colors.RED).tooltip(UIKeys.SNOWSTORM_LIGHTING_RED);

        this.g = new UIMolangExpression(() -> this.component.color instanceof Solid ? this.getSolid().g : null, (b) ->
        {
            Solid solid = this.getSolid();

            this.editMoLang("lighting.g", (str) -> solid.g = this.parse(str, solid.g), solid.g);
        });
        this.g.icon(Icons.COLOR).barColor(Colors.GREEN).tooltip(UIKeys.SNOWSTORM_LIGHTING_GREEN);

        this.b = new UIMolangExpression(() -> this.component.color instanceof Solid ? this.getSolid().b : null, (b) ->
        {
            Solid solid = this.getSolid();

            this.editMoLang("lighting.b", (str) -> solid.b = this.parse(str, solid.b), solid.b);
        });
        this.b.icon(Icons.COLOR).barColor(Colors.BLUE).tooltip(UIKeys.SNOWSTORM_LIGHTING_BLUE);

        this.a = new UIMolangExpression(() -> this.component.color instanceof Solid ? this.getSolid().a : null, (b) ->
        {
            Solid solid = this.getSolid();

            this.editMoLang("lighting.a", (str) -> solid.a = this.parse(str, solid.a), solid.a);
        });
        this.a.icon(Icons.DROP).barColor(Colors.WHITE).tooltip(UIKeys.SNOWSTORM_LIGHTING_ALPHA);

        this.lighting = new UIToggle(UIKeys.SNOWSTORM_LIGHTING_LIGHTING, (b) ->
        {
            if (b.getValue())
            {
                this.scheme.getOrCreate(ParticleComponentAppearanceLighting.class);
            }
            else
            {
                this.scheme.remove(ParticleComponentAppearanceLighting.class);
            }

            this.editor.dirty();
        });

        this.gradientColor = new UIColor(this::setGradientColor).withAlpha();
        this.gradientEditor = new UIGradientEditor(this, this.gradientColor);
        this.gradientInterpolant = new UIMolangExpression(() -> this.component.color instanceof Gradient ? ((Gradient) this.component.color).interpolant : null, (b) ->
        {
            Gradient gradient = (Gradient) this.component.color;

            this.editMoLang("lighting.interpolant", (str) -> gradient.interpolant = this.parse(str, gradient.interpolant), gradient.interpolant);
        });
        this.gradientInterpolant.icon(Icons.CURVES).tooltip(UIKeys.SNOWSTORM_LIGHTING_INTERPOLANT);
        this.gradient = new UIElement();
        this.gradient.column(UIConstants.MARGIN).vertical().stretch();
        this.gradient.add(this.gradientColor, this.gradientInterpolant);

        UILabel label = UI.label(UIKeys.SNOWSTORM_MODE, 20).labelAnchor(0, 0.5F);

        this.channels = new UIElement();
        this.channels.column(UIConstants.MARGIN).vertical().stretch();
        this.channels.add(this.r, this.g, this.b, this.a);

        this.fields.add(this.lighting);
        this.fields.add(UI.row(5, 0, 20, label, this.mode));
    }

    private void changeMode(int value)
    {
        if (this.cache[this.previous] == null)
        {
            this.cache[this.previous] = this.component.color;
        }

        Tint cached = this.cache[value];

        if (cached == null)
        {
            if (value == 2)
            {
                cached = new Gradient();
            }
            else
            {
                cached = new Solid();
            }

            this.cache[value] = cached;
        }

        this.component.color = cached;

        this.fillData();
        this.editor.dirty();

        this.previous = value;
    }

    private void setGradientColor(int color)
    {
        this.gradientEditor.setColor(color);
    }

    private MolangExpression set(MolangExpression expression, float value)
    {
        if (expression == MolangParser.ZERO || expression == MolangParser.ONE)
        {
            return new MolangValue(null, new Constant(value));
        }

        if (!(expression instanceof MolangValue))
        {
            expression = new MolangValue(null, new Constant(0));
        }

        MolangValue v = (MolangValue) expression;

        if (!(v.expression instanceof Constant))
        {
            v.expression = new Constant(0);
        }

        v.expression.set(value);

        return expression;
    }

    @Override
    public IKey getTitle()
    {
        return UIKeys.SNOWSTORM_LIGHTING_TITLE;
    }

    private Solid getSolid()
    {
        return (Solid) this.component.color;
    }

    @Override
    public void beforeSave(ParticleScheme scheme)
    {
        if (this.lighting.getValue())
        {
            scheme.getOrCreate(ParticleComponentAppearanceLighting.class);
        }
        else
        {
            scheme.remove(ParticleComponentAppearanceLighting.class);
        }
    }

    @Override
    public void setScheme(ParticleScheme scheme)
    {
        super.setScheme(scheme);

        Arrays.fill(this.cache, null);

        this.component = scheme.getOrCreate(ParticleComponentAppearanceTinting.class);
        this.lighting.setValue(scheme.get(ParticleComponentAppearanceLighting.class) != null);

        if (this.component.color instanceof Solid)
        {
            Solid solid = this.getSolid();

            if (solid.isConstant())
            {
                this.setMode(0);
            }
            else
            {
                this.setMode(1);
            }
        }
        else if (this.component.color instanceof Gradient)
        {
            this.setMode(2);
        }

        this.fillData();
    }

    private void setMode(int value)
    {
        this.previous = value;

        this.mode.setValue(value);
    }

    public void fillData()
    {
        this.gradientEditor.removeFromParent();
        this.gradient.removeFromParent();
        this.color.removeFromParent();
        this.color.picker.removeFromParent();
        this.channels.removeFromParent();

        if (this.mode.getValue() == 0)
        {
            Solid solid = (Solid) this.component.color;

            this.color.picker.color.set((float) solid.r.get(), (float) solid.g.get(), (float) solid.b.get(), (float) solid.a.get());

            this.fields.add(this.color);
        }
        else if (this.mode.getValue() == 2)
        {
            this.gradientEditor.setGradient((Gradient) this.component.color);

            this.fields.add(this.gradientEditor);
            this.fields.add(this.gradient);
        }
        else
        {
            this.fields.add(this.channels);
        }

        this.resizeParent();
    }
}