package mchorse.bbs_mod.graphics;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import java.util.ArrayDeque;
import java.util.Deque;

/** BBS transforms, independent of the Minecraft rendering API. */
public class MatrixStack
{
    public static class Entry
    {
        private final Matrix4f position;
        private final Matrix3f normal;
        private Entry() { position = new Matrix4f(); normal = new Matrix3f(); }
        private Entry(Entry other) { position = new Matrix4f(other.position); normal = new Matrix3f(other.normal); }
        public Matrix4f getPositionMatrix() { return position; }
        public Matrix3f getNormalMatrix() { return normal; }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    public MatrixStack() { entries.push(new Entry()); }
    public Entry peek() { return entries.peek(); }
    public void push() { entries.push(new Entry(peek())); }
    public void pop()
    {
        if (entries.size() == 1) throw new IllegalStateException("Cannot pop the base BBS matrix");
        entries.pop();
    }
    public void loadIdentity() { peek().position.identity(); peek().normal.identity(); }
    public void translate(double x, double y, double z) { peek().position.translate((float) x, (float) y, (float) z); }
    public void scale(float x, float y, float z)
    {
        peek().position.scale(x, y, z);
        peek().normal.scale(1F / x, 1F / y, 1F / z);
    }
    public void multiply(Quaternionf rotation) { peek().position.rotate(rotation); peek().normal.rotate(rotation); }
    public void multiply(Quaternionf rotation, float x, float y, float z)
    {
        translate(x, y, z); multiply(rotation); translate(-x, -y, -z);
    }
    public void multiplyPositionMatrix(Matrix4f matrix) { peek().position.mul(matrix); }
    public boolean isEmpty() { return entries.size() == 1; }
}
