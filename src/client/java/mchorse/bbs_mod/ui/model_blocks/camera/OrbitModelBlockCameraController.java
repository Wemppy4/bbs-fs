package mchorse.bbs_mod.ui.model_blocks.camera;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.camera.OrbitViewportController;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3f;

/**
 * The orbit of the model blocks panel: what it turns around is the selected block's model.
 * It is how that panel is flown at all — there is no flight beside it, so the wheel is always
 * the zoom and WASD always walks the pivot.
 *
 * <p>Nothing is followed: a block stands where it was put, and its transform is what the user
 * is dragging about, so hanging the camera off it would pull the view along with every drag.</p>
 */
public class OrbitModelBlockCameraController extends OrbitViewportController
{
    private final UIModelBlockPanel panel;

    public OrbitModelBlockCameraController(UIModelBlockPanel panel)
    {
        super();

        this.panel = panel;
    }

    @Override
    protected UIContext getContext()
    {
        return this.panel.getContext();
    }

    /** The world is drawn across the whole screen here, which is what the gizmo also projects into. */
    @Override
    protected Area getViewport()
    {
        return this.panel.getGizmoArea();
    }

    @Override
    protected Camera getViewportCamera()
    {
        return BBSModClient.getCameraController().camera;
    }

    @Override
    protected float getSpeed()
    {
        return this.panel.dashboard.orbit.getSpeed();
    }

    /**
     * The world camera of the dashboard is handed a position, a rotation and a field of view
     * and nothing else - whoever renders the world builds its own matrices. So the copy used
     * for the panning ray gets them built here, from that same viewport.
     */
    @Override
    protected void syncRayMatrices(Camera camera)
    {
        Area viewport = this.getViewport();

        camera.fov = BBSSettings.getFov();
        camera.updatePerspectiveProjection(viewport.w, viewport.h);
        camera.updateView();
    }

    /** Nobody else sets it while this orbit drives the camera, and the ray above reads it. */
    @Override
    public void setup(Camera camera, float transition)
    {
        super.setup(camera, transition);

        camera.fov = BBSSettings.getFov();
    }

    @Override
    protected boolean hasSubject()
    {
        return this.panel.getModelBlock() != null;
    }

    /**
     * The middle of the block's model: where its transform puts it, raised by half the form's
     * own height. The block's cell says nothing about how tall what stands in it is, and an
     * orbit around the floor of a two-block statue swings it through the frame.
     */
    @Override
    protected Vector3f getSubjectPivot(float transition)
    {
        ModelBlockEntity block = this.panel.getModelBlock();

        if (block == null)
        {
            return null;
        }

        BlockPos pos = block.getPos();
        ModelProperties properties = block.getProperties();
        Transform transform = properties.getTransform();
        Form form = properties.getForm();
        float height = form == null ? 1F : form.hitboxHeight.get() * Math.abs(transform.scale.y);

        return new Vector3f(
            pos.getX() + 0.5F + transform.translate.x,
            pos.getY() + transform.translate.y + height / 2F,
            pos.getZ() + 0.5F + transform.translate.z
        );
    }
}
