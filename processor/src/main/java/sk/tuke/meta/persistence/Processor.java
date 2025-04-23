package sk.tuke.meta.persistence;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import sk.tuke.meta.persistence.annotations.Column;
import sk.tuke.meta.persistence.annotations.Id;
import sk.tuke.meta.persistence.annotations.Table;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

@SupportedAnnotationTypes("sk.tuke.meta.persistence.annotations.Table")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class Processor extends AbstractProcessor {

    private static final String TEMPLATE_PATH = "templates";
    private VelocityEngine velocity;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        velocity = new VelocityEngine();
        velocity.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        velocity.setProperty("classpath.resource.loader.class",
                ClasspathResourceLoader.class.getName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> ignored,
                           RoundEnvironment roundEnv) {

        var tableEls  = roundEnv.getElementsAnnotatedWith(Table.class);
        var columnEls = roundEnv.getElementsAnnotatedWith(Column.class);

        var entities  = analyseEntities(tableEls, columnEls);

        for (var e : entities) generateDAO(e);
        if (!entities.isEmpty()) generatePersistenceManager(entities);

        return true;
    }

    private List<EntityStructure> analyseEntities(Set<? extends Element> tableEls,
                                                  Set<? extends Element> columnEls) {

        List<EntityStructure> list = new ArrayList<>();

        for (Element type : tableEls) {
            String className = type.getSimpleName().toString();
            String pkg       = processingEnv.getElementUtils()
                    .getPackageOf(type)
                    .getQualifiedName()
                    .toString();

            String tableName = resolveName(type, Table.class, "name",
                    () -> className);

            List<ColumnStructure> columns = columnsOf(type, columnEls);

            list.add(new EntityStructure(className, pkg, tableName, columns));
        }
        return list;
    }

    private List<ColumnStructure> columnsOf(Element classEl,
                                            Set<? extends Element> columnEls) {

        List<ColumnStructure> cols = new ArrayList<>();

        for (Element field : columnEls) {
            if (!field.getEnclosingElement().equals(classEl)) continue;

            String fieldName  = field.getSimpleName().toString();
            String columnName = resolveName(field, Column.class, "name",
                    () -> fieldName);
            String sqlType    = mapJavaType(field.asType().toString());

            boolean nullable      = true;
            boolean unique        = false;
            boolean primaryKey    = false;
            boolean autoIncrement = false;

            AnnotationMirror colMirror = null;
            for (AnnotationMirror m : field.getAnnotationMirrors()) {
                if (m.getAnnotationType().toString()
                        .equals(Column.class.getCanonicalName())) {
                    colMirror = m;
                    break;
                }
            }
            if (colMirror != null) {
                var vals = processingEnv.getElementUtils()
                        .getElementValuesWithDefaults(colMirror);
                for (var e : vals.entrySet()) {
                    switch (e.getKey().getSimpleName().toString()) {
                        case "nullable" -> nullable =
                                Boolean.parseBoolean(e.getValue().getValue().toString());
                        case "unique"   -> unique =
                                Boolean.parseBoolean(e.getValue().getValue().toString());
                    }
                }
            }

            if (field.getAnnotation(Id.class) != null) {
                primaryKey    = true;
                autoIncrement = true;
                nullable      = false;
            }

            boolean foreignKey    = false;
            String  refTable      = null;
            String  refColumn     = null;

            TypeMirror tm = field.asType();
            if (tm.getKind() == TypeKind.DECLARED) {
                Element refType = ((DeclaredType) tm).asElement();
                if (refType.getAnnotation(Table.class) != null) {
                    foreignKey = true;
                    refTable   = resolveName(refType, Table.class, "name",
                            () -> refType.getSimpleName().toString());
                    refColumn  = primaryKeyColumnName(refType);
                    sqlType    = "INTEGER";
                    nullable   = false;
                }
            }

            cols.add(new ColumnStructure(fieldName, columnName, sqlType,
                    nullable, unique,
                    primaryKey, autoIncrement,
                    foreignKey, refTable, refColumn));
        }

        boolean hasPk = cols.stream().anyMatch(ColumnStructure::isPrimaryKey);
        if (!cols.isEmpty() && !hasPk) {
            ColumnStructure first = cols.get(0);
            first.setPrimaryKey(true);
            first.setAutoIncrement(true);
            first.setNullable(false);
        }
        return cols;
    }

    private String primaryKeyColumnName(Element tableType) {

        Element firstCol = null;

        for (Element e : tableType.getEnclosedElements()) {
            if (e.getKind() != ElementKind.FIELD) continue;
            if (e.getAnnotation(Column.class) == null) continue;

            if (firstCol == null) firstCol = e;

            if (e.getAnnotation(Id.class) != null) {
                return resolveName(e, Column.class, "name",
                        () -> e.getSimpleName().toString());
            }
        }
        if (firstCol != null) {
            Element finalFirstCol = firstCol;
            return resolveName(firstCol, Column.class, "name",
                    () -> finalFirstCol.getSimpleName().toString());
        }
        return "id";
    }

    private String resolveName(Element e,
                               Class<? extends java.lang.annotation.Annotation> ann,
                               String attr,
                               Supplier<String> fallback) {

        String annFqn = ann.getCanonicalName();

        for (AnnotationMirror m : e.getAnnotationMirrors()) {
            if (!m.getAnnotationType().toString().equals(annFqn)) continue;

            var vals = processingEnv.getElementUtils()
                    .getElementValuesWithDefaults(m);
            for (var ev : vals.entrySet()) {
                if (ev.getKey().getSimpleName().contentEquals(attr)) {
                    String v = ev.getValue().getValue().toString();
                    if (!v.isBlank() && !v.equalsIgnoreCase("none")) {
                        return v;
                    }
                }
            }
        }
        return fallback.get();
    }

    private String mapJavaType(String jt) {
        return switch (jt) {
            case "java.lang.String" -> "VARCHAR";
            case "int", "java.lang.Integer",
                    "long", "java.lang.Long",
                    "double", "java.lang.Double" -> "INTEGER";
            case "boolean", "java.lang.Boolean"  -> "BOOLEAN";
            default  -> "TEXT";
        };
    }

    private void generateDAO(EntityStructure entity) {
        try {
            var file = processingEnv.getFiler()
                    .createSourceFile(entity.getFullDaoName());
            try (var out = file.openWriter()) {
                VelocityContext ctx = new VelocityContext();
                ctx.put("entity", entity);
                velocity.getTemplate(TEMPLATE_PATH + "/DAO.java.vm").merge(ctx, out);
            }
        } catch (IOException ex) { throw new RuntimeException(ex); }
    }

    private void generatePersistenceManager(List<EntityStructure> entities) {
        try {
            var file = processingEnv.getFiler()
                    .createSourceFile(
                            "sk.tuke.meta.persistence.GeneratedPersistenceManager");
            try (var out = file.openWriter()) {
                VelocityContext ctx = new VelocityContext();
                ctx.put("entities", entities);
                velocity.getTemplate(
                                TEMPLATE_PATH + "/GeneratedPersistenceManager.java.vm")
                        .merge(ctx, out);
            }
        } catch (IOException ex) { throw new RuntimeException(ex); }
    }
}
