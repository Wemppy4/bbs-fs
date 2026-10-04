package mchorse.bbs_mod.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Forge addon entrypoint, instantiated once before the corresponding BBS registry events. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BBSAddon {
    /** Client handlers are never loaded or instantiated on a dedicated server. */
    boolean clientOnly() default false;
}