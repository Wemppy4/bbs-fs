package mchorse.bbs_mod.ui.utils.resizers;

public abstract class DecoratedResizer implements IResizer, IParentResizer
{
    public IResizer resizer;

    public DecoratedResizer(IResizer resizer)
    {
        this.resizer = resizer;
    }
}