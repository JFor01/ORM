package sk.tuke.meta.persistence;

import java.util.ArrayList;
import java.util.List;

public class EntityStructure {
    private final String name;
    private final String packageName;

    private final String tableName;

    private final List<ColumnStructure> columnElements;

    public EntityStructure(String name, String packageName,String tableName, List<ColumnStructure> columnElements) {
        this.name = name;
        this.packageName = packageName;
        this.tableName = tableName;
        this.columnElements = columnElements;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getName() {
        return name;
    }

    public String getDaoName() {
        return name + "DAO";
    }

    public String getTableName(){
        return tableName;
    }


    public ColumnStructure getIdColumn() {
        return new ColumnStructure("id", "id", "INTEGER",true,true,true,true,false,"","","","java.lang.Long");
    }

    public String getFullName() {
        return packageName + "." + name;
    }

    public String getFullDaoName() {
        return packageName + "." + getDaoName();
    }

    public List<String> getColumnNames() {
        List<String> columnNames = new ArrayList<>();
        for (ColumnStructure columnElement : columnElements) {
            columnNames.add(columnElement.getColumnName());
        }
        return columnNames;
    }

    public List<ColumnStructure> getColumns() {
        return columnElements;
    }
}
