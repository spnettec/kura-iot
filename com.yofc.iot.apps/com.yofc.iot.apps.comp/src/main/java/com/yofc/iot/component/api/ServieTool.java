package com.yofc.iot.component.api;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;

public class ServieTool {

  public static final String KURA_SERVICE_PID = "kura.service.pid";
  private final BundleContext bundleContext;
  private final Map<String, ServiceReference<?>> refsMap = new HashMap<>();
  private final Map<String, Object> dataMap = new HashMap<>();

  public ServieTool() {
    this.bundleContext = FrameworkUtil.getBundle(ServieTool.class).getBundleContext();
  }

  @SuppressWarnings("unchecked")
  public Object getServiceByPid(String pid) {
    ServiceReference<?> ref = refsMap.get(pid);
    if (ref != null) {
      this.bundleContext.ungetService(ref);
      refsMap.remove(pid);
    }
    final String filter = format("(%s=%s)", KURA_SERVICE_PID, pid);
    ServiceReference<?>[] refs;
    try {
      refs = this.bundleContext.getServiceReferences((String) null, filter);
    } catch (InvalidSyntaxException e) {
      return null;
    }
    if (refs == null) {
      return null;
    }
    if (refs.length > 1) {
      return null;
    }
    Object service = getService((ServiceReference<Object>) refs[0]);
    if (service != null) {
      refsMap.put(pid, refs[0]);
    }
    return service;
  }

  private <T> T getService(ServiceReference<T> serviceReference) {
    T service = null;

    if (serviceReference != null) {
      service = this.bundleContext.getService(serviceReference);
    }
    return service;
  }

  public void ungetServices() {
    refsMap.forEach((key, ref) -> ServieTool.this.bundleContext.ungetService(ref));
    refsMap.clear();
  }

  public Map<String, Object> getDataMap() {
    return dataMap;
  }

  public void clearDataMap() {
    dataMap.clear();
  }
}
