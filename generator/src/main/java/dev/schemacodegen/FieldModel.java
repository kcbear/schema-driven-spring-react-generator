/**
 * Immutable definition of one resource field.
 * Combines the field's data type and name with labels and flags used to generate the model, table columns and form inputs.
 */
package dev.schemacodegen;
record FieldModel(String name,String type,String label,boolean required,boolean editable,boolean list) {}
