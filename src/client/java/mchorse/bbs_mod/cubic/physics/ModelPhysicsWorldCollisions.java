package mchorse.bbs_mod.cubic.physics;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves bone-physics collisions against solid world blocks. Each chain particle is a sphere of the
 * chain radius and each bone segment a capsule sampled as spheres along its length. Contacts are solved
 * by closest-point depenetration with Coulomb friction applied directly in Verlet velocity space: the
 * inward normal velocity is removed (no bounce) and the tangential velocity is scaled by the friction
 * coefficient. No swept raycasts, no axis-aligned normals, no special-cased rest thresholds.
 */
public final class ModelPhysicsWorldCollisions
{
    private static final float EPS = 1.0e-5f;
    private static final int RELAXATIONS = 2;
    private static final int DEPENETRATION_STEPS = 4;
    private static final float SEGMENT_SAMPLE_STEP = 0.5F;

    private ModelPhysicsWorldCollisions()
    {
    }

    public static void resolve(World world, Vector3f[] pos, Vector3f[] prev, int from, int to, float radius, float friction)
    {
        if (world == null || pos == null || prev == null || from < 0 || to > pos.length || to > prev.length || from >= to || radius <= 0F)
        {
            return;
        }

        List<float[]> boxes = gatherSolidBoxes(world, pos, prev, from, to, radius);

        if (boxes.isEmpty())
        {
            return;
        }

        float f = MathHelper.clamp(friction, 0F, 1F);
        Vector3f normal = new Vector3f();
        Vector3f sample = new Vector3f();

        for (int relax = 0; relax < RELAXATIONS; relax++)
        {
            /* Friction is a velocity change, so it must land once per solve, not once per relaxation —
             * applying it every pass would compound (1-friction) and stick contacts far harder than the
             * coefficient asks. Depenetration runs every pass; friction only on the last. */
            boolean applyFriction = relax == RELAXATIONS - 1;

            for (int i = from; i < to; i++)
            {
                collideSphere(pos[i], prev[i], radius, f, boxes, normal, applyFriction);
            }

            for (int i = from; i < to - 1; i++)
            {
                collideSegment(pos[i], pos[i + 1], radius, boxes, normal, sample);
            }
        }
    }

    private static void collideSphere(Vector3f p, Vector3f prev, float radius, float friction, List<float[]> boxes, Vector3f normal, boolean applyFriction)
    {
        float pushX = 0F;
        float pushY = 0F;
        float pushZ = 0F;

        for (int step = 0; step < DEPENETRATION_STEPS; step++)
        {
            float pen = deepestPenetration(p.x, p.y, p.z, radius, boxes, normal);

            if (pen <= EPS)
            {
                break;
            }

            p.x += normal.x * pen;
            p.y += normal.y * pen;
            p.z += normal.z * pen;

            pushX += normal.x * pen;
            pushY += normal.y * pen;
            pushZ += normal.z * pen;
        }

        if (!applyFriction)
        {
            return;
        }

        float pushLenSq = pushX * pushX + pushY * pushY + pushZ * pushZ;

        if (pushLenSq <= EPS * EPS)
        {
            return;
        }

        float inv = 1F / (float) Math.sqrt(pushLenSq);
        applyFriction(p, prev, pushX * inv, pushY * inv, pushZ * inv, friction);
    }

    private static void collideSegment(Vector3f a, Vector3f b, float radius, List<float[]> boxes, Vector3f normal, Vector3f sample)
    {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float dz = b.z - a.z;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        int samples = (int) Math.ceil(len / (radius * SEGMENT_SAMPLE_STEP));

        if (samples <= 1)
        {
            return;
        }

        for (int s = 1; s < samples; s++)
        {
            float t = s / (float) samples;

            sample.set(a.x + dx * t, a.y + dy * t, a.z + dz * t);

            float pen = deepestPenetration(sample.x, sample.y, sample.z, radius, boxes, normal);

            if (pen <= EPS)
            {
                continue;
            }

            float wa = 1F - t;
            float wb = t;
            float distribute = pen / (wa * wa + wb * wb);

            a.x += normal.x * wa * distribute;
            a.y += normal.y * wa * distribute;
            a.z += normal.z * wa * distribute;

            b.x += normal.x * wb * distribute;
            b.y += normal.y * wb * distribute;
            b.z += normal.z * wb * distribute;
        }
    }

    private static void applyFriction(Vector3f p, Vector3f prev, float nx, float ny, float nz, float friction)
    {
        float vx = p.x - prev.x;
        float vy = p.y - prev.y;
        float vz = p.z - prev.z;

        float vn = vx * nx + vy * ny + vz * nz;

        float tangentX = vx - nx * vn;
        float tangentY = vy - ny * vn;
        float tangentZ = vz - nz * vn;

        /* Keep outward separation but kill motion driving into the surface, so contacts don't bounce. */
        float normalScale = vn > 0F ? vn : 0F;
        float tangentScale = 1F - friction;

        prev.x = p.x - (nx * normalScale + tangentX * tangentScale);
        prev.y = p.y - (ny * normalScale + tangentY * tangentScale);
        prev.z = p.z - (nz * normalScale + tangentZ * tangentScale);
    }

