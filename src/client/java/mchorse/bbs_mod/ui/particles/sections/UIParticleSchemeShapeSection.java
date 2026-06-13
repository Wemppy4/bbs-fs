package mchorse.bbs_mod.ui.particles.sections;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.particles.components.shape.ParticleComponentShapeBase;
import mchorse.bbs_mod.particles.components.shape.ParticleComponentShapeBox;
import mchorse.bbs_mod.particles.components.shape.ParticleComponentShapeDisc;
import mchorse.bbs_mod.particles.components.shape.ParticleComponentShapeEntityAABB;
import mchorse.bbs_mod.particles.components.shape.ParticleComponentShapePoint;
import mchorse.bbs_mod.particles.components.shape.ParticleComponentShapeSphere;
import mchorse.bbs_mod.particles.components.shape.directions.ShapeDirectionInwards;
import mchorse.bbs_mod.particles.components.shape.directions.ShapeDirectionVector;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.particles.utils.UIMolangExpression;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIParticleSchemeShapeSection extends UIParticleSchemeModeSection<ParticleComponentShapeBase>
{
    public UIMolangExpression offsetX;
    public UIMolangExpression offsetY;
    public UIMolangExpression offsetZ;
    public UIDirectionSection direction;
    public UIToggle surface;

    public UILabel radiusLabel;
    public UIMolangExpression radius;

    public UILabel label;
    public UIElement xyz;
    public UIMolangExpression x;
    public UIMolangExpression y;
    public UIMolangExpression z;

    public UIParticleSchemeShapeSection(UIParticleSchemePanel parent)
    {
        super(parent);

        this.offsetX = new UIMolangExpression(() -> this.component.offset[0], (b) ->
        {
            this.editMoLang("shape.offset_x", (str) -> this.component.offset[0] = this.parse(str, this.component.offset[0]), this.component.offset[0]);
        });
        this.offsetX.icon(Icons.X).barColor(Colors.RED).tooltip(UIKeys.GENERAL_X);
        this.offsetY = new UIMolangExpression(() -> this.component.offset[1], (b) ->
        {
            this.editMoLang("shape.offset_y", (str) -> this.component.offset[1] = this.parse(str, this.component.offset[1]), this.component.offset[1]);
        });
        this.offsetY.icon(Icons.Y).barColor(Colors.GREEN).tooltip(UIKeys.GENERAL_Y);
        this.offsetZ = new UIMolangExpression(() -> this.component.offset[2], (b) ->
        {
            this.editMoLang("shape.offset_z", (str) -> this.component.offset[2] = this.parse(str, this.component.offset[2]), this.component.offset[2]);
        });
        this.offsetZ.icon(Icons.Z).barColor(Colors.BLUE).tooltip(UIKeys.GENERAL_Z);
        this.direction = new UIDirectionSection(this);
        this.surface = new UIToggle(UIKeys.SNOWSTORM_SHAPE_SURFACE, (b) ->
        {
            this.component.surface = b.getValue();
            this.editor.dirty();
        });
        this.surface.tooltip(UIKeys.SNOWSTORM_SHAPE_SURFACE_TOOLTIP);

        this.radiusLabel = UI.label(UIKeys.SNOWSTORM_SHAPE_RADIUS, 20).labelAnchor(0, 1F);
        this.radius = new UIMolangExpression(() -> this.component instanceof ParticleComponentShapeSphere ? ((ParticleComponentShapeSphere) this.component).radius : null, (b) ->
        {
            ParticleComponentShapeSphere sphere = (ParticleComponentShapeSphere) this.component;

            this.editMoLang("shape.radius", (str) -> sphere.radius = this.parse(str, sphere.radius), sphere.radius);
        });
        this.radius.icon(Icons.OUTLINE_SPHERE).tooltip(UIKeys.SNOWSTORM_SHAPE_RADIUS);

        this.label = UI.label(IKey.EMPTY, 20).labelAnchor(0, 1F);
        this.x = new UIMolangExpression(() -> this.normalDimension(0), (str) -> this.updateNormalDimension(0));
        this.x.icon(Icons.X).barColor(Colors.RED).tooltip(UIKeys.GENERAL_X);
        this.y = new UIMolangExpression(() -> this.normalDimension(1), (str) -> this.updateNormalDimension(1));
        this.y.icon(Icons.Y).barColor(Colors.GREEN).tooltip(UIKeys.GENERAL_Y);
        this.z = new UIMolangExpression(() -> this.normalDimension(2), (str) -> this.updateNormalDimension(2));
        this.z.icon(Icons.Z).barColor(Colors.BLUE).tooltip(UIKeys.GENERAL_Z);
        this.xyz = new UIElement();
        this.xyz.column(UIConstants.MARGIN).vertical().stretch();
        this.xyz.add(this.x, this.y, this.z);

        this.modeLabel.label = UIKeys.SNOWSTORM_SHAPE_SHAPE;

        this.fields.add(UI.label(UIKeys.SNOWSTORM_SHAPE_OFFSET, 20).labelAnchor(0, 1F));
        this.fields.add(this.offsetX, this.offsetY, this.offsetZ);
        this.fields.add(this.direction, this.surface);
    }

    private MolangExpression normalDimension(int index)
    {
        if (this.component instanceof ParticleComponentShapeBox)
        {
            return ((ParticleComponentShapeBox) this.component).halfDimensions[index];
        }
        else if (this.component instanceof ParticleComponentShapeDisc)
        {
            return ((ParticleComponentShapeDisc) this.component).normal[index];
        }

        return null;
    }

    private void updateNormalDimension(int index)
    {
        if (this.component instanceof ParticleComponentShapeBox)
        {
            ParticleComponentShapeBox box = (ParticleComponentShapeBox) this.component;

            this.editMoLang("shape.size_" + index, (str) -> box.halfDimensions[index] = this.parse(str, box.halfDimensions[index]), box.halfDimensions[index]);
        }
        else if (this.component instanceof ParticleComponentShapeDisc)
        {
            ParticleComponentShapeDisc disc = (ParticleComponentShapeDisc) this.component;

            this.editMoLang("shape.normal_" + index, (str) -> disc.normal[index] = this.parse(str, disc.normal[index]), disc.normal[index]);
        }
    }

    @Override
    public IKey getTitle()
    {
        return UIKeys.SNOWSTORM_SHAPE_TITLE;
    }

    @Override
    protected void fillModes(UICirculate button)
    {
        button.addLabel(UIKeys.SNOWSTORM_SHAPE_POINT);
        button.addLabel(UIKeys.SNOWSTORM_SHAPE_BOX);
        button.addLabel(UIKeys.SNOWSTORM_SHAPE_SPHERE);
        button.addLabel(UIKeys.SNOWSTORM_SHAPE_DISC);
        button.addLabel(UIKeys.SNOWSTORM_SHAPE_AABB);
    }

    @Override
    protected void restoreInfo(ParticleComponentShapeBase component, ParticleComponentShapeBase old)
    {
        component.offset = old.offset;
        component.direction = old.direction;
        component.surface = old.surface;

        if (component instanceof ParticleComponentShapeSphere && old instanceof ParticleComponentShapeSphere)
        {
            ((ParticleComponentShapeSphere) component).radius = ((ParticleComponentShapeSphere) old).radius;
        }
    }

    @Override
    protected Class<ParticleComponentShapeBase> getBaseClass()
    {
        return ParticleComponentShapeBase.class;
    }

    @Override
    protected Class getDefaultClass()
    {
        return ParticleComponentShapePoint.class;
    }

    @Override
    protected Class getModeClass(int value)
    {
        if (value == 1)
        {
            return ParticleComponentShapeBox.class;
        }
        else if (value == 2)
        {
            return ParticleComponentShapeSphere.class;
        }
        else if (value == 3)
        {
            return ParticleComponentShapeDisc.class;
        }
        else if (value == 4)
        {
            return ParticleComponentShapeEntityAABB.class;
        }

        return ParticleComponentShapePoint.class;
    }

    @Override
    protected void fillData()
    {
        super.fillData();

        this.direction.fillData();
        this.surface.setValue(this.component.surface);

        this.radiusLabel.removeFromParent();
        this.radius.removeFromParent();
        this.label.removeFromParent();
        this.xyz.removeFromParent();
        this.surface.removeFromParent();

        if (this.component instanceof ParticleComponentShapeSphere)
        {
            this.fields.add(this.radiusLabel, this.radius);
        }

        if (this.component instanceof ParticleComponentShapeBox || this.component instanceof ParticleComponentShapeDisc)
        {
            this.label.label = this.component instanceof ParticleComponentShapeBox ? UIKeys.SNOWSTORM_SHAPE_BOX_SIZE : UIKeys.SNOWSTORM_SHAPE_NORMAL;

            this.fields.add(this.label);
            this.fields.add(this.xyz);
        }

        this.fields.add(this.surface);

        this.resizeParent();
    }

    public static class UIDirectionSection extends UIElement
    {
        public UIParticleSchemeShapeSection parent;

        public UICirculate mode;
        public UIElement xyz;
        public UIMolangExpression x;
        public UIMolangExpression y;
        public UIMolangExpression z;

        public UIDirectionSection(UIParticleSchemeShapeSection parent)
        {
            super();

            this.parent = parent;
            this.mode = new UICirculate((b) ->
            {
                int value = this.mode.getValue();

                if (value == 0)
                {
                    this.parent.component.direction = ShapeDirectionInwards.OUTWARDS;
                }
                else if (value == 1)
                {
                    this.parent.component.direction = ShapeDirectionInwards.INWARDS;
                }
                else
                {
                    this.parent.component.direction = new ShapeDirectionVector(MolangParser.ZERO, MolangParser.ZERO, MolangParser.ZERO);
                }

                this.parent.editor.dirty();
                this.fillData();
            });
            this.mode.addLabel(UIKeys.SNOWSTORM_SHAPE_DIRECTION_OUTWARDS);
            this.mode.addLabel(UIKeys.SNOWSTORM_SHAPE_DIRECTION_INWARDS);
            this.mode.addLabel(UIKeys.SNOWSTORM_SHAPE_DIRECTION_VECTOR);

            this.x = new UIMolangExpression(() -> this.vectorComponent(0), (b) ->
            {
                ShapeDirectionVector vector = this.getVector();

                this.parent.editMoLang("shape.vector.z", (str) -> vector.x = this.parent.parse(str, vector.x), vector.x);
            });
            this.x.icon(Icons.X).barColor(Colors.RED).tooltip(UIKeys.GENERAL_X);
            this.y = new UIMolangExpression(() -> this.vectorComponent(1), (b) ->
            {
                ShapeDirectionVector vector = this.getVector();

                this.parent.editMoLang("shape.vector.y", (str) -> vector.y = this.parent.parse(str, vector.y), vector.y);
            });
            this.y.icon(Icons.Y).barColor(Colors.GREEN).tooltip(UIKeys.GENERAL_Y);
            this.z = new UIMolangExpression(() -> this.vectorComponent(2), (b) ->
            {
                ShapeDirectionVector vector = this.getVector();

                this.parent.editMoLang("shape.vector.x", (str) -> vector.z = this.parent.parse(str, vector.z), vector.z);
            });
            this.z.icon(Icons.Z).barColor(Colors.BLUE).tooltip(UIKeys.GENERAL_Z);
            this.xyz = new UIElement();
            this.xyz.column(UIConstants.MARGIN).vertical().stretch();
            this.xyz.add(this.x, this.y, this.z);

            this.column().vertical().stretch().height(20);
            this.add(UI.row(5, 0, 20, UI.label(UIKeys.SNOWSTORM_SHAPE_DIRECTION, 20).labelAnchor(0, 0.5F), this.mode));
        }

        private ShapeDirectionVector getVector()
        {
            return (ShapeDirectionVector) this.parent.component.direction;
        }

        private MolangExpression vectorComponent(int axis)
        {
            if (!(this.parent.component.direction instanceof ShapeDirectionVector))
            {
                return null;
            }

            ShapeDirectionVector vector = (ShapeDirectionVector) this.parent.component.direction;

            return axis == 0 ? vector.x : (axis == 1 ? vector.y : vector.z);
        }

        public void fillData()
        {
            boolean isVector = this.parent.component.direction instanceof ShapeDirectionVector;
            int value = 0;

            if (this.parent.component.direction == ShapeDirectionInwards.INWARDS)
            {
                value = 1;
            }
            else if (isVector)
            {
                value = 2;
            }

            this.mode.setValue(value);

            this.xyz.removeFromParent();

            if (isVector)
            {
                this.add(this.xyz);
            }

            this.parent.resizeParent();
        }
    }
}