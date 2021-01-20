package com.ahi.model_generator_from_postgres;

public class ModelProperty {

    private String lowerUnderscoreName; // column name
    private String lowerCamelCaseName; // property name
    private String javaDtoType;
    private String javaEntityType;
    private String jsType;
    private String upperCamelCaseName; // type or class name

    public ModelProperty(String lowerCamelCaseName, String lowerUnderscoreName, String javaDtoType, String javaEntityType, String jsType, String upperCamelCaseName) {
        this.lowerCamelCaseName = lowerCamelCaseName;
        this.lowerUnderscoreName = lowerUnderscoreName;
        this.javaDtoType = javaDtoType;
        this.javaEntityType = javaEntityType;
        this.jsType = jsType;
        if (javaEntityType.equals("Boolean") && lowerUnderscoreName.startsWith("is_")) {
            upperCamelCaseName = upperCamelCaseName.substring(2);
        }
        this.upperCamelCaseName = upperCamelCaseName;
    }

    public String getLowerCamelCaseName() {
        return lowerCamelCaseName;
    }

    public void setLowerCamelCaseName(String lowerCamelCaseName) {
        this.lowerCamelCaseName = lowerCamelCaseName;
    }

    public String getLowerUnderscoreName() {
        return lowerUnderscoreName;
    }

    public void setLowerUnderscoreName(String lowerUnderscoreName) {
        this.lowerUnderscoreName = lowerUnderscoreName;
    }

    public String getJavaDtoType() {
        return javaDtoType;
    }

    public void setJavaDtoType(String javaDtoType) {
        this.javaDtoType = javaDtoType;
    }

    public String getJavaEntityType() {
        return javaEntityType;
    }

    public void setJavaEntityType(String javaEntityType) {
        this.javaEntityType = javaEntityType;
    }

    public String getJsType() {
        return jsType;
    }

    public void setJsType(String jsType) {
        this.jsType = jsType;
    }

    public String getUpperCamelCaseName() {
        return upperCamelCaseName;
    }

    public void setUpperCamelCaseName(String upperCamelCaseName) {
        this.upperCamelCaseName = upperCamelCaseName;
    }
}
