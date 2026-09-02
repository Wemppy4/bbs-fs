package mchorse.bbs_mod.cubic.animation;

/**
 * A model's bone spoken in vanilla's terms: radians, vanilla's own directions.
 *
 * <p>Cubic and BOBJ rigs disagree with vanilla, and with each other, about units and about which
 * way each axis turns - and the two are posed by near-duplicate halves of
 * {@link ProceduralAnimator}. Anything lifted from vanilla is written once against this, and
 * each rig hands over an adapter that does its own conversion, so a pose taken off the bytecode
 * doesn't have to be transcribed twice with the signs guessed twice.</p>
 */
public interface VanillaBone
{
    public float pitch();

    public void pitch(float pitch);

    public float yaw();

    public void yaw(float yaw);

    public float roll();

    public void roll(float roll);
}
