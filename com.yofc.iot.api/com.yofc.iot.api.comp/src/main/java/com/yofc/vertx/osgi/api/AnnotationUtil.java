package com.yofc.vertx.osgi.api;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

class AnnotationUtil implements InvocationHandler {

  @SuppressWarnings("unchecked")
  public static <A extends Annotation> A of(Class<A> annotation) {
    return (A)
        Proxy.newProxyInstance(
            annotation.getClassLoader(), new Class[] {annotation}, new AnnotationUtil());
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    return method.getDefaultValue();
  }
}
