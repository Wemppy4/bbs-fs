package mchorse.bbs_mod.cubic.render.vao;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public class BOBJModelSimpleVAO extends BOBJModelVAO
{
    public Joint armLeft;
    public Joint armRight;
    public Joint legLeft;
    public Joint legRight;
    public Joint body;

    public BOBJModelSimpleVAO(BOBJLoader.CompiledData data)
    {
        super(data);

        this.armLeft = new Joint(this.armature.bones.get("left_arm"), this.armature.bones.get("low_left_arm"));
        this.armRight = new Joint(this.armature.bones.get("right_arm"), this.armature.bones.get("low_right_arm"));
        this.legLeft = new Joint(this.armature.bones.get("left_leg"), this.armature.bones.get("low_left_leg"));
        this.legRight = new Joint(this.armature.bones.get("right_leg"), this.armature.bones.get("low_leg_right"));
        this.body = new Joint(this.armature.bones.get("body"), this.armature.bones.get("low_body"));
    }

    @Override
    protected void processData(float[] newVertices, float[] newNormals)
    {
        if (!this.armLeft.isFilled())
        {
            float rmn1 = 22 / 64F;
            float rmx1 = 30 / 64F;
            float rmn2 = 54 / 64F;
            float rmx2 = 62 / 64F;
            float rmn3 = 38 / 64F;
            float rmx3 = 46 / 64F;

            for (int i = 0, c = this.data.posData.length / 3; i < c; i++)
            {
                double v = this.data.texData[i * 2 + 1];
                JointType type = JointType.NONE;

                for (int j = 0; j < 4; j++)
                {
                    int boneIndex = this.data.boneIndexData[i * 4 + j];

                    if (boneIndex == -1)
                    {
                        continue;
                    }

                    BOBJBone bone = this.armature.orderedBones.get(boneIndex);

                    if (bone.name.contains("leg"))
                    {
                        type = JointType.LEG;
                    }
                    else if (bone.name.contains("arm"))
                    {
                        type = JointType.ARM;
                    }
                    else if (bone.name.contains("body"))
                    {
                        type = JointType.BODY;
                    }

                    if (type != JointType.NONE)
                    {
                        break;
                    }
                }

                if (((v >= rmn1 && v <= rmx1) || (v >= rmn2 && v <= rmx2) || (v >= rmn3 && v <= rmx3)) && type != JointType.NONE)
                {
                    float z = this.data.posData[i * 3 + 2];
                    Joint joint;

                    if (type == JointType.BODY)
                    {
                        joint = this.body;
                    }
                    else if (v > 3 / 4F)
                    {
                        joint = type == JointType.LEG ? this.legLeft : this.armLeft;
                    }
                    else
                    {
                        joint = type == JointType.LEG ? this.legRight : this.armRight;
                    }

                    List<Integer> list = z < 0 ? joint.back : joint.front;

                    list.add(i);
                }
            }
        }

        this.armRight.process(this.data, this.armature, newVertices, newNormals);
        this.armLeft.process(this.data, this.armature, newVertices, newNormals);
        this.legRight.process(this.data, this.armature, newVertices, newNormals);
        this.legLeft.process(this.data, this.armature, newVertices, newNormals);
        this.body.process(this.data, this.armature, newVertices, newNormals);
    }

    public static class Joint
    {
        public static Vector4f temporary = new Vector4f();

        public List<Integer> front = new ArrayList<Integer>();
        public List<Integer> back = new ArrayList<Integer>();
        public BOBJBone top;
        public BOBJBone joint;

        public Joint(BOBJBone top, BOBJBone joint)
        {
            this.top = top;
            this.joint = joint;
        }

        public boolean isFilled()
        {
            return !this.front.isEmpty();
        }

        public void process(BOBJLoader.CompiledData data, BOBJArmature armature, float[] posData, float[] normalData)
        {
            final float pi = (float) Math.PI;

            float rotation = this.joint.transform.rotate.x;
            float frontFactor = MathUtils.clamp((rotation + pi / 2F) / pi, 0, 1);
            float backFactor = 1 - frontFactor;

            this.processSide(data, armature, this.front, posData, normalData, frontFactor);
            this.processSide(data, armature, this.back, posData, normalData, backFactor);
        }

        protected void processSide(BOBJLoader.CompiledData data, BOBJArmature armature, List<Integer> indices, float[] posData, float[] normalData, float factor)
        {
            int prevIndex = 0;

            for (int i : indices)
            {
                float x = data.posData[i * 3];
                float y = data.posData[i * 3 + 1] + factor * 4 / 16F - 2 / 16F;
                float z = data.posData[i * 3 + 2];

                temporary.set(x, y, z, 1F);
                armature.matrices[this.top.index].transform(temporary);

                posData[i * 3] = temporary.x;
                posData[i * 3 + 1] = temporary.y;
                posData[i * 3 + 2] = temporary.z;

                /* Copying the normal from the third/second side */
                int base = i - i % 3;
                int a = i - base;
                int b = prevIndex - base;
                int c = 0;

                if (b >= 0)
                {
                    /* If previous normal is from the same triangle, there is a need
                     * to figure out what is the third vertex is */
                    if ((a == 0 && b == 2) || (b == 0 && a == 2))
                    {
                        c = 1;
                    }
                    else if ((a == 0 && b == 1) || (b == 0 && a == 1))
                    {
                        c = 2;
                    }
                }
                else
                {
                    /* If there is only one transformed sharpened joint, then we
                     * can take just any as long as it's not the same as current index */
                    c = a == 1 ? 0 : 1;
                }

                c += base;

                normalData[i * 3] = normalData[c * 3];
                normalData[i * 3 + 1] = normalData[c * 3 + 1];
                normalData[i * 3 + 2] = normalData[c * 3 + 2];

                if (b >= 0)
                {
                    normalData[prevIndex * 3] = normalData[c * 3];
                    normalData[prevIndex * 3 + 1] = normalData[c * 3 + 1];
                    normalData[prevIndex * 3 + 2] = normalData[c * 3 + 2];
                }

                prevIndex = i;
            }
        }
    }

    public static enum JointType
    {
        LEG, ARM, BODY, NONE
    }
}