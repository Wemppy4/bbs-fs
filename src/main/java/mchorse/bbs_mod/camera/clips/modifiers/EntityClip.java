package mchorse.bbs_mod.camera.clips.modifiers;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.values.ValuePoint;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.utils.clips.ClipContext;

import java.util.Collections;
import java.util.List;

/**
 * Abstract entity modifier
 *
 * Abstract class for any new modifiers which are going to use entity
 * selector to fetch an entity and apply some modifications to the path
 * based on the entity.
 */
public abstract class EntityClip extends CameraClip
{
    /**
     * Position which may be used for calculation of relative
     * camera fixture animations
     */
    public Position position = new Position(0, 0, 0, 0, 0);

    /** Stable id of the tracked replay (empty = none) — not a list index, see {@code Anchor#replay}. */
    public final ValueString selector = new ValueString("selector", "");
    public final ValuePoint offset = new ValuePoint("offset", new Point(0, 0, 0));

    public EntityClip()
    {
        super();

        this.add(this.selector);
        this.add(this.offset);
    }

    public List<IEntity> getEntities(ClipContext context)
    {
        String id = this.selector.get();

        if (context instanceof CameraClipContext cameraClipContext && !id.isEmpty())
        {
            IEntity entity = cameraClipContext.entities.get(id);

            if (entity != null)
            {
                return Collections.singletonList(entity);
            }
        }

        return Collections.emptyList();
    }
}