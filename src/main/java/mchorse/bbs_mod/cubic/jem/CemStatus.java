package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.forms.forms.ModelForm;

/**
 * The entity states a CEM pack asks about that nobody can answer for a form: whether the creature sits,
 * is tamed, is angry. Not to be confused with {@link CemState}, which is the program's clock and its
 * variables — this is what the director sets by hand.
 *
 * <p>In the world a morph rides a real entity and {@link mchorse.bbs_mod.forms.entities.MCEntity} reads
 * these off it, but a film's actor is a stand-in: it has no owner to be tamed by and no target to be
 * angry at, so a cat could never sit and 35 of Fresh Animations' models could never bristle. They are
 * not facts to be simulated either — a sitting cat in a film is a decision, like its pose.</p>
 *
 * <p>Layered over what the entity says rather than replacing it: a flag turns the state on, and the
 * health factor scales the health the entity reports, so the whole of it at its defaults changes
 * nothing. Held by the animator and refilled from the form each frame, so nothing is allocated per
 * frame and a keyframed value takes effect the frame it changes.</p>
 */
public class CemStatus
{
    public boolean sitting;
    public boolean tamed;
    public boolean aggressive;
    public boolean onShoulder;
    public boolean burning;
    public boolean inLava;
    public boolean climbing;
    public boolean crawling;

    /** What the entity's health is multiplied by, so 1 is "as healthy as it says it is". */
    public float health = 1F;

    public void read(ModelForm form)
    {
        this.sitting = form.cemSitting.get();
        this.tamed = form.cemTamed.get();
        this.aggressive = form.cemAggressive.get();
        this.onShoulder = form.cemOnShoulder.get();
        this.burning = form.cemBurning.get();
        this.inLava = form.cemInLava.get();
        this.climbing = form.cemClimbing.get();
        this.crawling = form.cemCrawling.get();
        this.health = form.cemHealth.get();
    }
}
