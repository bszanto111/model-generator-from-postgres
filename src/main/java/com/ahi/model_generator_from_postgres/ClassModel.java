package com.ahi.model_generator_from_postgres;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClassModel {
    private String extendedClass;
    private String upperCamelCaseName; // class name
    private String lowerUnderscoreName; // table name
    private List<ModelProperty> properties = new ArrayList<>();
    private Set<String> javaImportLines = new HashSet<>();
    private Set<String> jsImportLines = new HashSet<>();

    public ClassModel(String upperCamelCaseName, String lowerUnderscoreName) {
        this.upperCamelCaseName = upperCamelCaseName;
        this.lowerUnderscoreName = lowerUnderscoreName;
    }

    public String getLowerUnderscoreName() {
        return lowerUnderscoreName;
    }

    public void setLowerUnderscoreName(String lowerUnderscoreName) {
        this.lowerUnderscoreName = lowerUnderscoreName;
    }

    public Set<String> getJavaImportLines() {
        return javaImportLines;
    }

    public void setJavaImportLines(Set<String> javaImportLines) {
        this.javaImportLines = javaImportLines;
    }

    public void addJavaImportLine(String importLine) {
        this.javaImportLines.add(importLine);
    }

    public Set<String> getJsImportLines() {
        return jsImportLines;
    }

    public void setJsImportLines(Set<String> jsImportLines) {
        this.jsImportLines = jsImportLines;
    }

    public void addJsImportLine(String importLine) {
        this.jsImportLines.add(importLine);
    }

    public String getExtendedClass() {
        return extendedClass;
    }

    public void setExtendedClass(String extendedClass) {
        this.extendedClass = extendedClass;
    }

    public String getUpperCamelCaseName() {
        return upperCamelCaseName;
    }

    public void setUpperCamelCaseName(String upperCamelCaseName) {
        this.upperCamelCaseName = upperCamelCaseName;
    }

    public List<ModelProperty> getProperties() {
        return properties;
    }

    public void setProperties(List<ModelProperty> properties) {
        this.properties = properties;
    }

    public void addProperty(ModelProperty modelProperty) {
        properties.add(modelProperty);
    }

    public void removeProperty(String propertyName) {
        properties.removeIf(x -> x.getLowerCamelCaseName().equals(propertyName));
    }
}
