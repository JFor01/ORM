package sk.tuke.meta.persistence;

public class ColumnStructure {

    private final String  fieldName;
    private final String  columnName;
    private final String  columnType;

    private final boolean nullable;   //  ➟  `NOT NULL` if false
    private final boolean unique;     //  ➟  `UNIQUE`   if true

    public ColumnStructure(String fieldName,
                           String columnName,
                           String columnType,
                           boolean nullable,
                           boolean unique) {
        this.fieldName  = fieldName;
        this.columnName = columnName;
        this.columnType = columnType;
        this.nullable   = nullable;
        this.unique     = unique;
    }

    /* getters for Velocity */

    public String  getFieldName()  { return fieldName; }
    public String  getColumnName() { return columnName; }
    public String  getColumnType() { return columnType; }
    public boolean isNullable()    { return nullable; }
    public boolean isUnique()      { return unique; }

    public String getGetterName() {
        return "get" + Character.toUpperCase(fieldName.charAt(0))
                + fieldName.substring(1);
    }
}
