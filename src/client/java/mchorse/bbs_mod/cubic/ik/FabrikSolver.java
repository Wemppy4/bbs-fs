package mchorse.bbs_mod.cubic.ik;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

final class FabrikSolver
{
    private FabrikSolver()
    {
    }

    public static List<Vector3f> solve(List<Vector3f> startPositions, Vector3f target, int maxIterations, float tolerance)
    {
        int n = startPositions.size();

        if (n < 2)
        {
            return startPositions;
        }

        List<Vector3f> p = new ArrayList<>(n);

        for (int i = 0; i < n; i++)
        {
            p.add(new Vector3f(startPositions.get(i)));
        }

        float[] d = new float[n - 1];
        float total = 0F;

        for (int i = 0; i < n - 1; i++)
        {
            float len = p.get(i).distance(p.get(i + 1));
            d[i] = len;
            total += len;
        }

        Vector3f root = new Vector3f(p.get(0));
        float rootToTarget = root.distance(target);

        if (rootToTarget > total)
        {
            Vector3f dir = new Vector3f(target).sub(root);

            if (dir.lengthSquared() < 1.0e-10f)
            {
                return p;
            }

            dir.normalize();

            p.set(0, root);

            for (int i = 0; i < n - 1; i++)
            {
                Vector3f next = new Vector3f(p.get(i)).fma(d[i], dir);
                p.set(i + 1, next);
            }

            return p;
        }

        if (p.get(n - 1).distanceSquared(target) <= tolerance * tolerance)
        {
            return p;
        }

        for (int iter = 0; iter < maxIterations; iter++)
        {
            p.set(n - 1, new Vector3f(target));

            for (int i = n - 2; i >= 0; i--)
            {
                Vector3f pi = p.get(i);
                Vector3f pj = p.get(i + 1);

                Vector3f dir = new Vector3f(pi).sub(pj);
                float lenSq = dir.lengthSquared();

                if (lenSq < 1.0e-10f)
                {
                    continue;
                }

                dir.mul((float) (d[i] / Math.sqrt(lenSq)));
                p.set(i, new Vector3f(pj).add(dir));
            }

            p.set(0, new Vector3f(root));

            for (int i = 0; i < n - 1; i++)
            {
                Vector3f pi = p.get(i);
                Vector3f pj = p.get(i + 1);

                Vector3f dir = new Vector3f(pj).sub(pi);
                float lenSq = dir.lengthSquared();

                if (lenSq < 1.0e-10f)
                {
                    continue;
                }

                dir.mul((float) (d[i] / Math.sqrt(lenSq)));
                p.set(i + 1, new Vector3f(pi).add(dir));
            }

            if (p.get(n - 1).distanceSquared(target) <= tolerance * tolerance)
            {
                break;
            }
        }

        return p;
    }
}

