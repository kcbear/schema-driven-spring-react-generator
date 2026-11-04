package dev.schemacodegen;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
class SchemaLoaderTest {
 @Test void loadsGlobalTradingExample() throws Exception {
  ResourceModel m=SchemaLoader.load(Path.of("../examples/purchase-order.json"));
  assertEquals("Instrument",m.name());
  assertEquals("instruments",m.plural());
  assertEquals(10000,m.demoRecords());
  assertTrue(m.fields().stream().anyMatch(f->f.name().equals("instrumentType")));
  assertTrue(m.fields().stream().anyMatch(f->f.name().equals("symbol")));
 }
}
