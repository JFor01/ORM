package sk.tuke.meta.persistence;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import sk.tuke.meta.persistence.annotations.Column;
import sk.tuke.meta.persistence.annotations.Table;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.*;

@SupportedAnnotationTypes("sk.tuke.meta.persistence.annotations.Table")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class Processor extends AbstractProcessor {
    private static final String TEMPLATE_PATH = "templates";

    private VelocityEngine velocity;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        velocity = new VelocityEngine();
        velocity.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        velocity.setProperty("classpath.resource.loader.class",
                ClasspathResourceLoader.class.getName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        var tableElements = roundEnvironment.getElementsAnnotatedWith(Table.class);
        var columnElements = roundEnvironment.getElementsAnnotatedWith(Column.class);
        var entities = analyzeEntities(tableElements,columnElements);
        for (var entity : entities) {
            generateDAO(entity);
        }
        if (!tableElements.isEmpty()) {
            generatePersistenceManager(entities);
        }

        return true;
    }

    private List<EntityStructure> analyzeEntities(Set<? extends Element> tableElements, Set<? extends Element> columnElements) {

        List<EntityStructure> entities = new ArrayList<>();

        for (Element element : tableElements) {
            String className  = element.getSimpleName().toString();
            String packageName =
                    processingEnv.getElementUtils()
                            .getPackageOf(element)
                            .getQualifiedName()
                            .toString();

            String tableName  = resolveTableName(element);
            List<ColumnStructure> classColumns =
                    assignColumnsToEntity(element, columnElements);

            entities.add(new EntityStructure(
                    className,
                    packageName,
                    tableName,
                    classColumns));
        }
        return entities;
    }



    private List<ColumnStructure> assignColumnsToEntity(Element classElement, Set<? extends Element> columnElements) {
        List<ColumnStructure> classColumns = new ArrayList<>();

        for (Element columnElement : columnElements) {
            if (columnElement.getEnclosingElement().equals(classElement)) {
                String fieldName = columnElement.getSimpleName().toString();
                String columnName = fieldName;
                String sqlType = mapJavaTypeToSQL(columnElement.asType().toString());

                for (AnnotationMirror mirror : columnElement.getAnnotationMirrors()) {
                    if (mirror.getAnnotationType().toString().equals(Column.class.getCanonicalName())) {
                        for (var entry : mirror.getElementValues().entrySet()) {
                            if (entry.getKey().getSimpleName().toString().equals("name")) {
                                columnName = entry.getValue().getValue().toString();
                            }
                        }
                    }
                }

                classColumns.add(new ColumnStructure(fieldName, columnName, sqlType));
            }
        }

        return classColumns;
    }

    private String mapJavaTypeToSQL(String javaType) {
        return switch (javaType) {
            case "java.lang.String" -> "VARCHAR";
            case "int", "java.lang.Integer", "double", "java.lang.Double", "long", "java.lang.Long" -> "INTEGER";
            case "boolean", "java.lang.Boolean" -> "BOOLEAN";
            default -> "TEXT";
        };
    }


    private void generatePersistenceManager(List<EntityStructure> entities) {
        try {
            var javaFile = processingEnv.getFiler().createSourceFile(
                    "sk.tuke.meta.persistence.GeneratedPersistenceManager");
            try (var writer = javaFile.openWriter()) {
                var template = velocity.getTemplate(
                        TEMPLATE_PATH + "/GeneratedPersistenceManager.java.vm");
                VelocityContext context = new VelocityContext();
                context.put("entities", entities);
                template.merge(context, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateDAO(EntityStructure entity) {
        try {
            var javaFile = processingEnv.getFiler().createSourceFile(
                    entity.getFullDaoName());
            try (var writer = javaFile.openWriter()) {
                var template = velocity.getTemplate(TEMPLATE_PATH + "/DAO.java.vm");
                VelocityContext context = new VelocityContext();
                context.put("entity", entity);

                template.merge(context, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String resolveTableName(Element type) {

        for (AnnotationMirror mirror : type.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString()
                    .equals(sk.tuke.meta.persistence.annotations.Table.class.getCanonicalName())) {
                for (var entry : mirror.getElementValues().entrySet()) {
                    if (entry.getKey().getSimpleName().contentEquals("name")) {
                        String explicit = entry.getValue().getValue().toString();
                        if (!explicit.isBlank()) {
                            return explicit;
                        }
                    }
                }
            }
        }
        return type.getSimpleName().toString();
    }

}
