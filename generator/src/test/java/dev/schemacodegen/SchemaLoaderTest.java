package dev.schemacodegen;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaLoaderTest {
 @Test void loadsGlobalTradingExample() throws Exception {
  ResourceModel m=SchemaLoader.load(Path.of("../examples/global-instruments.json"));
  assertEquals("Instrument",m.name());
  assertEquals("instruments",m.plural());
  assertEquals(10000,m.demoRecords());
  assertTrue(m.fields().stream().anyMatch(f->f.name().equals("instrumentType")));
  assertTrue(m.fields().stream().anyMatch(f->f.name().equals("symbol")));
 }
 @Test void generatesTenThousandInstrumentSeedAndPagedApi(@TempDir Path temp) throws Exception {
  ResourceModel m=SchemaLoader.load(Path.of("../examples/global-instruments.json"));
  Path output=temp.resolve("generated");
  ProjectGenerator.generate(m,output,false);
  String initializer=Files.readString(output.resolve("backend/src/main/java/com/example/globaltrading/DemoDataInitializer.java"));
  String controller=Files.readString(output.resolve("backend/src/main/java/com/example/globaltrading/InstrumentController.java"));
  assertTrue(initializer.contains("i<10000"));
  assertTrue(initializer.contains("\"ETF\""));
  assertTrue(initializer.contains("\"STOCK\""));
  assertTrue(controller.contains("defaultValue=\\\"50\\\""));
  assertTrue(controller.contains("PageRequest.of(page"));
 }
}
