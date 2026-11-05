/**
 * Immutable command-line configuration for a generation run.
 * Holds the input schema path, output directory and overwrite permission parsed by {@link #parse(String[])}.
 */
package dev.schemacodegen;
import java.nio.file.Path;
record CliOptions(Path schema, Path output, boolean force) {
 static CliOptions parse(String[] a) { Path s=null,o=Path.of("build/generated"); boolean f=false; for(int i=0;i<a.length;i++) switch(a[i]) { case "--schema" -> s=Path.of(value(a,++i)); case "--output" -> o=Path.of(value(a,++i)); case "--force" -> f=true; default -> throw new IllegalArgumentException("Unknown option "+a[i]); } if(s==null) throw new IllegalArgumentException("Usage: --schema <file> [--output <dir>] [--force]"); return new CliOptions(s,o,f); }
 private static String value(String[] a,int i) { if(i>=a.length) throw new IllegalArgumentException("Missing option value"); return a[i]; }
}
