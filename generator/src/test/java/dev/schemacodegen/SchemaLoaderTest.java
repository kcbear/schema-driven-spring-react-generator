package dev.schemacodegen;
import static org.junit.jupiter.api.Assertions.*; import java.nio.file.Path; import org.junit.jupiter.api.Test;
class SchemaLoaderTest { @Test void loadsExample() throws Exception { ResourceModel m=SchemaLoader.load(Path.of("../examples/purchase-order.json")); assertEquals("PurchaseOrder",m.name()); assertEquals(6,m.fields().size()); } }
