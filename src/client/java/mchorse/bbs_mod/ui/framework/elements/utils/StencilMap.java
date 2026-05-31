package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.utils.Pair;

import java.util.HashMap;
import java.util.Map;

public class StencilMap
{
    public int objectIndex;
    public Map<Integer, Pair<Form, String>> indexMap = new HashMap<>();
    public boolean increment = true;

    public void setIncrement(boolean increment)
    {
        this.increment = increment;
    }

    public void setup()
    {
        /* Pickable form parts start right after every gizmo stencil id so they
         * never share an index with a gizmo handle (the sphere and view ring
         * included), which would otherwise hijack the click. STENCIL_VIEW is the
         * highest handle id, so forms begin one past it. */
        this.objectIndex = Gizmo.STENCIL_VIEW + 1;

        /* Reset map and seed a pair per gizmo handle (move/scale/rotate axes,
         * planes, the trackball sphere and the shared view-plane ring). */
        this.indexMap.clear();

        for (Gizmo.Handle handle : Gizmo.Handle.values())
        {
            this.indexMap.put(handle.index, new Pair<>(null, handle.name().toLowerCase()));
        }
    }

    public void addPicking(Form form)
    {
        this.addPicking(form, "");
    }

    public void addPicking(Form form, String bone)
    {
        if (this.increment)
        {
            this.indexMap.put(this.objectIndex, new Pair<>(form, bone));

            this.objectIndex += 1;
        }
        else
        {
            this.indexMap.put(this.objectIndex, new Pair<>(form, ""));
        }
    }
}