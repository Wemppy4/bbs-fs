package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.resources.Link;

public class VideoOverlay extends ImageOverlay
{
    public Link video;
    public float seconds;

    public void updateVideo(Link video, float seconds)
    {
        this.video = video;
        this.seconds = seconds;
    }
}
