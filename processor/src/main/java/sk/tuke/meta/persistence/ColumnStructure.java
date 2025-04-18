package sk.tuke.meta.persistence;

public class ColumnStructure {
    private String fieldName;
    private String columnName;

    private String columnType;

    public ColumnStructure(String fieldName, String columnName, String columnType) {
        this.fieldName = fieldName;
        this.columnName = columnName;
        this.columnType = columnType;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getColumnType(){
        return columnType;
    }


    public String getGetterName() {
        return "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    }
}
