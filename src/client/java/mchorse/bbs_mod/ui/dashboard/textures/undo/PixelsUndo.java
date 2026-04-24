package mchorse.bbs_mod.ui.dashboard.textures.undo;

import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.Pixels;
import mchorse.bbs_mod.utils.undo.IUndo;

import java.util.HashMap;
import java.util.Map;

public class PixelsUndo implements IUndo<Pixels>
{
    public Map<Integer, Pair<Color, Color>> pixels = new HashMap<>();

    public void setColor(Pixels pixels, int x, int y, Color color)
    {
        if (x < 0 || y < 0 || x >= pixels.width || y >= pixels.height)
        {
            return;
        }

        int index = pixels.toIndex(x, y);
        Pair<Color, Color> pair = this.pixels.computeIfAbsent(index, (k) -> new Pair<>(pixels.getColor(x, y).copy(), null));

        pair.b = color.copy();
        pixels.setColor(x, y, color);
    }

    public Color getOriginalColor(Pixels pixels, int x, int y)
    {
        Pair<Color, Color> pair = this.pixels.get(pixels.toIndex(x, y));

        return pair == null ? null : pair.a;
    }

    @Override
    public IUndo<Pixels> noMerging()
    {
        return this;
    }

    @Override
    public boolean isMergeable(IUndo<Pixels> undo)
    {
        return false;
    }

    @Override
    public void merge(IUndo<Pixels> undo)
    {}

    @Override
    public void undo(Pixels context)
    {
        for (Map.Entry<Integer, Pair<Color, Color>> entry : this.pixels.entrySet())
        {
            int index = entry.getKey();

            context.setColor(context.toX(index), context.toY(index), entry.getValue().a);
        }
    }

    @Override
    public void redo(Pixels context)
    {
        for (Map.Entry<Integer, Pair<Color, Color>> entry : this.pixels.entrySet())
        {
            int index = entry.getKey();

            context.setColor(context.toX(index), context.toY(index), entry.getValue().b);
        }
    }
}