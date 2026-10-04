package mchorse.bbs_mod.bobj;

import mchorse.bbs_mod.utils.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.joml.Matrix4f;
import org.joml.Vector2d;
import org.joml.Vector3f;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OBJ file parser and loader
 *
 * Turns given
 */
public class BOBJLoader
{
    public static void merge(BOBJData to, BOBJData from)
    {
        int vertSize = to.vertices.size();
        int normSize = to.normals.size();
        int textSize = to.textures.size();

        to.vertices.addAll(from.vertices);
        to.normals.addAll(from.normals);
        to.textures.addAll(from.textures);
        to.armatures.putAll(from.armatures);

        for (BOBJMesh mesh : from.meshes)
        {
            BOBJMesh newMesh = mesh.add(vertSize, normSize, textSize);

            newMesh.armature = to.armatures.get(newMesh.armatureName);
            to.meshes.add(newMesh);
        }
    }

    /**
     * Read the data from OBJ file input stream
     */
    public static BOBJLoader.BOBJData readData(InputStream stream) throws Exception
    {
        List<String> lines = IOUtils.readLines(stream);

        List<Vertex> vertices = new ArrayList<Vertex>();
        List<Vector2d> textures = new ArrayList<Vector2d>();
        List<Vector3f> normals = new ArrayList<Vector3f>();
        List<BOBJMesh> objects = new ArrayList<BOBJMesh>();

        Map<String, BOBJAction> actions = new HashMap<String, BOBJAction>();
        Map<String, BOBJArmature> armatures = new HashMap<String, BOBJArmature>();

        BOBJMesh mesh = null;
        BOBJAction action = null;
        BOBJGroup group = null;
        BOBJChannel channel = null;
        BOBJArmature armature = null;
        BOBJBone bone = null;
        Vertex vertex = null;

        int i = 0;

        for (String line : lines)
        {
            String[] tokens = line.split("\\s");
            String first = tokens[0];

            if (first.equals("o"))
            {
                /* Object */
                objects.add(mesh = new BOBJMesh(tokens[1]));
                armature = null;
                vertex = null;
            }
            else if (first.equals("o_arm"))
            {
                mesh.armatureName = tokens[1];
            }
            else if (first.equals("v"))
            {
                /* Tiny weights are completely useless */
                if (vertex != null)
                {
                    vertex.eliminateTinyWeights();
                }

                /* Vertices */
                vertices.add(vertex = new Vertex(Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]), Float.parseFloat(tokens[3])));
            }
            else if (first.equals("vw"))
            {
                /* Vertex weight */
                float weight = Float.parseFloat(tokens[2]);

                if (weight != 0)
                {
                    vertex.weights.add(new Weight(tokens[1], weight));
                }
            }
            else if (first.equals("vt"))
            {
                /* Texture coordinates (UV) */
                textures.add(new Vector2d(Double.parseDouble(tokens[1]), Double.parseDouble(tokens[2])));
            }
            else if (first.equals("vn"))
            {
                /* Normals */
                normals.add(new Vector3f(Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]), Float.parseFloat(tokens[3])));
            }
            else if (first.equals("f"))
            {
                /* Collect faces */
                mesh.faces.add(new Face(tokens[1], tokens[2], tokens[3]));
            }
            else if (first.equals("arm_name"))
            {
                /* Armature stuff */
                i = 0;
                bone = null;
                armature = new BOBJArmature(tokens[1]);
                armatures.put(armature.name, armature);
            }
            else if (first.equals("arm_bone"))
            {
                Vector3f tail = new Vector3f(Float.parseFloat(tokens[3]), Float.parseFloat(tokens[4]), Float.parseFloat(tokens[5]));
                Matrix4f boneMat = new Matrix4f();
                float[] mat = new float[16];

                for (int j = 6; j < 6 + 16; j++)
                {
                    mat[j - 6] = Float.parseFloat(tokens[j]);
                }

                boneMat.set(mat);
                boneMat.transpose();
                bone = new BOBJBone(i++, tokens[1], tokens[2], boneMat);
                armature.addBone(bone);
            }
            else if (first.equals("an"))
            {
                /* Animation stuff */
                actions.put(tokens[1], action = new BOBJAction(tokens[1]));
            }
            else if (first.equals("ao"))
            {
                action.groups.put(tokens[1], group = new BOBJGroup(tokens[1]));
            }
            else if (first.equals("ag"))
            {
                group.channels.add(channel = new BOBJChannel(tokens[1], Integer.parseInt(tokens[2])));
            }
            else if (first.equals("kf"))
            {
                channel.keyframes.add(BOBJKeyframe.parse(tokens));
            }
        }

        /* Last ones need this too */
        if (vertex != null)
        {
            vertex.eliminateTinyWeights();
        }

        return new BOBJData(vertices, textures, normals, objects, actions, armatures);
    }

    /**
     * Load separate meshes from OBJ file
     */
    public static Map<String, CompiledData> loadMeshes(BOBJData data)
    {
        Map<String, CompiledData> meshes = new HashMap<String, CompiledData>();

        for (BOBJMesh mesh : data.meshes)
        {
            meshes.put(mesh.name, compileMesh(data, mesh));
        }

        return meshes;
    }

    public static CompiledData compileMesh(BOBJData data, BOBJMesh mesh)
    {
        List<Integer> indices = new ArrayList<Integer>();
        List<Face> facesList = mesh.faces;

        /* Initiate arrays for mesh data */
        int[] boneIndicesArr = new int[facesList.size() * 3 * 4];
        float[] weightsArr = new float[facesList.size() * 3 * 4];
        float[] posArr = new float[facesList.size() * 3 * 3];
        float[] textCoordArr = new float[facesList.size() * 3 * 2];
        float[] normArr = new float[facesList.size() * 3 * 3];

        Arrays.fill(boneIndicesArr, -1);
        Arrays.fill(weightsArr, -1);

        int i = 0;

        for (Face face : facesList)
        {
            for (IndexGroup indValue : face.idxGroups)
            {
                processFaceVertex(i, indValue, mesh, data, indices, posArr, textCoordArr, normArr, weightsArr, boneIndicesArr);

                i++;
            }
        }

        Integer[] integerArray = indices.toArray(new Integer[0]);
        int[] indicesArr = ArrayUtils.toPrimitive(integerArray);

        return new CompiledData(posArr, textCoordArr, normArr, weightsArr, boneIndicesArr, indicesArr, mesh);
    }

    /**
     * Load all meshes as one
     */
    public static CompiledData loadMesh(BOBJData data)
    {
        List<Integer> indices = new ArrayList<Integer>();
        List<Face> facesList = new ArrayList<Face>();

        for (BOBJMesh mesh : data.meshes)
        {
            facesList.addAll(mesh.faces);
        }

        /* Initiate arrays for mesh data */
        float[] posArr = new float[facesList.size() * 3 * 3];
        float[] textCoordArr = new float[facesList.size() * 3 * 2];
        float[] normArr = new float[facesList.size() * 3 * 3];

        int i = 0;

        for (Face face : facesList)
        {
            for (IndexGroup indValue : face.idxGroups)
            {
                processFaceVertex(i, indValue, null, data, indices, posArr, textCoordArr, normArr, null, null);

                i++;
            }
        }

        Integer[] integerArray = indices.toArray(new Integer[0]);
        int[] indicesArr = ArrayUtils.toPrimitive(integerArray);

        return new CompiledData(posArr, textCoordArr, normArr, null, null, indicesArr, null);
    }

    private static void processFaceVertex(int index, IndexGroup indices, BOBJMesh mesh, BOBJData data, List<Integer> indicesList, float[] posArr, float[] texCoordArr, float[] normArr, float[] weightsArr, int[] boneIndicesArr)
    {
        indicesList.add(index);

        if (indices.idxPos >= 0)
        {
            Vertex vec = data.vertices.get(indices.idxPos);

            posArr[index * 3] = vec.x;
            posArr[index * 3 + 1] = vec.y;
            posArr[index * 3 + 2] = vec.z;

            if (mesh != null)
            {
                for (int i = 0, c = Math.min(vec.weights.size(), 4); i < c; i++)
                {
                    Weight weight = vec.weights.get(i);
                    BOBJBone bone = mesh.armature.bones.get(weight.name);

                    weightsArr[index * 4 + i] = bone == null ? 0 : weight.factor;
                    boneIndicesArr[index * 4 + i] = bone == null ? -1 : bone.index;
                }
            }
        }

        if (indices.idxTextCoord >= 0)
        {
            Vector2d textCoord = data.textures.get(indices.idxTextCoord);

            texCoordArr[index * 2] = (float) textCoord.x;
            texCoordArr[index * 2 + 1] = (float) (1 - textCoord.y);
        }

        if (indices.idxVecNormal >= 0)
        {
            Vector3f vecNorm = data.normals.get(indices.idxVecNormal);

            normArr[index * 3] = vecNorm.x;
            normArr[index * 3 + 1] = vecNorm.y;
            normArr[index * 3 + 2] = vecNorm.z;
        }
    }

    public static class BOBJMesh
    {
        public String name;
        public List<Face> faces = new ArrayList<Face>();

        public String armatureName;
        public BOBJArmature armature;

        public BOBJMesh(String name)
        {
            this.name = name;
        }

        public BOBJMesh add(int vertices, int normals, int textures)
        {
            BOBJMesh mesh = new BOBJMesh(this.name);
            mesh.armatureName = this.armatureName;
            mesh.armature = this.armature;

            for (Face face : this.faces)
            {
                mesh.faces.add(face.add(vertices, normals, textures));
            }

            return mesh;
        }
    }

    public static class Face
    {
        /**
         * List of idxGroup groups for a face triangle (3 vertices per face).
         */
        public IndexGroup[] idxGroups = new IndexGroup[3];

        public Face(String v1, String v2, String v3)
        {
            idxGroups[0] = parseLine(v1);
            idxGroups[1] = parseLine(v2);
            idxGroups[2] = parseLine(v3);
        }

        public Face()
        {}

        /**
         * Parse index group from a string in format of "1/2/3". It can be also
         * "1//3" if the model doesn't provides texture coordinates.
         */
        private IndexGroup parseLine(String line)
        {
            IndexGroup idxGroup = new IndexGroup();
            String[] lineTokens = line.split("/");
            int length = lineTokens.length;

            idxGroup.idxPos = Integer.parseInt(lineTokens[0]) - 1;

            if (length > 1)
            {
                /* It can be empty if the obj does not define text coords */
                String textCoord = lineTokens[1];

                if (!textCoord.isEmpty())
                {
                    idxGroup.idxTextCoord = Integer.parseInt(textCoord) - 1;
                }

                if (length > 2)
                {
                    idxGroup.idxVecNormal = Integer.parseInt(lineTokens[2]) - 1;
                }
            }

            return idxGroup;
        }

        public Face add(int v, int n, int t)
        {
            Face face = new Face();

            for (int i = 0; i < face.idxGroups.length; i ++)
            {
                IndexGroup group = this.idxGroups[i];

                face.idxGroups[i] = new IndexGroup(group.idxPos + v, group.idxTextCoord + t, group.idxVecNormal + n);
            }

            return face;
        }
    }

    /**
     * Index group class
     *
     * This class represents an index group. Used in {@link Face} class to
     * represent an index group for looking up vertices from the collected
     * arrays.
     */
    public static class IndexGroup
    {
        public static final int NO_VALUE = -1;

        public int idxPos = NO_VALUE;
        public int idxTextCoord = NO_VALUE;
        public int idxVecNormal = NO_VALUE;

        public IndexGroup(int v, int t, int n)
        {
            this.idxPos = v;
            this.idxTextCoord = t;
            this.idxVecNormal = n;
        }

        public IndexGroup()
        {}
    }

    public static class Vertex
    {
        public float x;
        public float y;
        public float z;

        public List<Weight> weights = new ArrayList<Weight>();

        public Vertex(float x, float y, float z)
        {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public void eliminateTinyWeights()
        {
            this.weights.removeIf(w -> w.factor < 0.01);

            if (this.weights.isEmpty())
            {
                return;
            }

            float weight = 0;

            for (Weight w : this.weights)
            {
                weight += w.factor;
            }

            if (weight != 1)
            {
                for (Weight w : this.weights)
                {
                    w.factor /= weight;
                }
            }
        }
    }

    public static class Weight
    {
        public String name;
        public float factor;

        public Weight(String name, float factor)
        {
            this.name = name;
            this.factor = factor;
        }
    }

    public static class BOBJData
    {
        public List<Vertex> vertices;
        public List<Vector2d> textures;
        public List<Vector3f> normals;
        public List<BOBJMesh> meshes;
        public Map<String, BOBJAction> actions;
        public Map<String, BOBJArmature> armatures;

        public BOBJData(List<Vertex> vertices, List<Vector2d> textures, List<Vector3f> normals, List<BOBJMesh> meshes, Map<String, BOBJAction> actions, Map<String, BOBJArmature> armatures)
        {
            this.vertices = vertices;
            this.textures = textures;
            this.normals = normals;
            this.meshes = meshes;
            this.actions = actions;
            this.armatures = armatures;

            for (BOBJMesh mesh : meshes)
            {
                mesh.armature = armatures.get(mesh.armatureName);
            }
        }

        public boolean hasGeometry()
        {
            return !this.meshes.isEmpty();
        }

        /**
         * Should clean up all the geometry data used for constructing VBOs
         */
        public void dispose()
        {
            this.vertices.clear();
            this.textures.clear();
            this.normals.clear();
            this.meshes.clear();
        }

        public void initiateArmatures()
        {
            for (BOBJArmature armature : this.armatures.values())
            {
                armature.initArmature();
            }
        }
    }

    /**
     * Holds the mesh data 
     */
    public static class CompiledData
    {
        public float[] posData;
        public float[] texData;
        public float[] normData;
        public float[] weightData;
        public int[] boneIndexData;
        public int[] indexData;
        public BOBJMesh mesh;

        public CompiledData(float[] posData, float[] texData, float[] normData, float[] weightData, int[] boneIndexData, int[] indexData, BOBJMesh mesh)
        {
            this.posData = posData;
            this.texData = texData;
            this.normData = normData;
            this.weightData = weightData;
            this.boneIndexData = boneIndexData;
            this.indexData = indexData;
            this.mesh = mesh;
        }
    }
}