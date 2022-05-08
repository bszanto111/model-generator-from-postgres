package com.ahi.model_generator_from_postgres;

import com.google.common.base.CaseFormat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final Pattern createTablePattern = Pattern.compile("CREATE TABLE (public.)?(\\w+)");
    private static final Pattern columnPattern = Pattern.compile("(\\w+)\\s(\\w+).*,");
    private static String sqlFilePath;
    private static Properties properties = new Properties();

    public static void main(String[] args) {
        if (args.length < 5) {
            System.out.println("Parameter needed:");
            System.out.println("<SQL file path>");
        }
        sqlFilePath = args[0];
        String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
        String configPath = rootPath + "config.properties";
        try {
            properties.load(new FileInputStream(configPath));
        } catch (IOException e) {
            e.printStackTrace();
        }
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
                    String tableName = createTableMatcher.group(2);
                    String dtoClassName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, tableName);
                    currentClassModel = new ClassModel(dtoClassName, tableName);
                }
                else if (currentlyProcessingATable) {
                    Matcher columnPatternMatcher = columnPattern.matcher(line);
                    if (line.contains("CONSTRAINT")) {
                        // Write the table's classes
                        createJpaEntityClass(currentClassModel);
                        createJavaDtoClass(currentClassModel);
                        createJpaRepositoryInterface(currentClassModel);
                        createMapStructMapperInterface(currentClassModel);
                        createSpringServiceClass(currentClassModel);
                        createRestControllerClass(currentClassModel);
                        createTypeScriptClass(currentClassModel);
                        createAngularServiceClass(currentClassModel);
                        createNgRxAction(currentClassModel);
                        createNgRxStateMemberHandlerAndSelect(currentClassModel);
                        createNgRxEffect(currentClassModel);
                        // Skip each remaining line of the table description after CONSTRAINT keyword is found.
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

    private static void createJpaEntityClass(ClassModel currentClassModel) {
        try (FileWriter fileWriter = new FileWriter(properties.getProperty("backendRootPath")
                + "\\entity\\" + currentClassModel.getUpperCamelCaseName() + "Entity.java")) {

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

    private static void createJavaDtoClass(ClassModel currentClassModel) {
        try (FileWriter fileWriter = new FileWriter(properties.getProperty("backendRootPath")
                + "\\rest\\dto\\" + currentClassModel.getUpperCamelCaseName() + ".java")) {

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
            }
            fileWriter.write(System.lineSeparator());
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

    private static void createJpaRepositoryInterface(ClassModel currentClassModel) {
        try (FileWriter fileWriter = new FileWriter(properties.getProperty("backendRootPath")
                + "\\repository\\" + currentClassModel.getUpperCamelCaseName() + "Repository.java")) {

            fileWriter.write("package com.ahi.prop_man.repository;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import com.ahi.prop_man.entity." + currentClassModel.getUpperCamelCaseName() + "Entity;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import org.springframework.data.jpa.repository.JpaRepository;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import java.util.List;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            String primaryKeyType;
            if (currentClassModel.getExtendedClass() == null) { // If it does not have an int Id PK field, then the first property should be the PK.
                primaryKeyType = currentClassModel.getProperties().get(0).getJavaEntityType();
            }
            else {
                primaryKeyType = "Integer";
            }
            fileWriter.write("public interface "
                    + currentClassModel.getUpperCamelCaseName()
                    + "Repository extends JpaRepository<"
                    + currentClassModel.getUpperCamelCaseName()
                    + "Entity, "
                    + primaryKeyType
                    + "> {");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("    List<" + currentClassModel.getUpperCamelCaseName() + "Entity> findAll();");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("}");
        }
        catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    private static void createMapStructMapperInterface(ClassModel currentClassModel) {
        try (FileWriter fileWriter = new FileWriter(properties.getProperty("backendRootPath")
                + "\\mapper\\" + currentClassModel.getUpperCamelCaseName() + "Mapper.java")) {

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

    private static void createSpringServiceClass(ClassModel currentClassModel) {
        try (FileWriter fileWriter = new FileWriter(properties.getProperty("backendRootPath")
                + "\\rest\\service\\" + currentClassModel.getUpperCamelCaseName() + "Service.java")) {

            fileWriter.write("package com.ahi.prop_man.rest.service;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import com.ahi.prop_man.entity." + currentClassModel.getUpperCamelCaseName() + "Entity;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import com.ahi.prop_man.mapper." + currentClassModel.getUpperCamelCaseName() + "Mapper;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import com.ahi.prop_man.repository." + currentClassModel.getUpperCamelCaseName() + "Repository;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import com.ahi.prop_man.rest.dto." + currentClassModel.getUpperCamelCaseName() + ";");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import org.springframework.beans.factory.annotation.Autowired;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import org.springframework.stereotype.Service;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import java.util.List;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("@Service");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("public class "
                    + currentClassModel.getUpperCamelCaseName()
                    + "Service {");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("    @Autowired");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("    private "
                    + currentClassModel.getUpperCamelCaseName()
                    + "Mapper "
                    + currentClassModel.getLowerCamelCaseName()
                    + "Mapper;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("    @Autowired");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("    private "
                    + currentClassModel.getUpperCamelCaseName()
                    + "Repository "
                    + currentClassModel.getLowerCamelCaseName()
                    + "Repository;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("    public List<" + currentClassModel.getUpperCamelCaseName() + "> getAll() {");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("        List<"
                    + currentClassModel.getUpperCamelCaseName()
                    + "Entity> entities = "
                    + currentClassModel.getLowerCamelCaseName()
                    + "Repository.findAll();");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("        return "
                    + currentClassModel.getLowerCamelCaseName()
                    + "Mapper.entityToDto(entities);");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("    }");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("}");
        }
        catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    private static void createRestControllerClass(ClassModel currentClassModel) {
        try (FileWriter fileWriter = new FileWriter(properties.getProperty("backendRootPath")
                + "\\rest\\controller\\" + currentClassModel.getUpperCamelCaseName() + "Controller.java")) {

            fileWriter.write("package com.ahi.prop_man.rest.controller;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import com.ahi.prop_man.rest.dto." + currentClassModel.getUpperCamelCaseName() + ";");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import com.ahi.prop_man.rest.service." + currentClassModel.getUpperCamelCaseName() + "Service;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import org.springframework.beans.factory.annotation.Autowired;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import org.springframework.web.bind.annotation.GetMapping;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import org.springframework.web.bind.annotation.RequestMapping;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import org.springframework.web.bind.annotation.RestController;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import java.util.List;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("@RestController");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("@RequestMapping(\"/api/"
                    + currentClassModel.getLowerHyphenName()
                    + "s\")");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("public class "
                    + currentClassModel.getUpperCamelCaseName()
                    + "Controller {");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("    @Autowired");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("    private "
                    + currentClassModel.getUpperCamelCaseName()
                    + "Service "
                    + currentClassModel.getLowerCamelCaseName()
                    + "Service;");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("    @GetMapping");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("    public List<"
                    + currentClassModel.getUpperCamelCaseName()
                    + "> getAll() {");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("        return "
                    + currentClassModel.getLowerCamelCaseName()
                    + "Service.getAll();");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("    }");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("}");
        }
        catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    private static void createTypeScriptClass(ClassModel currentClassModel) {
        try (FileWriter fileWriter = new FileWriter(properties.getProperty("frontendRootPath")
                + "\\models\\" + currentClassModel.getLowerHyphenName() + ".ts")) {

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

    private static void createAngularServiceClass(ClassModel currentClassModel) {
        try (FileWriter fileWriter = new FileWriter(properties.getProperty("frontendRootPath")
                + "\\services\\" + currentClassModel.getLowerHyphenName() + ".service.ts")) {

            fileWriter.write("import { HttpClient } from '@angular/common/http';");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import { Injectable } from '@angular/core';");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import { Observable } from 'rxjs';");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import { " + currentClassModel.getUpperCamelCaseName() + " } from '../models/" + currentClassModel.getLowerHyphenName() + "';");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("import propManConfig from '../../config/prop-man-config';");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("@Injectable({");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("  providedIn: 'root'");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("})");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("export class " + currentClassModel.getUpperCamelCaseName() + "Service {");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("  private readonly baseUrl: string = propManConfig.serverDomain + '/api/" + currentClassModel.getLowerHyphenName() + "s';");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("  constructor(private http: HttpClient) { }");
            fileWriter.write(System.lineSeparator());
            fileWriter.write(System.lineSeparator());
            fileWriter.write("  getAll(): Observable<" + currentClassModel.getUpperCamelCaseName() + "[]> {");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("    return this.http.get<" + currentClassModel.getUpperCamelCaseName() + "[]>(this.baseUrl);");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("  }");
            fileWriter.write(System.lineSeparator());
            fileWriter.write("}");
        }
        catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    private static void createNgRxAction(ClassModel currentClassModel) {
        Path filePath = Path.of("C:\\Work\\prop-man\\prop-man-fe\\src\\app\\modules\\room-rack\\store\\room-rack.action.ts");
        try {
            List<String> fileContent = new ArrayList<>(Files.readAllLines(filePath, StandardCharsets.UTF_8));
            fileContent.add(0, "import { "
                    + currentClassModel.getUpperCamelCaseName()
                    + " } from 'src/app/models/"
                    + currentClassModel.getLowerHyphenName()
                    + "';");
            fileContent.add("");
            fileContent.add("export const load"
                    + currentClassModel.getUpperCamelCaseNameInPlural()
                    + " = createAction('[Room Rack] Load "
                    + currentClassModel.getUpperCamelCaseNameInPlural()
                    + "');");
            fileContent.add("");
            fileContent.add("export const "
                    + currentClassModel.getLowerCamelCaseNameInPlural()
                    + "LoadedSuccess = createAction(");
            fileContent.add("");
            fileContent.add("    '[Room Rack] "
                    + currentClassModel.getUpperCamelCaseNameInPlural()
                    + " Loaded Success',");
            fileContent.add("    props<{"
                    + currentClassModel.getLowerCamelCaseNameInPlural()
                    + ": "
                    + currentClassModel.getUpperCamelCaseName()
                    + "[]}>()");
            fileContent.add(");");
            Files.write(filePath, fileContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createNgRxStateMemberHandlerAndSelect(ClassModel currentClassModel) {
        Path filePath = Path.of("C:\\Work\\prop-man\\prop-man-fe\\src\\app\\modules\\room-rack\\store\\room-rack.reducer.ts");
        try {
            List<String> fileContent = new ArrayList<>(Files.readAllLines(filePath, StandardCharsets.UTF_8));
            fileContent.add(0, "import { "
                    + currentClassModel.getUpperCamelCaseName()
                    + " } from 'src/app/models/"
                    + currentClassModel.getLowerHyphenName()
                    + "';");
            int startIndexOfStateInterface = fileContent.indexOf("export interface State {") + 1;
            fileContent.add(startIndexOfStateInterface, "    "
                    + currentClassModel.getLowerCamelCaseNameInPlural()
                    + ": "
                    + currentClassModel.getUpperCamelCaseName()
                    + "[];");
            int startIndexOfStateInitialization = fileContent.indexOf("export const initialState: State = {") + 1;
            fileContent.add(startIndexOfStateInitialization, "    "
                    + currentClassModel.getLowerCamelCaseNameInPlural()
                    + ": [],");
            int startIndexOfReducerHandler = fileContent.indexOf("    initialState,") + 1;
            fileContent.add(startIndexOfReducerHandler++, "    on(RoomRackActions."
                    + currentClassModel.getLowerCamelCaseNameInPlural()
                    + "LoadedSuccess, (state, {"
                    + currentClassModel.getLowerCamelCaseNameInPlural()
                    + "}) => ({");
            fileContent.add(startIndexOfReducerHandler++, "        ...state,");
            fileContent.add(startIndexOfReducerHandler++, "        "
                    + currentClassModel.getLowerCamelCaseNameInPlural()
                    + ": "
                    + currentClassModel.getLowerCamelCaseNameInPlural());
            fileContent.add(startIndexOfReducerHandler, "    })),");
            fileContent.add("export const select"
                    + currentClassModel.getUpperCamelCaseNameInPlural()
                    + " =");
            fileContent.add("    (state: AppState) => state.roomRack."
                    + currentClassModel.getLowerCamelCaseNameInPlural()
                    + ";");
            Files.write(filePath, fileContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createNgRxEffect(ClassModel currentClassModel) {
        Path filePath = Path.of("C:\\Work\\prop-man\\prop-man-fe\\src\\app\\modules\\room-rack\\store\\room-rack.effects.ts");
        try {
            List<String> fileContent = new ArrayList<>(Files.readAllLines(filePath, StandardCharsets.UTF_8));
            fileContent.add(0, "import { "
                    + currentClassModel.getUpperCamelCaseName()
                    + "Service } from 'src/app/services/"
                    + currentClassModel.getLowerHyphenName()
                    + ".service';");
            int indexOfLastLineOfConstructor = fileContent.indexOf("  ) {}");
            fileContent.add(indexOfLastLineOfConstructor - 2, "    private "
                    + currentClassModel.getLowerCamelCaseName()
                    + "Service: "
                    + currentClassModel.getUpperCamelCaseName()
                    + "Service,");
            if (currentClassModel.getExtendedClass() != null && currentClassModel.getExtendedClass().equals("BaseEnumDto")) {
                createNgRxEffectForEnum(fileContent, indexOfLastLineOfConstructor + 2, currentClassModel);
            }
            else {
                createNgRxEffectForNonEnum(fileContent, indexOfLastLineOfConstructor + 2, currentClassModel);
            }
            Files.write(filePath, fileContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createNgRxEffectForEnum(List<String> lines, int startIndex, ClassModel currentClassModel) {

        lines.add(startIndex++, "");
        lines.add(startIndex++, "  load"
                + currentClassModel.getUpperCamelCaseNameInPlural()
                + "$ = createEffect(() => this.actions$.pipe(");
        lines.add(startIndex++, "    ofType(RoomRackActions.load"
                + currentClassModel.getUpperCamelCaseNameInPlural()
                + "),");
        lines.add(startIndex++, "    concatMap(action => of(action).pipe(");
        lines.add(startIndex++, "      withLatestFrom(this.store.pipe(select(fromRoomRack.select"
                + currentClassModel.getUpperCamelCaseNameInPlural()
                + ")))");
        lines.add(startIndex++, "    )),");
        lines.add(startIndex++, "    switchMap(([action, "
                + currentClassModel.getLowerCamelCaseNameInPlural()
                + "]) =>");
        lines.add(startIndex++, "      (!"
                + currentClassModel.getLowerCamelCaseNameInPlural()
                + " || "
                + currentClassModel.getLowerCamelCaseNameInPlural()
                + ".length === 0");
        lines.add(startIndex++, "        ? this."
                + currentClassModel.getLowerCamelCaseName()
                + "Service.getAll()");
        lines.add(startIndex++, "        : of("
                + currentClassModel.getLowerCamelCaseNameInPlural()
                + "))");
        lines.add(startIndex++, "      .pipe(");
        lines.add(startIndex++, "        map("
                + currentClassModel.getLowerCamelCaseNameInPlural()
                + " => (RoomRackActions."
                + currentClassModel.getLowerCamelCaseNameInPlural()
                + "LoadedSuccess({"
                + currentClassModel.getLowerCamelCaseNameInPlural()
                + "}))),");
        lines.add(startIndex++, "        catchError(val => this.errorHandlingService.handleError(val))");
        lines.add(startIndex++, "    ))");
        lines.add(startIndex, "  ));");
    }

    private static void createNgRxEffectForNonEnum(List<String> lines, int startIndex, ClassModel currentClassModel) {

        lines.add(startIndex++, "");
        lines.add(startIndex++, "  load"
                + currentClassModel.getUpperCamelCaseNameInPlural()
                + "$ = createEffect(() => this.actions$.pipe(");
        lines.add(startIndex++, "    ofType(RoomRackActions.load"
                + currentClassModel.getUpperCamelCaseNameInPlural()
                + "),");
        lines.add(startIndex++, "    switchMap(() => this."
                + currentClassModel.getLowerCamelCaseName()
                + "Service.getAll()");
        lines.add(startIndex++, "      .pipe(");
        lines.add(startIndex++, "        map("
                + currentClassModel.getLowerCamelCaseNameInPlural()
                + " => (RoomRackActions."
                + currentClassModel.getLowerCamelCaseNameInPlural()
                + "LoadedSuccess({"
                + currentClassModel.getLowerCamelCaseNameInPlural()
                + "}))),");
        lines.add(startIndex++, "        catchError(val => this.errorHandlingService.handleError(val))");
        lines.add(startIndex++, "    ))");
        lines.add(startIndex, "  ));");
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
