package mchorse.bbs_mod.forms.entities;

/** BBS actor gait, using the limbSwing/limbSwingAmount interpolation of 1.12.2. */
public final class LimbAnimation
{
    private float previousSpeed;
    private float speed;
    private float position;

    public void updateLimbs(float target, float damping)
    {
        this.previousSpeed = this.speed;
        this.speed += (target - this.speed) * damping;
        this.position += this.speed;
    }

    public void setSpeed(float speed) { this.speed = speed; }
    public float getSpeed() { return this.speed; }
    public float getPos() { return this.position; }
    public float getSpeed(float partial) { return this.previousSpeed + (this.speed - this.previousSpeed) * partial; }
    public float getPos(float partial) { return this.position - this.speed * (1F - partial); }
}
