package mchorse.bbs_mod.resources.packs;

import mchorse.bbs_mod.api.client.events.OrderedEvent;

public interface URLTextureErrorCallback
{
    OrderedEvent<URLTextureErrorCallback> EVENT = new OrderedEvent<>(listeners -> (url, error) ->
    {
        for (URLTextureErrorCallback listener : listeners) listener.onError(url, error);
    });
    void onError(String url, URLError error);
}
