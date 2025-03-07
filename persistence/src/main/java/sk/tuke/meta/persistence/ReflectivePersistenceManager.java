package sk.tuke.meta.persistence;

import java.lang.reflect.Field;
import java.security.KeyPair;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public class ReflectivePersistenceManager implements PersistenceManager {
    private final Connection connection;


    public ReflectivePersistenceManager(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void createTables(Class<?>... types)  {
        HashMap<String,List<Pair<String,Class<?>>>> classFields = getClassFields(types);
        List<String> sqlStrings = getSqlStrings(classFields);
        List<PreparedStatement> statements = new ArrayList<>();
        for(String sqlString : sqlStrings){
            System.out.println(sqlString);
            try {
                statements.add(connection.prepareStatement(sqlString));
            }
            catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        for(PreparedStatement statement :statements){
            try {
                statement.execute();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }



    }

    @Override
    public <T> Optional<T> get(Class<T> type, long id) {
        return Optional.empty();
    }

    @Override
    public <T> List<T> getAll(Class<T> type) {
        return Collections.emptyList();
    }

    @Override
    public void save(Object entity) {
    }

    @Override
    public void delete(Object entity) {
    }


    private HashMap<String,List<Pair<String,Class<?>>>> getClassFields(Class<?>... types){
        HashMap<String,List<Pair<String,Class<?>>>> classFields = new HashMap<String,List<Pair<String,Class<?>>>>();
        for (Class<?> type :types){
            List<Pair<String,Class<?>>> fieldDataTypes = new ArrayList<Pair<String,Class<?>>>();
            Field[] fields = type.getDeclaredFields();
            String className = type.getSimpleName();
            for(Field field : fields){
                Class<?> fieldType = field.getType();
                String fieldName = field.getName();
                Pair<String,Class<?>> nameToType = new Pair<>(fieldName,fieldType);
                fieldDataTypes.add(nameToType);
                classFields.put(className,fieldDataTypes);
            }
            classFields.put(className,fieldDataTypes);
        }
        return classFields;

    }
    private String convertJavaTypesToSql(Class<?> javaType)  {
        String sqltype;
        switch (javaType.getSimpleName()) {
            case "long", "int": return "INTEGER";
            case "String" : return "TEXT";
            case "float", "double": return "REAL";
            default:    try {
                javaType.getField("id");
                sqltype = "INT";
            }
            catch(NoSuchFieldException e){
                sqltype = "INT";
            }
        }
        return sqltype;

    }

    public List<String> getSqlStrings(HashMap<String, List<Pair<String,Class<?>>>> classFields){
        List<String> createTableSqlStrings = new ArrayList<String>();
        for (String className : classFields.keySet()) {
            String createTableSqlString = "CREATE TABLE IF NOT EXISTS ";
            List<Pair<String,Class<?>>> fieldTypes = classFields.get(className);
            createTableSqlString = createTableSqlString + className + " (%s)";
            System.out.println(createTableSqlString);
            String collumString = "";
            for (int i = 0; i < fieldTypes.size(); i++) {
                String sqlType = convertJavaTypesToSql(fieldTypes.get(i).getSecond());
                String fieldname = fieldTypes.get(i).getFirst();
                collumString = collumString + fieldname + " " + sqlType + ",\n";
            }
            System.out.println(collumString);
            collumString = collumString + "PRIMARY KEY" + " (" + fieldTypes.get(0).getFirst() + ")\n";
            createTableSqlString = String.format(createTableSqlString,collumString);
            createTableSqlStrings.add(createTableSqlString);
        }
        return createTableSqlStrings;
    }
}

