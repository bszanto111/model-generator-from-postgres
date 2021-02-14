package com.ahi.model_generator_from_postgres;

import com.google.common.base.CaseFormat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClassModel {
    private String extendedClass;
    private String upperCamelCaseName; // class name
    private String lowerUnderscoreName; // table name
    private String lowerCamelCaseName;
    private String lowerHyphenName; // endpoint name (+s)
    private String upperCamelCaseNameInPlural;
    private String lowerCamelCaseNameInPlural;
    private String lowerHyphenNameInPlural;
    private List<ModelProperty> properties = new ArrayList<>();
    private Set<String> javaImportLines = new HashSet<>();
    private Set<String> jsImportLines = new HashSet<>();

    public ClassModel(String upperCamelCaseName, String lowerUnderscoreName) {
        if (upperCamelCaseName.endsWith("y")) {
            upperCamelCaseNameInPlural = upperCamelCaseName.substring(0, upperCamelCaseName.length() - 1) + "ies";
        }
        else if (upperCamelCaseName.endsWith("s") || upperCamelCaseName.endsWith("x")) {
            upperCamelCaseNameInPlural = upperCamelCaseName + "es";
        }
        else {
            upperCamelCaseNameInPlural = upperCamelCaseName + "s";
        }
        this.upperCamelCaseName = upperCamelCaseName;
        this.lowerUnderscoreName = lowerUnderscoreName;
        this.lowerCamelCaseName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, upperCamelCaseName);
        this.lowerHyphenName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, upperCamelCaseName);
        this.lowerCamelCaseNameInPlural = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, upperCamelCaseNameInPlural);
        this.lowerHyphenNameInPlural = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, upperCamelCaseNameInPlural);
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

    public String getLowerCamelCaseName() {
        return lowerCamelCaseName;
    }

    public void setLowerCamelCaseName(String lowerCamelCaseName) {
        this.lowerCamelCaseName = lowerCamelCaseName;
    }

    public String getLowerHyphenName() {
        return lowerHyphenName;
    }

    public void setLowerHyphenName(String lowerHyphenName) {
        this.lowerHyphenName = lowerHyphenName;
    }

    public String getUpperCamelCaseNameInPlural() {
        return upperCamelCaseNameInPlural;
    }

    public void setUpperCamelCaseNameInPlural(String upperCamelCaseNameInPlural) {
        this.upperCamelCaseNameInPlural = upperCamelCaseNameInPlural;
    }

    public String getLowerCamelCaseNameInPlural() {
        return lowerCamelCaseNameInPlural;
    }

    public void setLowerCamelCaseNameInPlural(String lowerCamelCaseNameInPlural) {
        this.lowerCamelCaseNameInPlural = lowerCamelCaseNameInPlural;
    }

    public String getLowerHyphenNameInPlural() {
        return lowerHyphenNameInPlural;
    }

    public void setLowerHyphenNameInPlural(String lowerHyphenNameInPlural) {
        this.lowerHyphenNameInPlural = lowerHyphenNameInPlural;
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
