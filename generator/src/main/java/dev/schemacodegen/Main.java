package dev.schemacodegen;
public final class Main { private Main() {} public static void main(String[] args) throws Exception { CliOptions o=CliOptions.parse(args); ResourceModel m=SchemaLoader.load(o.schema()); ProjectGenerator.generate(m,o.output(),o.force()); System.out.println("Generated at "+o.output().toAbsolutePath()); } }
