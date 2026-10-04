package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.cubic.spline.SplineIK;
import mchorse.bbs_mod.settings.values.base.BaseValue;

import mchorse.bbs_mod.cubic.spline.SplineSource;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.ui.framework.UIContext;
import java.util.Set;

/** The viewport addresses points identically in both compound key editors. */
public interface SplineKeyframeEditor extends SplineEditorUtils.Selection
{
    UISplinePointsEditor pointEditor();
    SplineSource source();
    void selectPoint(String path);
    default String pointPath()
    {
        var point = this.pointEditor().point();
        return point == null ? null : FormUtils.getPropertyPath(point.position);
    }
    default boolean sameSource(SplineSource source)
    {
        SplineSource edited = this.source();
        if (source == null || edited == null || source.getClass() != edited.getClass()) return false;
        var a = (BaseValue) source;
        var b = (BaseValue) edited;
        return FormUtils.getPath(SplineEditorUtils.owner(source)).equals(FormUtils.getPath(SplineEditorUtils.owner(edited)))
            && (!(source instanceof SplineIK) || a.getId().equals(b.getId()));
    }
    default Set<String> selectedPoints(SplineSource source)
    { return this.sameSource(source) ? this.pointEditor().selected() : java.util.Collections.emptySet(); }
    default String hoveredPoint(UIContext context, SplineSource source)
    { return this.sameSource(source) ? this.pointEditor().points.hoveredPoint(context) : ""; }
    default void viewportHover(SplineSource source, String point)
    { this.pointEditor().points.viewportHover = this.sameSource(source) ? point : ""; }
}
