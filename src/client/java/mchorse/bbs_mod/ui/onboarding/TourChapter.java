package mchorse.bbs_mod.ui.onboarding;

import mchorse.bbs_mod.l10n.keys.IKey;

import java.util.List;

/**
 * One guided walk through one place: a handful of steps, each pointing at an anchor with a
 * line or two about it. Chapters are data — a new one is a list and its strings, not code.
 *
 * <p>Each chapter is ticked off by its own id once it has been walked, so a chapter added in
 * a later version is shown to everyone exactly once, including people who walked the others
 * long ago.</p>
 *
 * @param priority which chapter wins when two want the screen at once — the dashboard's
 *                 outranks the film's, because the film opens under it on the way in
 */
public record TourChapter(String id, int priority, List<Step> steps)
{
    /** What to point at and what to say. The text says what to do there, not what it is called. */
    public record Step(String anchor, IKey title, IKey text)
    {}
}
