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
import java.util.function.Supplier;

@SupportedAnnotationTypes("sk.tuke.meta.persistence.annotations.Table")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class Processor extends AbstractProcessor {

    private static final String TEMPLATE_PATH = "templates";
    private VelocityEngine velocity;

    /* ─────────── init ─────────── */

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        velocity = new VelocityEngine();
        velocity.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        velocity.setProperty("classpath.resource.loader.class",
                ClasspathResourceLoader.class.getName());
    }

    /* ───────── process ───────── */

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {

        var tableElements  = roundEnv.getElementsAnnotatedWith(Table.class);
        var columnElements = roundEnv.getElementsAnnotatedWith(Column.class);

        var entities = analyzeEntities(tableElements, columnElements);

        for (var entity : entities) generateDAO(entity);
        if (!entities.isEmpty())    generatePersistenceManager(entities);

        return true; // we handled all supported annotations
    }

    /* ─────── analyze entities ─────── */

    private List<EntityStructure> analyzeEntities(Set<? extends Element> tableEls,
                                                  Set<? extends Element> columnEls) {

        List<EntityStructure> list = new ArrayList<>();

        for (Element type : tableEls) {
            String className  = type.getSimpleName().toString();
            String packageName = processingEnv.getElementUtils()
                    .getPackageOf(type)
                    .getQualifiedName()
                    .toString();

            String tableName = resolveName(type, Table.class, "name",
                    () -> className);  // ⭑ NEW generic helper

            List<ColumnStructure> columns =
                    assignColumnsToEntity(type, columnEls);

            list.add(new EntityStructure(className, packageName, tableName, columns));
        }
        return list;
    }

    /* ─── build ColumnStructure list for one class ─── */

    private List<ColumnStructure> assignColumnsToEntity(Element classElement,
                                                        Set<? extends Element> columnEls) {

        List<ColumnStructure> cols = new ArrayList<>();

        for (Element field : columnEls) {
            if (!field.getEnclosingElement().equals(classElement)) continue;

            String fieldName  = field.getSimpleName().toString();
            String columnName = resolveName(field, Column.class, "name",
                    () -> fieldName);     // ⭑ NEW
            String sqlType    = mapJavaTypeToSQL(field.asType().toString());

            // ⭑ NEW — read nullable / unique with defaults matching @Column
            boolean nullable = true;
            boolean unique   = false;

            for (AnnotationMirror m : field.getAnnotationMirrors()) {
                if (!m.getAnnotationType().toString()
                        .equals(Column.class.getCanonicalName())) continue;

                for (var e : m.getElementValues().entrySet()) {
                    switch (e.getKey().getSimpleName().toString()) {
                        case "nullable" -> nullable = Boolean.parseBoolean(
                                e.getValue().getValue().toString());
                        case "unique"   -> unique   = Boolean.parseBoolean(
                                e.getValue().getValue().toString());
                    }
                }
            }

            cols.add(new ColumnStructure(fieldName, columnName,
                    sqlType, nullable, unique)); // ⭑ NEW ctor
        }
        return cols;
    }

    /* ────────── utilities ────────── */

    /** Generic helper: return attribute value or fallback when absent/blank. */
    private String resolveName(Element element,
                               Class<? extends java.lang.annotation.Annotation> annClass,
                               String attrName,
                               Supplier<String> fallback) {

        String annFqn = annClass.getCanonicalName();

        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (!mirror.getAnnotationType().toString().equals(annFqn)) continue;

            for (var ev : mirror.getElementValues().entrySet()) {
                if (ev.getKey().getSimpleName().contentEquals(attrName)) {
                    String v = ev.getValue().getValue().toString();
                    if (!v.isBlank()) return v;
                }
            }
        }
        return fallback.get();
    }

    private String mapJavaTypeToSQL(String javaType) {
        return switch (javaType) {
            case "java.lang.String"                                 -> "VARCHAR";
            case "int", "java.lang.Integer",
                    "long", "java.lang.Long",
                    "double", "java.lang.Double"                       -> "INTEGER";
            case "boolean", "java.lang.Boolean"                     -> "BOOLEAN";
            default                                                 -> "TEXT";
        };
    }

    /* ─────── code generation (Velocity) ─────── */

    private void generatePersistenceManager(List<EntityStructure> entities) {
        try {
            var file = processingEnv.getFiler()
                    .createSourceFile(
                            "sk.tuke.meta.persistence.GeneratedPersistenceManager");
            try (var out = file.openWriter()) {
                var ctx = new VelocityContext();
                ctx.put("entities", entities);
                velocity.getTemplate(TEMPLATE_PATH + "/GeneratedPersistenceManager.java.vm")
                        .merge(ctx, out);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateDAO(EntityStructure entity) {
        try {
            var file = processingEnv.getFiler()
                    .createSourceFile(entity.getFullDaoName());
            try (var out = file.openWriter()) {
                var ctx = new VelocityContext();
                ctx.put("entity", entity);
                velocity.getTemplate(TEMPLATE_PATH + "/DAO.java.vm")
                        .merge(ctx, out);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
