package sk.tuke.meta.persistence;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.security.KeyPair;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            String idString = field.get(entity).toString();
            long id =Long.parseLong(idString);
            if(id == 0) insert(entity);
            else update(entity,id);
        } catch (NoSuchFieldException e) {
            throw new PersistenceException(e);
        } catch (IllegalAccessException e) {
            throw new PersistenceException(e);
        }

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
            case "long", "int","byte","short": return "INTEGER";
            case "String" : return "TEXT";
            case "float", "double": return "REAL";
            default:
                sqltype = "FOREIGN:" +javaType.getSimpleName()+":INTEGER";
                System.out.println("EROOOR");
                System.out.println("Somehow you managed to turn proper field into foreign key");
            }
        return sqltype;

    }

    public List<String> getSqlStrings(HashMap<String, List<Pair<String,Class<?>>>> classFields){
        List<String> createTableSqlStrings = new ArrayList<String>();
        List<String> foreignKeys = new ArrayList<>();
        for (String className : classFields.keySet()) {
            String createTableSqlString = "CREATE TABLE IF NOT EXISTS ";
            List<Pair<String,Class<?>>> fieldTypes = classFields.get(className);
            createTableSqlString = createTableSqlString + "'"+className+"'" + " (%s)";
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





    private void updateClassId(Object entity,long id){
        try {
            Field idField =  entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity,id);

        } catch (NoSuchFieldException e) {
            throw new PersistenceException(e);
        } catch (IllegalAccessException e) {
            throw new PersistenceException(e);
        }

    }

    private void insert(Object entity){
        String className = entity.getClass().getSimpleName();
        List<Object> actualFieldsObj = new ArrayList<>();
        Field[] declaredFields = entity.getClass().getDeclaredFields();
        ResultSet generatedKeys;
        for(Field field : declaredFields){
            try {
                field.setAccessible(true);
                actualFieldsObj.add(field.get(entity));
            } catch (IllegalAccessException e) {
                throw new PersistenceException(e);
            }
        }
        String insertString = SqlBuilder.buildSqlInsert(className,declaredFields,actualFieldsObj);
        System.out.println(insertString);
        try {
            PreparedStatement statement = connection.prepareStatement(insertString,PreparedStatement.RETURN_GENERATED_KEYS);
            statement.executeUpdate();
            generatedKeys = statement.getGeneratedKeys();
        } catch (SQLException e) {
            throw new PersistenceException(e);
        }

        long id = 0;
        try {
            id = generatedKeys.getLong(1);
        } catch (SQLException e) {
            throw new PersistenceException(e);
        }


        System.out.println(id);

        updateClassId(entity,id);
    }

    private void update(Object entity,long id) throws PersistenceException{
        String name = entity.getClass().getSimpleName();
        String strId = Long.toString(id);
        List<String> declaredFields = getDeclaredFields(entity);
        List<String> actualFields = getActualFields(entity);

        String sqlQuerry = "UPDATE " + name + " SET %s \nWHERE " + name+".id == " + strId;
        String dataString = formatDataUpdate(declaredFields,actualFields);
        sqlQuerry = String.format(sqlQuerry,dataString);

        System.out.println("Printing sql queryy");
        System.out.println(sqlQuerry);
        try {
            PreparedStatement statement = connection.prepareStatement(sqlQuerry);
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new PersistenceException(e);
        }



    }

    private List<String> getDeclaredFields(Object entity){
        List<String> declaredFieldsList = new ArrayList<>();
        Field[] declaredFields = entity.getClass().getDeclaredFields();
        for(Field field : declaredFields){
            declaredFieldsList.add(field.getName());
        }

        return declaredFieldsList;
    }
    private List<String> getActualFields(Object entity){
        List<Object> actualFieldsObj = new ArrayList<>();
        List<String> actualFieldsList = new ArrayList<>();
        Field[] declaredFields = entity.getClass().getDeclaredFields();
        for(Field field : declaredFields){
            try {
                field.setAccessible(true);
                actualFieldsObj.add(field.get(entity));
            } catch (IllegalAccessException e) {
                throw new PersistenceException(e);
            }
        }

        for(Object actualFieldObject : actualFieldsObj){
            try {
                Class<?> aclass = actualFieldObject.getClass();
                Field field = aclass.getDeclaredField("id");
                field.setAccessible(true);
                actualFieldsList.add(field.get(actualFieldObject).toString());

            } catch (NoSuchFieldException e) {
                actualFieldsList.add(actualFieldObject.toString());
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        return actualFieldsList;
    }

    private String formatDataUpdate(List<String> declaredFields, List<String> actualFields){
        if (declaredFields.size()!= actualFields.size()) throw new PersistenceException("Declared fields and actual fields have to match in size");
        String dataString = "";
        for (int i = 0;i<declaredFields.size();i++){
            if (i != declaredFields.size()-1) dataString = dataString+declaredFields.get(i) + " = " +"'" + actualFields.get(i)+ "'" + ",";
            else dataString = dataString+declaredFields.get(i) + " = " +"'" + actualFields.get(i) + "'";
        }

        return dataString;
    }
}