    private static float deepestPenetration(float px, float py, float pz, float radius, List<float[]> boxes, Vector3f outNormal)
    {
        float best = 0F;

        for (int i = 0, n = boxes.size(); i < n; i++)
        {
            float[] box = boxes.get(i);

            float cx = clamp(px, box[0], box[3]);
            float cy = clamp(py, box[1], box[4]);
            float cz = clamp(pz, box[2], box[5]);

            float dx = px - cx;
            float dy = py - cy;
            float dz = pz - cz;
            float d2 = dx * dx + dy * dy + dz * dz;

            if (d2 > radius * radius)
            {
                continue;
            }

            float pen;
            float nx;
            float ny;
            float nz;

            if (d2 > EPS * EPS)
            {
                float d = (float) Math.sqrt(d2);
                float inv = 1F / d;
                nx = dx * inv;
                ny = dy * inv;
                nz = dz * inv;
                pen = radius - d;
            }
            else
            {
                /* Centre is inside the box: push out through the nearest face. */
                float dMinX = px - box[0];
                float dMaxX = box[3] - px;
                float dMinY = py - box[1];
                float dMaxY = box[4] - py;
                float dMinZ = pz - box[2];
                float dMaxZ = box[5] - pz;

                float face = dMinX;
                nx = -1F;
                ny = 0F;
                nz = 0F;

                if (dMaxX < face) { face = dMaxX; nx = 1F; ny = 0F; nz = 0F; }
                if (dMinY < face) { face = dMinY; nx = 0F; ny = -1F; nz = 0F; }
                if (dMaxY < face) { face = dMaxY; nx = 0F; ny = 1F; nz = 0F; }
                if (dMinZ < face) { face = dMinZ; nx = 0F; ny = 0F; nz = -1F; }
                if (dMaxZ < face) { face = dMaxZ; nx = 0F; ny = 0F; nz = 1F; }

                pen = radius + face;
            }

            if (pen > best)
            {
                best = pen;
                outNormal.set(nx, ny, nz);
            }
        }

        return best;
    }

    private static List<float[]> gatherSolidBoxes(World world, Vector3f[] pos, Vector3f[] prev, int from, int to, float radius)
    {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (int i = from; i < to; i++)
        {
            minX = Math.min(minX, Math.min(pos[i].x, prev[i].x));
            minY = Math.min(minY, Math.min(pos[i].y, prev[i].y));
            minZ = Math.min(minZ, Math.min(pos[i].z, prev[i].z));
            maxX = Math.max(maxX, Math.max(pos[i].x, prev[i].x));
            maxY = Math.max(maxY, Math.max(pos[i].y, prev[i].y));
            maxZ = Math.max(maxZ, Math.max(pos[i].z, prev[i].z));
        }

        int bx1 = MathHelper.floor(minX - radius);
        int by1 = MathHelper.floor(minY - radius);
        int bz1 = MathHelper.floor(minZ - radius);
        int bx2 = MathHelper.floor(maxX + radius);
        int by2 = MathHelper.floor(maxY + radius);
        int bz2 = MathHelper.floor(maxZ + radius);

        List<float[]> boxes = new ArrayList<>();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = bx1; x <= bx2; x++)
        {
            for (int y = by1; y <= by2; y++)
            {
                for (int z = bz1; z <= bz2; z++)
                {
                    mutable.set(x, y, z);

                    if (!world.isChunkLoaded(mutable))
                    {
                        continue;
                    }

                    BlockState state = world.getBlockState(mutable);

                    if (state == null)
                    {
                        continue;
                    }

                    if (state.isFullCube(world, mutable))
                    {
                        boxes.add(new float[] {x, y, z, x + 1F, y + 1F, z + 1F});
                        continue;
                    }

                    VoxelShape shape = state.getCollisionShape(world, mutable, ShapeContext.absent());

                    if (shape.isEmpty())
                    {
                        continue;
                    }

                    for (Box box : shape.getBoundingBoxes())
                    {
                        boxes.add(new float[] {
                            (float) (x + box.minX), (float) (y + box.minY), (float) (z + box.minZ),
                            (float) (x + box.maxX), (float) (y + box.maxY), (float) (z + box.maxZ)
                        });
                    }
                }
            }
        }

        return boxes;
    }

    public static boolean hasFullCubeInAabb(World world, BlockPos.Mutable mutable, int minBX, int minBY, int minBZ, int maxBX, int maxBY, int maxBZ)
    {
        if (world == null)
        {
            return false;
        }

        for (int x = minBX; x <= maxBX; x++)
        {
            for (int y = minBY; y <= maxBY; y++)
            {
                for (int z = minBZ; z <= maxBZ; z++)
                {
                    mutable.set(x, y, z);

                    if (!world.isChunkLoaded(mutable))
                    {
                        continue;
                    }

                    BlockState block = world.getBlockState(mutable);

                    if (block == null)
                    {
                        continue;
                    }

                    if (block.isFullCube(world, mutable))
                    {
                        return true;
                    }

                    if (!block.getCollisionShape(world, mutable, ShapeContext.absent()).isEmpty())
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static float clamp(float value, float min, float max)
    {
        return value < min ? min : (value > max ? max : value);
    }
}
