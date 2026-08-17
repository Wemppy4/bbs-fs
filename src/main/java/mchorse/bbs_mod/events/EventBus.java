package mchorse.bbs_mod.events;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscribers = new HashMap<>();

    /**
     * Registers the given subscriber to receive events.
     *
     * <p>Methods are collected from the whole class hierarchy rather than from the subscriber's
     * own class alone, so an addon can keep shared subscriptions in a base class. The most
     * specific declaration of a method wins: an override replaces the method it overrides
     * instead of being called next to it, and an override that drops {@link Subscribe}
     * unsubscribes it.</p>
     */
    public void register(Object subscriber)
    {
        Set<String> visited = new HashSet<>();

        for (Class<?> clazz = subscriber.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass())
        {
            for (Method method : clazz.getDeclaredMethods())
            {
                if (visited.add(getSignature(method)))
                {
                    this.subscribe(subscriber, method);
                }
            }
        }
    }

    private static String getSignature(Method method)
    {
        StringBuilder builder = new StringBuilder(method.getName());

        for (Class<?> type : method.getParameterTypes())
        {
            builder.append(':').append(type.getName());
        }

        return builder.toString();
    }

    private void subscribe(Object subscriber, Method method)
    {
        if (method.isAnnotationPresent(Subscribe.class))
        {
            if (method.getParameterCount() != 1)
            {
                return;
            }

            this.subscribers
                .computeIfAbsent(method.getParameterTypes()[0], (clazz) -> new CopyOnWriteArrayList<>())
                .add(new Subscription(subscriber, method));
        }
    }

    /**
     * Posts the given event to the event bus.
     *
     * <p>Subscribers of the event's own class are called first, then those of its super classes,
     * so subscribing to a base event type receives every event derived from it.</p>
     */
    public void post(Object event)
    {
        for (Class<?> clazz = event.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass())
        {
            this.post(event, this.subscribers.get(clazz));
        }
    }

    private void post(Object event, CopyOnWriteArrayList<Subscription> eventSubscribers)
    {
        if (eventSubscribers == null || eventSubscribers.isEmpty())
        {
            return;
        }

        for (Subscription subscription : eventSubscribers)
        {
            try
            {
                subscription.method.invoke(subscription.target, event);
            }
            catch (Throwable e)
            {
                /* A subscriber blowing up used to vanish without a trace, which made a broken
                 * addon indistinguishable from an absent one — the single nastiest thing to debug
                 * on the addon side. Whatever it did wrong is not this bus's business to fix, but
                 * it is its business to say so, and to keep the remaining subscribers running. */
                Throwable cause = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;

                LOGGER.error("Subscriber {}.{}() failed to handle {}!",
                    subscription.target.getClass().getName(),
                    subscription.method.getName(),
                    event.getClass().getSimpleName(),
                    cause);
            }
        }
    }
}
