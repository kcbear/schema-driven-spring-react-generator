/**
 * Reads and validates a JSON resource schema.
 * Converts the raw Jackson tree into the typed {@link ResourceModel} and {@link FieldModel} model used by generation.
 */
package dev.schemacodegen;
import com.fasterxml.jackson.databind.*; import java.nio.file.Path; import java.util.*;
final class SchemaLoader {
 private static final ObjectMapper JSON=new ObjectMapper(); private static final Set<String> TYPES=Set.of("string","integer","number","boolean","date","datetime");
 static ResourceModel load(Path p) throws Exception { JsonNode root=JSON.readTree(p.toFile()), r=root.path("resource"); String n=req(r,"name"), plural=req(r,"plural"), pkg=req(r,"package"), id=r.path("id").asText("id"); if(!n.matches("[A-Z][A-Za-z0-9]*")) throw new IllegalArgumentException("resource.name must be PascalCase"); if(!plural.matches("[a-z][a-z0-9-]*")) throw new IllegalArgumentException("resource.plural must be kebab-case"); if(!pkg.matches("[a-zA-Z_][\\w]*(\\.[a-zA-Z_][\\w]*)*")) throw new IllegalArgumentException("Invalid package"); List<FieldModel> fs=new ArrayList<>(); Set<String> names=new HashSet<>(); for(JsonNode f:root.path("fields")) { String fn=req(f,"name"),t=req(f,"type"); if(!fn.matches("[a-z][A-Za-z0-9]*")||!TYPES.contains(t)||!names.add(fn)) throw new IllegalArgumentException("Invalid, duplicate or unsupported field: "+fn); fs.add(new FieldModel(fn,t,f.path("label").asText(fn),f.path("required").asBoolean(false),f.path("editable").asBoolean(true),f.path("list").asBoolean(true))); } if(fs.isEmpty()||!names.contains(id)) throw new IllegalArgumentException("Fields must be non-empty and include resource.id"); int demoRecords=root.path("demoRecords").asInt(0); if(demoRecords<0) throw new IllegalArgumentException("demoRecords must be >= 0"); return new ResourceModel(n,plural,pkg,id,List.copyOf(fs),demoRecords); }
 private static String req(JsonNode n,String k) { String v=n.path(k).asText(""); if(v.isBlank()) throw new IllegalArgumentException("Missing "+k); return v; }
}
