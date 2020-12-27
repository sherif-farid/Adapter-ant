package com.sherif ;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target( ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface CreateAdapter  {
    String name();
    Class<?> model();
    Class<?> Row();
}