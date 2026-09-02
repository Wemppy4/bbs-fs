package mchorse.bbs_mod.blocks.entities;

import mchorse.bbs_mod.blocks.ModelBlockSound;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Vector3f;

/**
 * The physical side of a model block: its hitbox shape, whether entities and
 * the camera collide with it, its light level and sound material. Light and
 * sound are stored here as the source of truth (so they travel inside the
 * item's BlockEntityTag) and the server mirrors them into the block state,
 * where the engine actually reads them.
 */
public class ModelBody implements IMapSerializable
{
    /**
     * Vanilla's collision spliterator visits blocks overlapped by the entity's
     * box expanded by one block in every direction, so a shape reaching past
     * [-1..2] of its own cell simply stops colliding — clamp to that.
     */
    private static final float SHAPE_LIMIT = 1F;

    /** Below this size a box can be neither targeted nor collided with meaningfully. */
    private static final float MIN_SIZE = 0.01F;

    public enum HitboxMode
    {
        CUBE("cube"),
        FORM("form"),
        MANUAL("manual");

        public final String id;

        HitboxMode(String id)
        {
            this.id = id;
        }

        public static HitboxMode byId(String id)
        {
            for (HitboxMode mode : values())
            {
                if (mode.id.equals(id))
                {
                    return mode;
                }
            }

            return CUBE;
        }
    }

    private HitboxMode hitboxMode = HitboxMode.CUBE;
    private final Vector3f hitboxMin = new Vector3f(0F, 0F, 0F);
    private final Vector3f hitboxMax = new Vector3f(1F, 1F, 1F);
    private boolean solid;
    private boolean cameraCollision;
    private int lightLevel;
    private ModelBlockSound sound = ModelBlockSound.STONE;

    /** Vanilla-scale hardness (stone 1.5, obsidian 50); 0 keeps the instant break. */
    private float hardness;

    public HitboxMode getHitboxMode()
    {
        return this.hitboxMode;
    }

    public void setHitboxMode(HitboxMode mode)
    {
        this.hitboxMode = mode == null ? HitboxMode.CUBE : mode;
    }

    public Vector3f getHitboxMin()
    {
        return this.hitboxMin;
    }

    public Vector3f getHitboxMax()
    {
        return this.hitboxMax;
    }

    public boolean isSolid()
    {
        return this.solid;
    }

    public void setSolid(boolean solid)
    {
        this.solid = solid;
    }

    public boolean isCameraCollision()
    {
        return this.cameraCollision;
    }

    public void setCameraCollision(boolean cameraCollision)
    {
        this.cameraCollision = cameraCollision;
    }

    public int getLightLevel()
    {
        return this.lightLevel;
    }

    public void setLightLevel(int lightLevel)
    {
        this.lightLevel = MathHelper.clamp(lightLevel, 0, 15);
    }

    public ModelBlockSound getSound()
    {
        return this.sound;
    }

    public void setSound(ModelBlockSound sound)
    {
        this.sound = sound == null ? ModelBlockSound.STONE : sound;
    }

    public float getHardness()
    {
        return this.hardness;
    }

    public void setHardness(float hardness)
    {
        this.hardness = Math.max(hardness, 0F);
    }

    /**
     * The block's shape in cell coordinates (0..1 is the block's own cell).
     * Never empty; CUBE returns the {@link VoxelShapes#fullCube()} singleton
     * so callers can recognize it by reference and skip building unions.
     */
    public VoxelShape buildShape(Form form, Transform transform)
    {
        if (this.hitboxMode == HitboxMode.FORM && form != null)
        {
            float halfW = form.hitboxWidth.get() / 2F;
            float height = form.hitboxHeight.get();
            Vector3f t = transform.translate;
            Vector3f s = transform.scale;

            return buildBox(
                0.5F + t.x - halfW * Math.abs(s.x), t.y, 0.5F + t.z - halfW * Math.abs(s.z),
                0.5F + t.x + halfW * Math.abs(s.x), t.y + height * Math.abs(s.y), 0.5F + t.z + halfW * Math.abs(s.z)
            );
        }
        else if (this.hitboxMode == HitboxMode.MANUAL)
        {
            return buildBox(
                this.hitboxMin.x, this.hitboxMin.y, this.hitboxMin.z,
                this.hitboxMax.x, this.hitboxMax.y, this.hitboxMax.z
            );
        }

        return VoxelShapes.fullCube();
    }

    private static VoxelShape buildBox(float x1, float y1, float z1, float x2, float y2, float z2)
    {
        float[] x = orderAndClamp(x1, x2);
        float[] y = orderAndClamp(y1, y2);
        float[] z = orderAndClamp(z1, z2);

        return VoxelShapes.cuboidUnchecked(x[0], y[0], z[0], x[1], y[1], z[1]);
    }

    private static float[] orderAndClamp(float a, float b)
    {
        float min = MathHelper.clamp(Math.min(a, b), -SHAPE_LIMIT, 1F + SHAPE_LIMIT);
        float max = MathHelper.clamp(Math.max(a, b), -SHAPE_LIMIT, 1F + SHAPE_LIMIT);

        if (max - min < MIN_SIZE)
        {
            max = min + MIN_SIZE;
        }

        return new float[] {min, max};
    }

    @Override
    public void fromData(MapType data)
    {
        this.hitboxMode = HitboxMode.byId(data.getString("hitbox"));
        this.hitboxMin.set(DataStorageUtils.vector3fFromData(data.getList("min"), new Vector3f(0F, 0F, 0F)));
        this.hitboxMax.set(DataStorageUtils.vector3fFromData(data.getList("max"), new Vector3f(1F, 1F, 1F)));
        this.solid = data.getBool("solid");
        this.cameraCollision = data.getBool("camera");
        this.setLightLevel(data.getInt("light"));
        this.sound = ModelBlockSound.byId(data.getString("sound"));
        this.setHardness(data.getFloat("hardness"));
    }

    @Override
    public void toData(MapType data)
    {
        data.putString("hitbox", this.hitboxMode.id);
        data.put("min", DataStorageUtils.vector3fToData(this.hitboxMin));
        data.put("max", DataStorageUtils.vector3fToData(this.hitboxMax));
        data.putBool("solid", this.solid);
        data.putBool("camera", this.cameraCollision);
        data.putInt("light", this.lightLevel);
        data.putString("sound", this.sound.id);
        data.putFloat("hardness", this.hardness);
    }
}
