package com.example.client.module.annotation;

import com.example.client.language.Text;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModuleInfo {
    Text[] name();

    int key() default 0;

    boolean enable() default false;
}
