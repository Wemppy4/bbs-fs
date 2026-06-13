package mchorse.bbs_mod.ui.particles.sections;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.particles.components.motion.ParticleComponentInitialSpeed;
import mchorse.bbs_mod.particles.components.motion.ParticleComponentInitialSpin;
import mchorse.bbs_mod.particles.components.motion.ParticleComponentMotion;
import mchorse.bbs_mod.particles.components.motion.ParticleComponentMotionDynamic;
import mchorse.bbs_mod.particles.components.motion.ParticleComponentMotionParametric;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.particles.utils.UIMolangExpression;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIParticleSchemeMotionSection extends UIParticleSchemeModeSection<ParticleComponentMotion>
{
    public UIElement position;
    public UIMolangExpression positionSpeed;
    public UIMolangExpression positionX;
    public UIMolangExpression positionY;
    public UIMolangExpression positionZ;
    public UIMolangExpression positionDrag;

    public UIElement rotation;
    public UIMolangExpression rotationAngle;
    public UIMolangExpression rotationRate;
    public UIMolangExpression rotationAcceleration;
    public UIMolangExpression rotationDrag;

    private ParticleComponentInitialSpeed speed;
    private ParticleComponentInitialSpin spin;

    public UIParticleSchemeMotionSection(UIParticleSchemePanel parent)
    {
        super(parent);

        this.positionSpeed = new UIMolangExpression(() -> this.speed == null ? null : this.speed.speed, (b) ->
        {
            this.editMoLang("motion.speed", (str) -> this.speed.speed = this.parse(str, this.speed.speed), this.speed.speed);
        });
        this.positionSpeed.icon(Icons.ALL_DIRECTIONS).tooltip(UIKeys.SNOWSTORM_MOTION_POSITION_SPEED);
        this.positionX = new UIMolangExpression(() -> this.positionExpression(0), (str) -> this.updatePosition(0));
        this.positionX.icon(Icons.X).barColor(Colors.RED).tooltip(UIKeys.GENERAL_X);
        this.positionY = new UIMolangExpression(() -> this.positionExpression(1), (str) -> this.updatePosition(1));
        this.positionY.icon(Icons.Y).barColor(Colors.GREEN).tooltip(UIKeys.GENERAL_Y);
        this.positionZ = new UIMolangExpression(() -> this.positionExpression(2), (str) -> this.updatePosition(2));
        this.positionZ.icon(Icons.Z).barColor(Colors.BLUE).tooltip(UIKeys.GENERAL_Z);
        this.positionDrag = new UIMolangExpression(() -> this.component instanceof ParticleComponentMotionDynamic ? ((ParticleComponentMotionDynamic) this.component).motionDrag : null, (b) ->
        {
            ParticleComponentMotionDynamic component = (ParticleComponentMotionDynamic) this.component;

            this.editMoLang("motion.drag", (str) -> component.motionDrag = this.parse(str, component.motionDrag), component.motionDrag);
        });
        this.positionDrag.icon(Icons.REVERSE).tooltip(UIKeys.SNOWSTORM_MOTION_POSITION_DRAG);

        this.rotationAngle = new UIMolangExpression(() -> this.spin == null ? null : this.spin.rotation, (b) ->
        {
            this.editMoLang("motion.angle", (str) -> this.spin.rotation = this.parse(str, this.spin.rotation), this.spin.rotation);
        });
        this.rotationAngle.icon(Icons.ARC).tooltip(UIKeys.SNOWSTORM_MOTION_ROTATION_ANGLE);
        this.rotationRate = new UIMolangExpression(() -> this.spin == null ? null : this.spin.rate, (b) ->
        {
            this.editMoLang("motion.angle_speed", (str) -> this.spin.rate = this.parse(str, this.spin.rate), this.spin.rate);
        });
        this.rotationRate.icon(Icons.ORBIT).tooltip(UIKeys.SNOWSTORM_MOTION_ROTATION_SPEED);
        this.rotationAcceleration = new UIMolangExpression(() -> this.rotationAccelerationExpression(), (b) ->
        {
            if (this.component instanceof ParticleComponentMotionDynamic)
            {
                ParticleComponentMotionDynamic component = (ParticleComponentMotionDynamic) this.component;

                this.editMoLang("motion.angle_acceleration", (str) -> component.rotationAcceleration = this.parse(str, component.rotationAcceleration), component.rotationAcceleration);
            }
            else
            {
                ParticleComponentMotionParametric component = (ParticleComponentMotionParametric) this.component;

                this.editMoLang("motion.angle_expression", (str) -> component.rotation = this.parse(str, component.rotation), component.rotation);
            }
        });
        this.rotationAcceleration.icon(Icons.REFRESH).tooltip(UIKeys.SNOWSTORM_MOTION_ROTATION_ACCELERATION);
        this.rotationDrag = new UIMolangExpression(() -> this.component instanceof ParticleComponentMotionDynamic ? ((ParticleComponentMotionDynamic) this.component).rotationDrag : null, (b) ->
        {
            ParticleComponentMotionDynamic component = (ParticleComponentMotionDynamic) this.component;

            this.editMoLang("motion.angle_drag", (str) -> component.rotationDrag = this.parse(str, component.rotationDrag), component.rotationDrag);
        });
        this.rotationDrag.icon(Icons.REVERSE).tooltip(UIKeys.SNOWSTORM_MOTION_ROTATION_DRAG);

        this.position = new UIElement();
        this.position.column(UIConstants.MARGIN).vertical().stretch();
        this.position.add(UI.label(UIKeys.SNOWSTORM_MOTION_POSITION, 20).labelAnchor(0, 1F), this.positionSpeed);
        this.position.add(this.positionX, this.positionY, this.positionZ);

        this.rotation = new UIElement();
        this.rotation.column(UIConstants.MARGIN).vertical().stretch();
        this.rotation.add(UI.label(UIKeys.SNOWSTORM_MOTION_ROTATION, 20).labelAnchor(0, 1F), this.rotationAngle, this.rotationRate);
        this.rotation.add(this.rotationAcceleration);

        this.fields.add(this.position, this.rotation);
    }

    private MolangExpression positionExpression(int index)
    {
        if (this.component instanceof ParticleComponentMotionDynamic)
        {
            return ((ParticleComponentMotionDynamic) this.component).motionAcceleration[index];
        }

        return ((ParticleComponentMotionParametric) this.component).position[index];
    }

    private MolangExpression rotationAccelerationExpression()
    {
        if (this.component instanceof ParticleComponentMotionDynamic)
        {
            return ((ParticleComponentMotionDynamic) this.component).rotationAcceleration;
        }

        return ((ParticleComponentMotionParametric) this.component).rotation;
    }

    private void updatePosition(int index)
    {
        if (this.component instanceof ParticleComponentMotionDynamic)
        {
            ParticleComponentMotionDynamic component = (ParticleComponentMotionDynamic) this.component;

            this.editMoLang("motion.acceleration_" + index, (str) -> component.motionAcceleration[index] = this.parse(str, component.motionAcceleration[index]), component.motionAcceleration[index]);
        }
        else
        {
            ParticleComponentMotionParametric component = (ParticleComponentMotionParametric) this.component;

            this.editMoLang("motion.position_" + index, (str) -> component.position[index] = this.parse(str, component.position[index]), component.position[index]);
        }
    }

    @Override
    public IKey getTitle()
    {
        return UIKeys.SNOWSTORM_MOTION_TITLE;
    }

    @Override
    protected void fillModes(UICirculate button)
    {
        button.addLabel(UIKeys.SNOWSTORM_MOTION_DYNAMIC);
        button.addLabel(UIKeys.SNOWSTORM_MOTION_PARAMETRIC);
    }

    @Override
    protected Class<ParticleComponentMotion> getBaseClass()
    {
        return ParticleComponentMotion.class;
    }

    @Override
    protected Class getDefaultClass()
    {
        return ParticleComponentMotionDynamic.class;
    }

    @Override
    protected Class getModeClass(int value)
    {
        if (value == 1)
        {
            return ParticleComponentMotionParametric.class;
        }

        return ParticleComponentMotionDynamic.class;
    }

    @Override
    protected void fillData()
    {
        super.fillData();

        this.speed = this.scheme.getOrCreate(ParticleComponentInitialSpeed.class);
        this.spin = this.scheme.getOrCreate(ParticleComponentInitialSpin.class);

        this.positionDrag.removeFromParent();
        this.rotationDrag.removeFromParent();

        if (this.component instanceof ParticleComponentMotionDynamic)
        {
            this.position.add(this.positionDrag);
            this.rotation.add(this.rotationDrag);
        }

        this.resizeParent();
    }
}