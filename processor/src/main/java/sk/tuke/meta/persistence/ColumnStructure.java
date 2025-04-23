package sk.tuke.meta.persistence;


public class ColumnStructure {

    private final String fieldName;
    private final String columnName;
    private       String columnType;

    private       boolean nullable;
    private final boolean unique;

    private       boolean primaryKey;
    private       boolean autoIncrement;

    private final boolean foreignKey;
    private final String  referencedTable;
    private final String  referencedColumn;

    public ColumnStructure(String fieldName,
                           String columnName,
                           String columnType,
                           boolean nullable,
                           boolean unique,
                           boolean primaryKey,
                           boolean autoIncrement,
                           boolean foreignKey,
                           String  referencedTable,
                           String  referencedColumn) {

        this.fieldName        = fieldName;
        this.columnName       = columnName;
        this.columnType       = columnType;
        this.nullable         = nullable;
        this.unique           = unique;
        this.primaryKey       = primaryKey;
        this.autoIncrement    = autoIncrement;
        this.foreignKey       = foreignKey;
        this.referencedTable  = referencedTable;
        this.referencedColumn = referencedColumn;
    }


    public String  getFieldName()        { return fieldName; }
    public String  getColumnName()       { return columnName; }
    public String  getColumnType()       { return columnType; }
    public boolean isNullable()          { return nullable; }
    public boolean isUnique()            { return unique; }
    public boolean isPrimaryKey()        { return primaryKey; }
    public boolean isAutoIncrement()     { return autoIncrement; }
    public boolean isForeignKey()        { return foreignKey; }
    public String  getReferencedTable()  { return referencedTable; }
    public String  getReferencedColumn() { return referencedColumn; }


    public void setPrimaryKey(boolean v)     { this.primaryKey = v; }
    public void setAutoIncrement(boolean v)  { this.autoIncrement = v; }
    public void setNullable(boolean v)       { this.nullable = v; }
    public void setColumnType(String t)      { this.columnType = t; }

    public String getGetterName() {
        return "get" + Character.toUpperCase(fieldName.charAt(0))
                + fieldName.substring(1);
    }
    public String getSetterName() {
        return "set" + Character.toUpperCase(fieldName.charAt(0))
                + fieldName.substring(1);
    }

}
