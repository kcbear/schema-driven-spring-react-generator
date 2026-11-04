package dev.schemacodegen;
import java.util.List;
record ResourceModel(String name,String plural,String packageName,String idField,List<FieldModel> fields,int demoRecords) {}
