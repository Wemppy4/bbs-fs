package mchorse.bbs_mod.ui.particles.sections;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.particles.components.appearance.ParticleComponentAppearanceBillboard;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.particles.utils.UIMolangExpression;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIParticleSchemeAppearanceSection extends UIParticleSchemeComponentSection<ParticleComponentAppearanceBillboard>
{
    public UICirculate mode;
    public UILabel modeLabel;

    public UIMolangExpression sizeW;
    public UIMolangExpression sizeH;
    public UIMolangExpression uvX;
    public UIMolangExpression uvY;
    public UIMolangExpression uvW;
    public UIMolangExpression uvH;

    public UIElement flipbook;
    public UITrackpad stepX;
    public UITrackpad stepY;
    public UITrackpad fps;
    public UIMolangExpression max;
    public UIToggle stretch;
    public UIToggle loop;

    public UIParticleSchemeAppearanceSection(UIParticleSchemePanel parent)
    {
        super(parent);

        this.mode = new UICirculate((b) ->
        {
            this.component.flipbook = this.mode.getValue() == 1;
            this.updateElements();
            this.editor.dirty();
        });
        this.mode.addLabel(UIKeys.SNOWSTORM_APPEARANCE_REGULAR);
        this.mode.addLabel(UIKeys.SNOWSTORM_APPEARANCE_ANIMATED);
        this.modeLabel = UI.label(UIKeys.SNOWSTORM_MODE, 20).labelAnchor(0, 0.5F);

        this.sizeW = new UIMolangExpression(() -> this.component.sizeW, (b) -> this.editMoLang("appearance.width", (str) -> this.component.sizeW = this.parse(str, this.component.sizeW), this.component.sizeW));
        this.sizeW.icon(Icons.HORIZONTAL).barColor(Colors.RED).tooltip(UIKeys.SNOWSTORM_APPEARANCE_WIDTH);
        this.sizeH = new UIMolangExpression(() -> this.component.sizeH, (b) -> this.editMoLang("appearance.height", (str) -> this.component.sizeH = this.parse(str, this.component.sizeH), this.component.sizeH));
        this.sizeH.icon(Icons.VERTICAL).barColor(Colors.GREEN).tooltip(UIKeys.SNOWSTORM_APPEARANCE_HEIGHT);

        this.uvX = new UIMolangExpression(() -> this.component.uvX, (b) -> this.editMoLang("appearance.uv_x", (str) -> this.component.uvX = this.parse(str, this.component.uvX), this.component.uvX));
        this.uvX.icon(Icons.X).barColor(Colors.RED).tooltip(UIKeys.SNOWSTORM_APPEARANCE_UV_X);
        this.uvY = new UIMolangExpression(() -> this.component.uvY, (b) -> this.editMoLang("appearance.uv_y", (str) -> this.component.uvY = this.parse(str, this.component.uvY), this.component.uvY));
        this.uvY.icon(Icons.Y).barColor(Colors.GREEN).tooltip(UIKeys.SNOWSTORM_APPEARANCE_UV_Y);
        this.uvW = new UIMolangExpression(() -> this.component.uvW, (b) -> this.editMoLang("appearance.uv_w", (str) -> this.component.uvW = this.parse(str, this.component.uvW), this.component.uvW));
        this.uvW.icon(Icons.HORIZONTAL).barColor(Colors.RED).tooltip(UIKeys.SNOWSTORM_APPEARANCE_UV_W);
        this.uvH = new UIMolangExpression(() -> this.component.uvH, (b) -> this.editMoLang("appearance.uv_h", (str) -> this.component.uvH = this.parse(str, this.component.uvH), this.component.uvH));
        this.uvH.icon(Icons.VERTICAL).barColor(Colors.GREEN).tooltip(UIKeys.SNOWSTORM_APPEARANCE_UV_H);

        this.stepX = new UITrackpad((value) ->
        {
            this.component.stepX = value.floatValue();
            this.editor.dirty();
        });
        this.stepX.tooltip(UIKeys.SNOWSTORM_APPEARANCE_STEP_X);
        this.stepY = new UITrackpad((value) ->
        {
            this.component.stepY = value.floatValue();
            this.editor.dirty();
        });
        this.stepY.tooltip(UIKeys.SNOWSTORM_APPEARANCE_STEP_Y);
        this.fps = new UITrackpad((value) ->
        {
            this.component.fps = value.floatValue();
            this.editor.dirty();
        });
        this.fps.tooltip(UIKeys.SNOWSTORM_APPEARANCE_FPS);
        this.max = new UIMolangExpression(() -> this.component.maxFrame, (b) -> this.editMoLang("appearance.max_frames", (str) -> this.component.maxFrame = this.parse(str, this.component.maxFrame), this.component.maxFrame));
        this.max.icon(Icons.GALLERY).tooltip(UIKeys.SNOWSTORM_APPEARANCE_MAX);

        this.stretch = new UIToggle(UIKeys.SNOWSTORM_APPEARANCE_STRETCH, (b) ->
        {
            this.component.stretchFPS = b.getValue();
            this.editor.dirty();
        });
        this.stretch.tooltip(UIKeys.SNOWSTORM_APPEARANCE_STRETCH_TOOLTIP);
        this.loop = new UIToggle(UIKeys.SNOWSTORM_APPEARANCE_LOOP, (b) ->
        {
            this.component.loop = b.getValue();
            this.editor.dirty();
        });
        this.loop.tooltip(UIKeys.SNOWSTORM_APPEARANCE_LOOP_TOOLTIP);

        this.flipbook = new UIElement();
        this.flipbook.column().vertical().stretch();
        this.flipbook.add(UI.label(UIKeys.SNOWSTORM_APPEARANCE_ANIMATED, 20).labelAnchor(0, 1F));
        this.flipbook.add(UI.row(5, 0, 20, this.stepX, this.stepY));
        this.flipbook.add(this.fps);
        this.flipbook.add(this.max);
        this.flipbook.add(UI.row(5, 0, 20, this.stretch, this.loop));

        this.fields.add(UI.row(5, 0, 20, this.modeLabel, this.mode));
        this.fields.add(UI.label(UIKeys.SNOWSTORM_APPEARANCE_SIZE, 20).labelAnchor(0, 1F));
        this.fields.add(this.sizeW, this.sizeH);
        this.fields.add(UI.label(UIKeys.SNOWSTORM_APPEARANCE_MAPPING, 20).labelAnchor(0, 1F));
        this.fields.add(this.uvX, this.uvY, this.uvW, this.uvH);
    }

    private void updateElements()
    {
        this.flipbook.removeFromParent();

        if (this.component.flipbook)
        {
            this.fields.add(this.flipbook);
        }

        this.resizeParent();
    }

    @Override
    public IKey getTitle()
    {
        return UIKeys.SNOWSTORM_APPEARANCE_TITLE;
    }

    @Override
    protected ParticleComponentAppearanceBillboard getComponent(ParticleScheme scheme)
    {
        return scheme.getOrCreate(ParticleComponentAppearanceBillboard.class);
    }

    @Override
    protected void fillData()
    {
        super.fillData();

        this.mode.setValue(this.component.flipbook ? 1 : 0);

        this.stepX.setValue(this.component.stepX);
        this.stepY.setValue(this.component.stepY);
        this.fps.setValue(this.component.fps);

        this.stretch.setValue(this.component.stretchFPS);
        this.loop.setValue(this.component.loop);

        this.updateElements();
    }
}