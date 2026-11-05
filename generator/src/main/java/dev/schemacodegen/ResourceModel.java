/**
 * Immutable, validated description of one resource to generate.
 * Carries resource-level metadata and the ordered field definitions shared by backend and frontend generation.
 */
package dev.schemacodegen;
import java.util.List;
record ResourceModel(String name,String plural,String packageName,String idField,List<FieldModel> fields,int demoRecords) {}
