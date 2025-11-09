package org.openmaptiles.util;

import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to filter tile attributes based on what's actually used in a Mapbox GL style.
 * This helps reduce tile size by removing unused attributes.
 */
public class StyleAttributeFilter {

  private static final Logger LOGGER = LoggerFactory.getLogger(StyleAttributeFilter.class);

  /**
   * Defines which attributes each layer should retain based on the style definition.
   * Extracted from the style JSON provided by analyzing filters, paint properties, and layout properties.
   */
  private static final Map<String, Set<String>> LAYER_ATTRIBUTES = new HashMap<>();

  /**
   * Tracks filtered out attributes per layer (thread-safe for concurrent tile processing).
   */
  private static final Map<String, Set<String>> FILTERED_ATTRIBUTES = new ConcurrentHashMap<>();

  static {
    // park layer - no specific attribute filters in style
    LAYER_ATTRIBUTES.put("park", Set.of());
    
    // landcover layer - uses "class" attribute in filters and paint
    LAYER_ATTRIBUTES.put("landcover", Set.of("class"));
    
    // landuse layer - uses "class" attribute in filters and paint
    LAYER_ATTRIBUTES.put("landuse", Set.of("class"));
    
    // water layer - uses "brunnel" attribute in filters
    LAYER_ATTRIBUTES.put("water", Set.of("brunnel"));
    
    // aeroway layer - uses "class" attribute in filters and paint
    LAYER_ATTRIBUTES.put("aeroway", Set.of("class"));
    
    // transportation layer - uses "class", "brunnel", "ramp" attributes
    LAYER_ATTRIBUTES.put("transportation", Set.of("class", "brunnel", "ramp"));
    
    // waterway layer - uses "brunnel" and "class" attributes
    LAYER_ATTRIBUTES.put("waterway", Set.of("brunnel", "class"));
    
    // boundary layer - uses "admin_level" and "maritime" attributes
    LAYER_ATTRIBUTES.put("boundary", Set.of("admin_level", "maritime"));
    
    // building layer - no specific attributes in style filters/paint
    LAYER_ATTRIBUTES.put("building", Set.of());
    
    // place layer - uses "class", "name", "name_en", "rank"
    LAYER_ATTRIBUTES.put("place", Set.of(
      "class", "name", "name_en", "rank"
    ));
    
    // poi layer - uses "class", "name", "name_en", "rank"
    LAYER_ATTRIBUTES.put("poi", Set.of(
      "class", "name", "name_en", "rank"
    ));
    
    // transportation_name layer - uses "class", "name", "name_en"
    LAYER_ATTRIBUTES.put("transportation_name", Set.of(
      "class", "name", "name_en"
    ));
  }

  /**
   * Post-processes vector tile features to retain only the attributes used in the style.
   * This reduces tile size by removing unused attributes.
   *
   * @param layerName the name of the layer being processed
   * @param zoom the zoom level
   * @param items the list of features to process
   * @return the filtered list of features
   */
  public static List<VectorTile.Feature> filterAttributes(String layerName, int zoom,
    List<VectorTile.Feature> items) throws GeometryException {
    
    Set<String> allowedAttributes = LAYER_ATTRIBUTES.get(layerName);
    
    // If layer not in our map or no filtering defined, return as-is
    if (allowedAttributes == null) {
      return items;
    }
    
    // Get or create set for tracking filtered attributes for this layer
    Set<String> filteredForLayer = FILTERED_ATTRIBUTES.computeIfAbsent(layerName, k -> ConcurrentHashMap.newKeySet());
    
    // If empty set, remove all attributes
    if (allowedAttributes.isEmpty()) {
      for (VectorTile.Feature feature : items) {
        // Track all removed attributes
        filteredForLayer.addAll(feature.tags().keySet());
        feature.tags().clear();
      }
      return items;
    }
    
    // Filter attributes to only keep allowed ones
    for (VectorTile.Feature feature : items) {
      Map<String, Object> tags = feature.tags();
      Set<String> keysToRemove = new HashSet<>();
      for (String key : tags.keySet()) {
        if (!allowedAttributes.contains(key) && !isNameVariant(key, allowedAttributes)) {
          keysToRemove.add(key);
        }
      }
      // Track filtered attributes
      filteredForLayer.addAll(keysToRemove);
      // Remove them
      keysToRemove.forEach(tags::remove);
    }
    
    return items;
  }

  /**
   * Checks if a key is an allowed name variant.
   * Only name and name_en are retained - all other language variants are filtered out.
   */
  private static boolean isNameVariant(String key, Set<String> allowedAttributes) {
    // Only allow name_en if name is in the allowed attributes
    if (allowedAttributes.contains("name") && "name_en".equals(key)) {
      return true;
    }
    return false;
  }

  /**
   * Gets the set of allowed attributes for a given layer.
   * 
   * @param layerName the layer name
   * @return set of allowed attribute names, or null if layer is not configured
   */
  public static Set<String> getAllowedAttributes(String layerName) {
    return LAYER_ATTRIBUTES.get(layerName);
  }

  /**
   * Checks if a layer has attribute filtering configured.
   */
  public static boolean hasFilteringForLayer(String layerName) {
    return LAYER_ATTRIBUTES.containsKey(layerName);
  }

  /**
   * Logs a summary of filtered attributes per layer.
   * Should be called after tile processing is complete.
   */
  public static void logFilteredAttributes() {
    if (FILTERED_ATTRIBUTES.isEmpty()) {
      LOGGER.info("No attributes were filtered");
      return;
    }

    LOGGER.info("=== Attribute Filtering Summary ===");
    FILTERED_ATTRIBUTES.entrySet().stream()
      .sorted(Map.Entry.comparingByKey())
      .forEach(entry -> {
        String layerName = entry.getKey();
        Set<String> filtered = entry.getValue();
        if (!filtered.isEmpty()) {
          LOGGER.info("Layer '{}': Filtered {} attributes: {}", 
            layerName, filtered.size(), String.join(", ", filtered));
        }
      });
    LOGGER.info("=== End Attribute Filtering Summary ===");
  }

  /**
   * Resets the tracked filtered attributes (useful for testing).
   */
  public static void resetFilteredAttributes() {
    FILTERED_ATTRIBUTES.clear();
  }
}
