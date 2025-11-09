package org.openmaptiles.util;

import static org.junit.jupiter.api.Assertions.*;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.List;
import org.junit.jupiter.api.Test;

class UniversalAttributeFilterTest {

  // Mock layer that implements multiple interfaces
  static class TestLayer implements 
      ForwardingProfile.LayerPostProcessor,
      ForwardingProfile.Handler,
      TestInterface {
    
    @Override
    public String name() {
      return "test";
    }
    
    @Override
    public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items) {
      return items;
    }
    
    @Override
    public String testMethod() {
      return "original";
    }
  }
  
  interface TestInterface {
    String testMethod();
  }

  @Test
  void testProxyPreservesAllInterfaces() {
    TestLayer layer = new TestLayer();
    Object proxy = UniversalAttributeFilter.wrap("test", layer);
    
    // Verify the proxy implements all the original interfaces
    assertTrue(proxy instanceof ForwardingProfile.LayerPostProcessor,
        "Proxy should implement LayerPostProcessor");
    assertTrue(proxy instanceof ForwardingProfile.Handler,
        "Proxy should implement Handler");
    assertTrue(proxy instanceof TestInterface,
        "Proxy should implement custom TestInterface");
  }

  @Test
  void testProxyDelegatesMethods() {
    TestLayer layer = new TestLayer();
    Object proxy = UniversalAttributeFilter.wrap("test", layer);
    
    // Test that methods are properly delegated
    TestInterface testProxy = (TestInterface) proxy;
    assertEquals("original", testProxy.testMethod(),
        "Proxy should delegate method calls to wrapped layer");
  }

  @Test
  void testProxyReturnsCorrectName() {
    TestLayer layer = new TestLayer();
    Object proxy = UniversalAttributeFilter.wrap("custom-name", layer);
    
    ForwardingProfile.LayerPostProcessor postProcessor = 
        (ForwardingProfile.LayerPostProcessor) proxy;
    assertEquals("custom-name", postProcessor.name(),
        "Proxy should return the provided layer name");
  }

  @Test
  void testProxyPostProcessIsIntercepted() throws GeometryException {
    TestLayer layer = new TestLayer();
    Object proxy = UniversalAttributeFilter.wrap("boundary", layer);
    
    ForwardingProfile.LayerPostProcessor postProcessor = 
        (ForwardingProfile.LayerPostProcessor) proxy;
    
    // This should not throw an exception
    assertDoesNotThrow(() -> {
      postProcessor.postProcess(5, List.of());
    }, "Proxy should intercept and handle postProcess calls");
  }

  @Test
  void testWrapRequiresLayerPostProcessor() {
    Object notAPostProcessor = new Object();
    
    assertThrows(IllegalArgumentException.class, () -> {
      UniversalAttributeFilter.wrap("test", notAPostProcessor);
    }, "wrap() should require LayerPostProcessor interface");
  }

  @Test
  void testInterfaceCachingWorks() {
    // Create two instances of the same class
    TestLayer layer1 = new TestLayer();
    TestLayer layer2 = new TestLayer();
    
    // Wrap them both - this should use the cached interfaces for the second one
    Object proxy1 = UniversalAttributeFilter.wrap("test1", layer1);
    Object proxy2 = UniversalAttributeFilter.wrap("test2", layer2);
    
    // Both proxies should implement the same interfaces
    assertTrue(proxy1 instanceof ForwardingProfile.LayerPostProcessor);
    assertTrue(proxy2 instanceof ForwardingProfile.LayerPostProcessor);
    assertTrue(proxy1 instanceof TestInterface);
    assertTrue(proxy2 instanceof TestInterface);
    
    // Verify they work independently
    assertEquals("test1", ((ForwardingProfile.LayerPostProcessor) proxy1).name());
    assertEquals("test2", ((ForwardingProfile.LayerPostProcessor) proxy2).name());
  }
}
