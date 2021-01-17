package com.ahi.model_generator_from_postgres;

import com.google.common.base.CaseFormat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Optional;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final Pattern createTablePattern = Pattern.compile("CREATE TABLE (public.)?(\\w+)");
    private static final Pattern columnPattern = Pattern.compile("(\\w+)\\s(\\w+).*,");
    private static String sqlFilePath;
    private static String jpaEntityDirectoryPath;
    private static String javaDtoDirectoryPath;
    private static String mapStructDirectoryPath;
    private static String typeScriptDirectoryPath;

    public static void main(String[] args) {
        if (args.length < 5) {
            System.out.println("Parameters needed:");
            System.out.println("<SQL file path> <JPA Entity directory path> <Java DTO directory path> <MapStruct directory path> <TypeScript DTO directory path>");
        }
        sqlFilePath = args[0];
        jpaEntityDirectoryPath = args[1];
        javaDtoDirectoryPath = args[2];
        mapStructDirectoryPath = args[3];
        typeScriptDirectoryPath = args[4];
        generate();
    }

    private static void generate() {
        File sqlFile = new File(sqlFilePath);
        ClassModel currentClassModel = null;
        boolean currentlyProcessingATable = false;
        try (Scanner myReader = new Scanner(sqlFile)) {
            while (myReader.hasNextLine()) {
                String line = myReader.nextLine();
                if (line.isBlank()) {
                    continue;
                }
                Matcher createTableMatcher = createTablePattern.matcher(line);
                if (!currentlyProcessingATable && createTableMatcher.find()) {
                    currentlyProcessingATable = true;
                    if (currentClassModel != null) { // Write the previous table's classes
                        createJpaEntityClass(currentClassModel);
                        createJavaDtoClass(currentClassModel);
                        createMapStructMapperInterface(currentClassModel);
                        createTypeScriptClass(currentClassModel);
                    }
                    String tableName = createTableMatcher.group(2);
                    String dtoClassName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, tableName);
                    currentClassModel = new ClassModel(dtoClassName, tableName);
                }
                else if (currentlyProcessingATable) {
                    Matcher columnPatternMatcher = columnPattern.matcher(line);
                    if (line.contains("CONSTRAINT")) { // Skip each remaining line of the table description after CONSTRAINT keyword is found.
                        currentlyProcessingATable = false;
                    }
                    else if (columnPatternMatcher.find()) {
                        retrieveAndSaveProperty(currentClassModel, columnPatternMatcher);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    private static void retrieveAndSaveProperty(ClassModel currentClassModel, Matcher columnPatternMatcher) {
        String columnName = columnPatternMatcher.group(1);
        String upperCamelCaseName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, columnName);
        boolean isReferenceColumn = upperCamelCaseName.length() > 2 && upperCamelCaseName.endsWith("Id");
        if (isReferenceColumn) {
            upperCamelCaseName = upperCamelCaseName.substring(0, upperCamelCaseName.length() - 2);
        }
        String lowerCamelCaseName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, upperCamelCaseName);
        String javaDtoType;
        String javaEntityType;
        String jsType;
        if (isReferenceColumn) {
            javaDtoType = upperCamelCaseName;
            javaEntityType = upperCamelCaseName + "Entity";
            jsType = upperCamelCaseName;
            String lowerHyphenName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, upperCamelCaseName);
            currentClassModel.addJsImportLine("import { " + upperCamelCaseName + " } from './" + lowerHyphenName + "';");
        }
        else {
            switch (columnPatternMatcher.group(2)) {
                case "SERIAL":
                    javaDtoType = "int";
                    jsType = "number";
                    break;
                case "text":
                    javaDtoType = "String";
                    jsType = "string";
                    break;
                case "boolean":
                    javaDtoType = "Boolean";
                    jsType = "boolean";
                    break;
                case "date":
                    javaDtoType = "LocalDate";
                    jsType = "Date";
                    currentClassModel.addJavaImportLine("import java.time.LocalDate;");
                    break;
                case "timestamp":
                    javaDtoType = "LocalDateTime";
                    jsType = "Date";
                    currentClassModel.addJavaImportLine("import java.time.LocalDateTime;");
                    break;
                case "numeric":
                    javaDtoType = "BigDecimal";
                    currentClassModel.addJavaImportLine("import java.math.BigDecimal;");
                    jsType = "number";
                    break;
                default:
                    javaDtoType = "Integer";
                    jsType = "number";
            }
            javaEntityType = javaDtoType;
        }
        ModelProperty modelProperty = new ModelProperty(lowerCamelCaseName, columnName, javaDtoType, javaEntityType, jsType, upperCamelCaseName);
        currentClassModel.addProperty(modelProperty);
    }

    private static void createTypeScriptClass(ClassModel currentClassModel) {
        String tsFileName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, currentClassModel.getUpperCamelCaseName());
        try (FileWriter fileWriter = new FileWriter(typeScriptDirectoryPath
                + "\\" + tsFileName + ".ts")) {

            for (String importLine : currentClassModel.getJsImportLines()) {
                fileWriter.write(importLine);
                fileWriter.write(System.lineSeparator());
            }
            if (!currentClassModel.getJsImportLines().isEmpty()) {
                fileWriter.write(System.lineSeparator());
            }
            String extendedDtoInterface = currentClassModel.getExtendedClass();

            StringBuilder interfaceDeclarationBuilder = new StringBuilder("export interface ")
                    .append(currentClassModel.getUpperCamelCaseName());
            if (extendedDtoInterface != null) {
                interfaceDeclarationBuilder.append(" extends ").append(extendedDtoInterface);
            }
            interfaceDeclarationBuilder.append(" {");
            fileWriter.write(interfaceDeclarationBuilder.toString());
            fileWriter.write(System.lineSeparator());
            for (ModelProperty property : currentClassModel.getProperties()) {
                fileWriter.write("    " + property.getLowerCamelCaseName() + "?: " + property.getJsType() + ";");
                fileWriter.write(System.lineSeparator());
            }
            fileWriter.write("}");
        }
        catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    private static void createJavaDtoClass(ClassModel currentClassModel) {
        try (FileWriter fileWriter = new FileWriter(javaDtoDirectoryPath
                + "\\" + currentClassModel.getUpperCamelCaseName() + ".java")) {

            fileWriter.write("package com.ahi.prop_man.rest.dto;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            for (String importLine : currentClassModel.getJavaImportLines()) {
                fileWriter.write(importLine);
                fileWriter.write(System.lineSeparator());
            }
            if (!currentClassModel.getJavaImportLines().isEmpty()) {
                fileWriter.write(System.lineSeparator());
            }
            String extendedEntityClass = currentClassModel.getExtendedClass();

            StringBuilder classDeclarationBuilder = new StringBuilder("public class ")
                    .append(currentClassModel.getUpperCamelCaseName());
            if (extendedEntityClass != null) {
                String extendedDtoClass = extendedEntityClass.replaceAll("Entity", "Dto");
                classDeclarationBuilder.append(" extends ")
                        .append(extendedDtoClass);
                currentClassModel.setExtendedClass(extendedDtoClass);
                // Here for simplicity we add JS import line for the extended class. (e.g. import { BaseDto } from './base-dto';)
                String extendedJsFileName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, extendedDtoClass);
                currentClassModel.addJsImportLine("import { " + extendedDtoClass + " } from './" + extendedJsFileName + "';");
            }
            classDeclarationBuilder.append(" {");
            fileWriter.write(classDeclarationBuilder.toString());
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            for (ModelProperty property : currentClassModel.getProperties()) {
                fileWriter.write("    private " + property.getJavaDtoType() + " " + property.getLowerCamelCaseName() + ";");
                fileWriter.write(System.lineSeparator());
                fileWriter.write(System.lineSeparator());
            }
            for (ModelProperty property : currentClassModel.getProperties()) {
                writeGetterMethod(fileWriter, property, false);
                writeSetterMethod(fileWriter, property, false);
            }
            fileWriter.write("}");
        }
        catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    private static void createJpaEntityClass(ClassModel currentClassModel) {
        try (FileWriter fileWriter = new FileWriter(jpaEntityDirectoryPath
                + "\\" + currentClassModel.getUpperCamelCaseName() + "Entity.java")) {

            fileWriter.write("package com.ahi.prop_man.entity;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import javax.persistence.*;");
            for (String importLine : currentClassModel.getJavaImportLines()) {
                fileWriter.write(System.lineSeparator());
                fileWriter.write(importLine);
            }
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("@Entity");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("@Table(name = \"" + currentClassModel.getLowerUnderscoreName() + "\")");
            fileWriter.write(System.lineSeparator());
            StringBuilder classDeclarationBuilder = new StringBuilder()
                    .append("public class ")
                    .append(currentClassModel.getUpperCamelCaseName())
                    .append("Entity");
            Optional<ModelProperty> idProperty = currentClassModel.getProperties().stream()
                    .filter(x -> x.getLowerCamelCaseName().equals("id")).findAny();
            if (idProperty.isPresent()) {
                currentClassModel.setExtendedClass("BaseEntity");
                currentClassModel.removeProperty("id");
                Optional<ModelProperty> nameProperty = currentClassModel.getProperties().stream()
                        .filter(x -> x.getLowerCamelCaseName().equals("name")).findAny();
                if (nameProperty.isPresent()) {
                    currentClassModel.setExtendedClass("BaseEntityWithName");
                    currentClassModel.removeProperty("name");
                    Optional<ModelProperty> labelProperty = currentClassModel.getProperties().stream()
                            .filter(x -> x.getLowerCamelCaseName().equals("label")).findAny();
                    if (labelProperty.isPresent()) {
                        currentClassModel.setExtendedClass("BaseEnumEntity");
                        currentClassModel.removeProperty("label");
                    }
                }
                classDeclarationBuilder.append(" extends ").append(currentClassModel.getExtendedClass());
            }
            classDeclarationBuilder.append(" {");
            fileWriter.write(classDeclarationBuilder.toString());
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            for (ModelProperty property : currentClassModel.getProperties()) {
                StringBuilder columnAnnotationBuilder = new StringBuilder("    @");
                if (property.getLowerUnderscoreName().endsWith("_id")) {
                    fileWriter.write("    @ManyToOne(cascade = {CascadeType.PERSIST,\n" +
                            "                          CascadeType.DETACH, CascadeType.REFRESH})");
                    fileWriter.write(System.lineSeparator());
                    columnAnnotationBuilder.append("Join");
                }
                columnAnnotationBuilder.append("Column(name = \"")
                        .append(property.getLowerUnderscoreName())
                        .append("\")");
                fileWriter.write(columnAnnotationBuilder.toString());
                fileWriter.write(System.lineSeparator());
                fileWriter.write("    private " + property.getJavaEntityType() + " " + property.getLowerCamelCaseName() + ";");
                fileWriter.write(System.lineSeparator());
                fileWriter.write(System.lineSeparator());
            }
            for (ModelProperty property : currentClassModel.getProperties()) {
                writeGetterMethod(fileWriter, property, true);
                writeSetterMethod(fileWriter, property, true);
            }
            fileWriter.write("}");
        }
        catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    private static void createMapStructMapperInterface(ClassModel currentClassModel) {
        try (FileWriter fileWriter = new FileWriter(mapStructDirectoryPath
                + "\\" + currentClassModel.getUpperCamelCaseName() + "Mapper.java")) {

            fileWriter.write("package com.ahi.prop_man.mapper;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import com.ahi.prop_man.entity." + currentClassModel.getUpperCamelCaseName() + "Entity;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import com.ahi.prop_man.rest.dto." + currentClassModel.getUpperCamelCaseName() + ";");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import org.mapstruct.Mapper;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import java.util.Collection;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import java.util.List;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("@Mapper(componentModel = \"spring\")");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("public interface " + currentClassModel.getUpperCamelCaseName() + "Mapper {");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("    " + currentClassModel.getUpperCamelCaseName() + " entityToDto(" +
                    currentClassModel.getUpperCamelCaseName() + "Entity entity);");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("    " + currentClassModel.getUpperCamelCaseName() + "Entity dtoToEntity(" +
                    currentClassModel.getUpperCamelCaseName() + " dto);");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("    List<" + currentClassModel.getUpperCamelCaseName() + "> entityToDto(Collection<" +
                    currentClassModel.getUpperCamelCaseName() + "Entity> entities);");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("    List<" + currentClassModel.getUpperCamelCaseName() + "Entity> dtoToEntity(Collection<" +
                    currentClassModel.getUpperCamelCaseName() + "> items);");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("}");
        }
        catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    private static void writeGetterMethod(FileWriter fileWriter, ModelProperty property, boolean isEntityClass) throws IOException {
        String propertyType = isEntityClass ? property.getJavaEntityType() : property.getJavaDtoType();
        String getterLine = "    public " +
                propertyType +
                " get" +
                property.getUpperCamelCaseName() +
                "() {";
        fileWriter.write(getterLine);
        fileWriter.write(System.lineSeparator());
        fileWriter.write("        return " + property.getLowerCamelCaseName() + ";");
        fileWriter.write(System.lineSeparator());
        fileWriter.write("    }");
        fileWriter.write(System.lineSeparator());
        fileWriter.write(System.lineSeparator());
    }

    private static void writeSetterMethod(FileWriter fileWriter, ModelProperty property, boolean isEntityClass) throws IOException {
        String propertyType = isEntityClass ? property.getJavaEntityType() : property.getJavaDtoType();
        String setterLine = "    public void set" +
                property.getUpperCamelCaseName() +
                "(" +
                propertyType +
                " " +
                property.getLowerCamelCaseName() +
                ") {";
        fileWriter.write(setterLine);
        fileWriter.write(System.lineSeparator());
        fileWriter.write("        this." + property.getLowerCamelCaseName() + " = " + property.getLowerCamelCaseName() + ";");
        fileWriter.write(System.lineSeparator());
        fileWriter.write("    }");
        fileWriter.write(System.lineSeparator());
        fileWriter.write(System.lineSeparator());
    }
}
