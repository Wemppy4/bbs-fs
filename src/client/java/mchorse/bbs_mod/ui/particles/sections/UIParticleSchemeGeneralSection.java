package mchorse.bbs_mod.ui.particles.sections;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.particles.ParticleMaterial;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.particles.components.appearance.BillboardDirection;
import mchorse.bbs_mod.particles.components.appearance.CameraFacing;
import mchorse.bbs_mod.particles.components.appearance.ParticleComponentAppearanceBillboard;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcons;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.particles.utils.UIMolangExpression;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIParticleSchemeGeneralSection extends UIParticleSchemeSection
{
    public UITextbox identifier;
    public UIButton pick;
    public UIIcons material;
    public UIIcons facing;

    public UIElement directionFields;
    public UIElement directionLabel;
    public UIIcons directionSource;
    public UITrackpad threshold;
    public UIMolangExpression dirX;
    public UIMolangExpression dirY;
    public UIMolangExpression dirZ;

    public UIParticleSchemeGeneralSection(UIParticleSchemePanel parent)
    {
        super(parent);

        this.identifier = new UITextbox(100, (str) ->
        {
            this.scheme.identifier = str;
            this.editor.dirty();
        });
        this.identifier.tooltip(UIKeys.SNOWSTORM_GENERAL_IDENTIFIER);

        this.pick = new UIButton(UIKeys.SNOWSTORM_GENERAL_PICK, (b) ->
        {
            UITexturePicker.open(this.getContext(), this.scheme.texture, (link) ->
            {
                if (link == null)
                {
                    link = ParticleScheme.DEFAULT_TEXTURE;
                }

                this.setTextureSize(link);
                this.scheme.texture = link;
                this.editor.dirty();
            });
        });

        this.material = new UIIcons((b) ->
        {
            this.scheme.material = ParticleMaterial.values()[this.material.getValue()];
            this.editor.dirty();
        });
        this.material.add(Icons.SQUARE, UIKeys.SNOWSTORM_GENERAL_PARTICLES_OPAQUE);
        this.material.add(Icons.DROP, UIKeys.SNOWSTORM_GENERAL_PARTICLES_ALPHA);
        this.material.add(Icons.FADING, UIKeys.SNOWSTORM_GENERAL_PARTICLES_BLEND);

        this.facing = new UIIcons((b) ->
        {
            this.getBillboard().facing = CameraFacing.values()[this.facing.getValue()];
            this.editor.dirty();
            this.updateDirectionFields();
        });
        this.facing.add(Icons.ALL_DIRECTIONS, UIKeys.C_CAMERA_FACING.get(CameraFacing.ROTATE_XYZ.id));
        this.facing.add(Icons.ORBIT, UIKeys.C_CAMERA_FACING.get(CameraFacing.ROTATE_Y.id));
        this.facing.add(Icons.LOOKING, UIKeys.C_CAMERA_FACING.get(CameraFacing.LOOKAT_XYZ.id));
        this.facing.add(Icons.CAMERA, UIKeys.C_CAMERA_FACING.get(CameraFacing.LOOKAT_Y.id));
        this.facing.add(Icons.X, UIKeys.C_CAMERA_FACING.get(CameraFacing.DIRECTION_X.id));
        this.facing.add(Icons.Y, UIKeys.C_CAMERA_FACING.get(CameraFacing.DIRECTION_Y.id));
        this.facing.add(Icons.Z, UIKeys.C_CAMERA_FACING.get(CameraFacing.DIRECTION_Z.id));

        this.buildDirectionFields();

        this.facing.w(this.facing.getPreferredWidth());

        this.fields.add(this.identifier, UI.row(5, 0, 20, this.pick, this.material));
        /* The strip is as wide as its icons and pins itself to the right, so the label rides
         * the same line instead of costing a row of its own */
        this.fields.add(UI.row(5, 0, 20,
            UI.label(UIKeys.SNOWSTORM_GENERAL_FACING, 0).labelAnchor(0, 0.5F),
            this.facing
        ).marginTop(UIConstants.MARGIN));
    }

    /* Direction source for the direction_x/y/z facing modes (shown only for those modes). */
    private void buildDirectionFields()
    {
        this.directionLabel = UI.label(UIKeys.SNOWSTORM_GENERAL_DIRECTION, 20).labelAnchor(0, 1F).marginTop(UIConstants.MARGIN);

        this.directionSource = new UIIcons((b) ->
        {
            this.getBillboard().directionMode = BillboardDirection.values()[this.directionSource.getValue()];
            this.editor.dirty();
            this.updateDirectionFields();
        });
        this.directionSource.add(Icons.ALL_DIRECTIONS, UIKeys.C_BILLBOARD_DIRECTION.get(BillboardDirection.DERIVE_FROM_VELOCITY.id));
        this.directionSource.add(Icons.CODE, UIKeys.C_BILLBOARD_DIRECTION.get(BillboardDirection.CUSTOM_DIRECTION.id));

        this.threshold = new UITrackpad((value) ->
        {
            this.getBillboard().minSpeedThreshold = value.floatValue();
            this.editor.dirty();
        });
        this.threshold.limit(0).tooltip(UIKeys.SNOWSTORM_GENERAL_DIRECTION_THRESHOLD);

        this.dirX = new UIMolangExpression(() -> this.getBillboard().customDirection[0], (b) ->
        {
            ParticleComponentAppearanceBillboard billboard = this.getBillboard();

            this.editMoLang("appearance.direction_x", (str) -> billboard.customDirection[0] = this.parse(str, billboard.customDirection[0]), billboard.customDirection[0]);
        });
        this.dirX.icon(Icons.X).barColor(Colors.RED).tooltip(UIKeys.GENERAL_X);
        this.dirY = new UIMolangExpression(() -> this.getBillboard().customDirection[1], (b) ->
        {
            ParticleComponentAppearanceBillboard billboard = this.getBillboard();

            this.editMoLang("appearance.direction_y", (str) -> billboard.customDirection[1] = this.parse(str, billboard.customDirection[1]), billboard.customDirection[1]);
        });
        this.dirY.icon(Icons.Y).barColor(Colors.GREEN).tooltip(UIKeys.GENERAL_Y);
        this.dirZ = new UIMolangExpression(() -> this.getBillboard().customDirection[2], (b) ->
        {
            ParticleComponentAppearanceBillboard billboard = this.getBillboard();

            this.editMoLang("appearance.direction_z", (str) -> billboard.customDirection[2] = this.parse(str, billboard.customDirection[2]), billboard.customDirection[2]);
        });
        this.dirZ.icon(Icons.Z).barColor(Colors.BLUE).tooltip(UIKeys.GENERAL_Z);

        this.directionFields = new UIElement();
        this.directionFields.column(UIConstants.MARGIN).vertical().stretch();
    }

    private void updateDirectionFields()
    {
        ParticleComponentAppearanceBillboard billboard = this.getBillboard();
        CameraFacing facing = billboard.facing;
        boolean isDirection = facing == CameraFacing.DIRECTION_X || facing == CameraFacing.DIRECTION_Y || facing == CameraFacing.DIRECTION_Z;

        this.directionSource.setValue(billboard.directionMode.ordinal());
        this.threshold.setValue(billboard.minSpeedThreshold);

        this.directionFields.removeAll();
        this.directionFields.removeFromParent();

        if (isDirection)
        {
            this.directionFields.add(this.directionLabel, this.directionSource);

            if (billboard.directionMode == BillboardDirection.CUSTOM_DIRECTION)
            {
                this.directionFields.add(this.dirX, this.dirY, this.dirZ);
            }
            else
            {
                this.directionFields.add(this.threshold);
            }

            this.fields.add(this.directionFields);
        }

        this.resizeParent();
    }

    private ParticleComponentAppearanceBillboard getBillboard()
    {
        return this.scheme.getOrCreate(ParticleComponentAppearanceBillboard.class);
    }

    private void setTextureSize(Link link)
    {
        ParticleComponentAppearanceBillboard component = this.scheme.get(ParticleComponentAppearanceBillboard.class);

        if (component == null)
        {
            return;
        }

        Texture texture = BBSModClient.getTextures().getTexture(link);

        component.textureWidth = texture.width;
        component.textureHeight = texture.height;
    }

    @Override
    public IKey getTitle()
    {
        return UIKeys.SNOWSTORM_GENERAL_TITLE;
    }

    @Override
    public void setScheme(ParticleScheme scheme)
    {
        super.setScheme(scheme);

        this.identifier.setText(scheme.identifier);
        this.material.setValue(scheme.material.ordinal());
        this.facing.setValue(this.getBillboard().facing.ordinal());
        this.updateDirectionFields();
    }
}