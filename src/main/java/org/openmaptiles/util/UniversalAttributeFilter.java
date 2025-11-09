package org.openmaptiles.util;

import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal post-processor that filters attributes for all layers based on style requirements.
 * This uses a dynamic proxy to wrap any layer object while preserving all its interfaces,
 * intercepting only the postProcess method to add attribute filtering.
 */
public class UniversalAttributeFilter implements InvocationHandler {

  // Cache for interface arrays to avoid recomputation
  private static final ConcurrentHashMap<Class<?>, Class<?>[]> interfaceCache = new ConcurrentHashMap<>();

  private final Object wrappedLayer;
  private final String layerName;

  /**
   * Creates a filter that wraps an existing layer using a dynamic proxy.
   *
   * @param layerName the name of the layer
   * @param wrappedLayer the layer to wrap
   */
  private UniversalAttributeFilter(String layerName, Object wrappedLayer) {
    this.layerName = layerName;
    this.wrappedLayer = wrappedLayer;
  }

  /**
   * Creates a dynamic proxy that wraps the given layer, preserving all its interfaces
   * while intercepting the postProcess method to add attribute filtering.
   *
   * @param layerName the name of the layer
   * @param layer the layer to wrap (must implement ForwardingProfile.LayerPostProcessor)
   * @return a proxy that implements all interfaces of the wrapped layer
   */
  public static Object wrap(String layerName, Object layer) {
    if (!(layer instanceof ForwardingProfile.LayerPostProcessor)) {
      throw new IllegalArgumentException("Layer must implement LayerPostProcessor");
    }

    // Get all interfaces implemented by the layer and its superclasses
    Class<?>[] interfaces = getAllInterfaces(layer.getClass());

    // Create dynamic proxy
    return Proxy.newProxyInstance(
      layer.getClass().getClassLoader(),
      interfaces,
      new UniversalAttributeFilter(layerName, layer)
    );
  }

  /**
   * Collects all interfaces implemented by the given class and its superclasses.
   * Results are cached to avoid recomputation for the same class.
   */
  private static Class<?>[] getAllInterfaces(Class<?> clazz) {
    return interfaceCache.computeIfAbsent(clazz, key -> {
      java.util.Set<Class<?>> interfaces = new java.util.LinkedHashSet<>();
      collectInterfaces(key, interfaces);
      return interfaces.toArray(new Class<?>[0]);
    });
  }

  /**
   * Recursively collects all interfaces from a class and its superclasses.
   */
  private static void collectInterfaces(Class<?> clazz, java.util.Set<Class<?>> interfaces) {
    if (clazz == null || clazz == Object.class) {
      return;
    }

    // Add direct interfaces
    for (Class<?> iface : clazz.getInterfaces()) {
      interfaces.add(iface);
      // Recursively add parent interfaces
      collectInterfaces(iface, interfaces);
    }

    // Process superclass
    collectInterfaces(clazz.getSuperclass(), interfaces);
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // Intercept postProcess method to add attribute filtering
    if ("postProcess".equals(method.getName()) && 
        method.getParameterCount() == 2 &&
        method.getParameterTypes()[0] == int.class &&
        method.getParameterTypes()[1] == List.class) {
      
      // First, call the original postProcess method
      @SuppressWarnings("unchecked")
      List<VectorTile.Feature> items = (List<VectorTile.Feature>) method.invoke(wrappedLayer, args);
      
      // Then apply attribute filtering
      int zoom = (int) args[0];
      try {
        return StyleAttributeFilter.filterAttributes(layerName, zoom, items);
      } catch (GeometryException e) {
        throw new RuntimeException("Error filtering attributes for layer " + layerName, e);
      }
    }

    // Intercept name() method to ensure consistent layer name
    if ("name".equals(method.getName()) && method.getParameterCount() == 0) {
      return layerName;
    }

    // For all other methods, delegate to the wrapped layer
    return method.invoke(wrappedLayer, args);
  }
}
