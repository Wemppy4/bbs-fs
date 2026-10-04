package mchorse.bbs_mod.graphics.shader;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

/** A program-owned uniform. Values can be set before the program is bound. */
public final class GlUniform
{
    private final ShaderProgram program;
    private final int location;
    private final String type;
    private final float[] values;
    private final int[] integerValues;
    private final FloatBuffer floats;
    private final IntBuffer integers;
    private boolean dirty = true;

    GlUniform(ShaderProgram program, int location, String type, int count)
    {
        this.program = program;
        this.location = location;
        this.type = type;
        this.values = new float[count];
        this.integerValues = new int[count];
        this.floats = BufferUtils.createFloatBuffer(count);
        this.integers = BufferUtils.createIntBuffer(count);
    }

    public void set(int value) { this.set(new int[] {value}); }
    public void set(int[] values)
    {
        if (values.length != this.values.length && values.length != 1)
            throw new IllegalArgumentException("Uniform expects " + this.values.length + " values, got " + values.length);
        for (int i = 0; i < this.values.length; i++)
        {
            int value = values[values.length == 1 ? 0 : i];
            this.integerValues[i] = value;
            this.values[i] = value;
        }
        this.dirty = true;
        if (this.program.isBound()) this.upload();
    }
    public void set(float value) { this.set(new float[] {value}); }
    public void set(float a, float b) { this.set(new float[] {a, b}); }
    public void set(float a, float b, float c) { this.set(new float[] {a, b, c}); }
    public void set(float a, float b, float c, float d) { this.set(new float[] {a, b, c, d}); }
    public void set(Matrix4f matrix) { this.set(matrix.get(new float[16])); }
    public void set(Matrix3f matrix) { this.set(matrix.get(new float[9])); }
    public void set(float[] values)
    {
        if (values.length != this.values.length && values.length != 1)
            throw new IllegalArgumentException("Uniform expects " + this.values.length + " values, got " + values.length);
        if (values.length == 1) Arrays.fill(this.values, values[0]);
        else System.arraycopy(values, 0, this.values, 0, values.length);
        for (int i = 0; i < this.values.length; i++) this.integerValues[i] = (int) this.values[i];
        this.dirty = true;
        if (this.program.isBound()) this.upload();
    }

    void upload()
    {
        if (!this.dirty || this.location < 0) return;
        if (this.type.equals("int"))
        {
            this.integers.clear();
            this.integers.put(this.integerValues);
            this.integers.flip();
            switch (this.values.length)
            {
                case 1: GL20.glUniform1(this.location, this.integers); break;
                case 2: GL20.glUniform2(this.location, this.integers); break;
                case 3: GL20.glUniform3(this.location, this.integers); break;
                case 4: GL20.glUniform4(this.location, this.integers); break;
                default: throw new IllegalStateException("Unsupported integer uniform size " + this.values.length);
            }
        }
        else
        {
            this.floats.clear(); this.floats.put(this.values).flip();
            if (this.type.equals("matrix4x4")) GL20.glUniformMatrix4(this.location, false, this.floats);
            else if (this.type.equals("matrix3x3")) GL20.glUniformMatrix3(this.location, false, this.floats);
            else if (this.type.equals("matrix2x2")) GL20.glUniformMatrix2(this.location, false, this.floats);
            else switch (this.values.length)
            {
                case 1: GL20.glUniform1(this.location, this.floats); break;
                case 2: GL20.glUniform2(this.location, this.floats); break;
                case 3: GL20.glUniform3(this.location, this.floats); break;
                case 4: GL20.glUniform4(this.location, this.floats); break;
                default: throw new IllegalStateException("Unsupported float uniform size " + this.values.length);
            }
        }
        this.dirty = false;
    }
}
