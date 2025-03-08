package sk.tuke.meta.persistence;

import java.lang.reflect.Array;
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
    public void createTables(Class<?>... types)  throws PersistenceException {
        HashMap<String,List<Pair<String,Class<?>>>> classFields = getClassFields(types);
        List<String> sqlStrings = getSqlStrings(classFields);
        List<PreparedStatement> statements = new ArrayList<>();
        for(String sqlString : sqlStrings){
            System.out.println(sqlString);
            try {
                statements.add(connection.prepareStatement(sqlString));
            }
            catch (SQLException e) {
                System.out.println("Problem with creating table");
                throw new PersistenceException(e);
            }
        }
        for(PreparedStatement statement :statements){
            try {
                statement.execute();
            } catch (SQLException e) {
                throw new PersistenceException(e);
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
    public void save(Object entity) throws PersistenceException {
        String className = entity.getClass().getSimpleName();
        List<Object> actualFieldsObj = new ArrayList<>();
        Field[] declaredFields = entity.getClass().getDeclaredFields();
        for(Field field : declaredFields){
            try {
                field.setAccessible(true);
                actualFieldsObj.add(field.get(entity));
            } catch (IllegalAccessException e) {
                throw new PersistenceException(e);
            }
        }
        String insertString = buildSqlInsert(className,declaredFields,actualFieldsObj);
        System.out.println(insertString);
       try {
            PreparedStatement statement = connection.prepareStatement(insertString);
            statement.execute();
        } catch (SQLException e) {
            throw new PersistenceException(e);
        }
       long id = 0;

       String selectIdQuery;

       id = getId(entity);

       updateClassId(entity,id);

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
            default:
                sqltype = "FOREIGN:" +javaType.getSimpleName()+":INTEGER";
            }
        return sqltype;

    }

    public List<String> getSqlStrings(HashMap<String, List<Pair<String,Class<?>>>> classFields){
        List<String> createTableSqlStrings = new ArrayList<String>();
        List<String> foreignKeys = new ArrayList<>();
        for (String className : classFields.keySet()) {
            String createTableSqlString = "CREATE TABLE IF NOT EXISTS ";
            List<Pair<String,Class<?>>> fieldTypes = classFields.get(className);
            createTableSqlString = createTableSqlString + className + " (%s)";
            System.out.println(createTableSqlString);
            String collumString = "";

            for (int i = 0; i < fieldTypes.size(); i++) {
                String sqlType = convertJavaTypesToSql(fieldTypes.get(i).getSecond());
                if (sqlType.startsWith("FOREIGN:")) {
                    sqlType = sqlType.substring(8);
                    String name = sqlType.split(":")[0];
                    String type = sqlType.split(":")[1];
                    foreignKeys.add(name);
                    sqlType = type;
                }
                String fieldname = fieldTypes.get(i).getFirst();
                if(i == 0) {collumString = collumString + fieldname + " " + sqlType + " PRIMARY KEY AUTOINCREMENT,\n";}
                else if(i==fieldTypes.size()-1){collumString = collumString + fieldname + " " + sqlType ;}
                else {collumString = collumString + fieldname + " " + sqlType + ",\n";}
            }
            System.out.println(collumString);
            for(String foreignKey : foreignKeys){
                System.out.println("Foreign key is");
                System.out.println(foreignKey);
                collumString = collumString + ",\nFOREIGN KEY (" +foreignKey+") REFERENCES " + foreignKey + "(id)";
            }
            createTableSqlString = String.format(createTableSqlString,collumString);
            createTableSqlStrings.add(createTableSqlString);
        }
        return createTableSqlStrings;
    }

    String buildSqlInsert(String className, Field[] declaredFields, List<Object> actualFields) throws PersistenceException {
        String insertString = "INSERT INTO " + className+"(%s) VALUES (%s)";
        List<Field> declaredFieldsList= new ArrayList<>();

        for(int i = 0;i<actualFields.size();i++){
            declaredFieldsList.add(declaredFields[i]);
        }

        actualFields.remove(0);
        declaredFieldsList.remove(0);

        String declaredFieldsString = buildSqlInsertDeclared(declaredFieldsList);
        String actualFieldsString = buildSqlInsertActual(actualFields);

        insertString = String.format(insertString,declaredFieldsString,actualFieldsString);
        return insertString;

    }

    String buildSqlInsertDeclared(List<Field> fields){
        String collString = "";
        for(int i = 0; i<fields.size();i++){
            collString = collString+fields.get(i).getName();
            if(i != fields.size()-1) collString = collString+", ";
        }
        return collString;
    }

    String buildSqlInsertActual(List<Object> actualFields) throws PersistenceException {
        String actualFieldsString = " ";
        String value;
        int counter = 0;
        for(Object actualField : actualFields){

            System.out.println(actualField.toString());
            try{
                Field field = actualField.getClass().getDeclaredField("id");
                field.setAccessible(true);
                value = field.get(actualField).toString();


            } catch (NoSuchFieldException e) {
                value = actualField.toString();
            } catch (IllegalAccessException e) {
                throw new PersistenceException(e);
            }
            if(counter == actualFields.size()-1) actualFieldsString = actualFieldsString + "'"+value+"'";
            else actualFieldsString = actualFieldsString + "'" + value + "',";
            counter = counter+1;

        }
        return actualFieldsString;
    }



    void getId(Object entity){

    }
    void updateClassId(Object entity,long id){

    }
}

