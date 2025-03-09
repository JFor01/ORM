package sk.tuke.meta.persistence;

import javax.swing.text.Position;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.security.KeyPair;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.Optional;

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
        String className = type.getSimpleName();
        String selectQuery = "SELECT * FROM " + "'" + className + "' " + " WHERE ID = (?)";
        Field[] fields = type.getDeclaredFields();
        List<String> types = getTypes(fields);

        for(String fieldType :types){
            System.out.println(fieldType);
        }

        ResultSet resultSet = executeGetQuery(selectQuery,id);

        T classInstance;
        try {
            classInstance = type.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            throw new PersistenceException(e);
        }
        try {
            if(!resultSet.next())return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }


        return insertFieldsIntoInstance(resultSet,types,classInstance);
    }

    @Override
    public <T> List<T> getAll(Class<T> type) {
        String className = type.getSimpleName();
        String selectQuery = "SELECT * FROM " + "'" + className + "' ";
        Field[] fields = type.getDeclaredFields();
        List<String> types = getTypes(fields);

        for(String fieldType :types){
            System.out.println(fieldType);
        }

        ResultSet resultSet = null;
        try {
            resultSet = connection.prepareStatement(selectQuery).executeQuery();
        } catch (SQLException e) {
            throw new PersistenceException(e);
        }

        List<T> classInstances = new ArrayList<>();
        while (true) {
            try {
                if (!resultSet.next()) break;
            } catch (SQLException e) {
                throw new PersistenceException(e);
            }
            try {
                T classInstance = type.getDeclaredConstructor().newInstance();
                Optional<T> optClassInstance = insertFieldsIntoInstance(resultSet,types,classInstance);
                classInstances.add(optClassInstance.get());
            } catch (InstantiationException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
                throw new PersistenceException(e);
            }
        }




        System.out.println("Printing all class instances");
        for(T classInstance :classInstances){
            System.out.println(classInstance);
        }
        return classInstances;
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
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new PersistenceException(e);
        }

    }

    @Override
    public void delete(Object entity) {
        String name = entity.getClass().getSimpleName();
        long id;
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
             id = (long)field.get(entity);
        } catch (NoSuchFieldException e) {
            throw new PersistenceException(e);
        } catch (IllegalAccessException e) {
            throw new PersistenceException(e);
        }
        if(id==0)throw new PersistenceException("You can only delete savde objects");
        String idString = Long.toString(id);
        String sqlStatement = "DELETE FROM " + "'" + name + "'" + " WHERE id=" +idString;
        try {
            connection.prepareStatement(sqlStatement).executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
            }
        return sqltype;

    }

    public List<String> getSqlStrings(HashMap<String, List<Pair<String,Class<?>>>> classFields){
        List<String> createTableSqlStrings = new ArrayList<String>();
        for (String className : classFields.keySet()) {
            List<String> foreignKeys = new ArrayList<>();
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
                if(i == 0) {collumString = collumString + "'"+ fieldname + "' " + sqlType + " PRIMARY KEY AUTOINCREMENT,\n";}
                else if(i==fieldTypes.size()-1){collumString = collumString + "'"+ fieldname + "' " + sqlType ;}
                else {collumString = collumString +  "'"+ fieldname + "' " + sqlType + ",\n";}
            }
            System.out.println(collumString);
            for(String foreignKey : foreignKeys){
                System.out.println("Foreign key is");
                System.out.println(foreignKey);
                collumString = collumString + ",\nFOREIGN KEY (" +foreignKey+") REFERENCES " + foreignKey + "(id)";
            }
            createTableSqlString = String.format(createTableSqlString,collumString);
            System.out.println(createTableSqlString);
            createTableSqlStrings.add(createTableSqlString);
        }
        return createTableSqlStrings;
    }





    private void updateClassId(Object entity,long id){
        try {
            Field idField =  entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity,id);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new PersistenceException(e);
        }

    }

    private void insert(Object entity){
        String className = entity.getClass().getSimpleName();
        String insertString = "INSERT INTO " + "'" + className+ "'"+ "(%s) VALUES (%s)";
        Optional<ResultSet> generatedKeys;
        List<String> declaredFields = getDeclaredFields(entity);
        List<FieldTypeAndValue> actualFields= getActualFields(entity);

        //Getting rid of the unnecessary id
        declaredFields.remove(0);
        actualFields.remove(0);

        String dataString = formatInsertString(declaredFields);
        String dataString2 = formatInsertStringTokens(declaredFields.size());
        insertString = String.format(insertString,dataString,dataString2);
        System.out.println(dataString);
        System.out.println(dataString2);
        System.out.println(insertString);

        generatedKeys = executeQuery(insertString,actualFields,PreparedStatement.RETURN_GENERATED_KEYS);

        long id = 0;
        try {
            if(generatedKeys.isPresent())id = generatedKeys.get().getLong(1);
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
        List<FieldTypeAndValue> actualFields = getActualFields(entity);

        String sqlQuerry = "UPDATE " + "'" + name + "'" + " SET %s \nWHERE " + "'" + name + "'"+".id == " + strId;
        String dataString = formatDataUpdate(declaredFields);
        sqlQuerry = String.format(sqlQuerry,dataString);

        System.out.println("Printing sql queryy");

        System.out.println(sqlQuerry);

        executeQuery(sqlQuerry,actualFields,PreparedStatement.NO_GENERATED_KEYS);



    }

    private List<String> getDeclaredFields(Object entity){
        List<String> declaredFieldsList = new ArrayList<>();
        Field[] declaredFields = entity.getClass().getDeclaredFields();
        for(Field field : declaredFields){
            declaredFieldsList.add(field.getName());
        }

        return declaredFieldsList;
    }
    private List<FieldTypeAndValue> getActualFields(Object entity){
        List<Object> actualFieldsObj = new ArrayList<>();
        List<FieldTypeAndValue> actualFieldsList = new ArrayList<>();
        Field[] declaredFields = entity.getClass().getDeclaredFields();
        for(Field field : declaredFields){
            try {
                field.setAccessible(true);
                actualFieldsObj.add(field.get(entity));
            } catch (IllegalAccessException e) {
                throw new PersistenceException(e);
            }
        }
        int index = 0;
        System.out.println("Printing actual fields");
        for(Object actualFieldObject : actualFieldsObj){
            FieldTypeAndValue fieldTypeAndValue = new FieldTypeAndValue();
            try {
                Class<?> aclass = actualFieldObject.getClass();
                Field field = aclass.getDeclaredField("id");
                field.setAccessible(true);
                String value = field.get(actualFieldObject).toString();
                if(Integer.parseInt(value) == 0) throw new PersistenceException("You have to save reference to the table first");
                fieldTypeAndValue.setValue(value);
                fieldTypeAndValue.setType("java.lang.Integer");
                actualFieldsList.add(fieldTypeAndValue);

            } catch (NoSuchFieldException e) {
                fieldTypeAndValue.setValue(actualFieldObject.toString());
                fieldTypeAndValue.setType(actualFieldObject.getClass().getTypeName());
                actualFieldsList.add(fieldTypeAndValue);
            } catch (IllegalAccessException e) {
                throw new PersistenceException(e);
            }
            System.out.println(actualFieldsList.get(index).getType());
            System.out.println(actualFieldsList.get(index).getValue());
            index++;
        }

        return actualFieldsList;
    }

    private String formatDataUpdate(List<String> declaredFields){
        String dataString = "";
        for (int i = 0;i<declaredFields.size();i++){
            if (i != declaredFields.size()-1) dataString =   dataString+ "'" +declaredFields.get(i) + "'"  + "=?,";
            else dataString = dataString+"'" + declaredFields.get(i) + "'" + " =?";
        }

        return dataString;
    }
    private String formatInsertString(List<String> declaredFields){
        String dataString = "";
        for (int i = 0;i<declaredFields.size();i++){
            if (i != declaredFields.size()-1) dataString = dataString+"'" + declaredFields.get(i) + "'" + ",";
            else dataString = dataString+ "'" + declaredFields.get(i) + "'" ;
        }
        return dataString;
    }

    private String formatInsertStringTokens(int size){
        String dataString = "";
        for (int i = 0;i<size;i++){
            if (i != size-1) dataString = dataString+ "?,";
            else dataString = dataString+ "?";
        }
        return dataString;
    }

    private Optional<ResultSet> executeQuery(String sqlQuerry,List<FieldTypeAndValue> actualFields,int returnGeneratedKeys){
        Optional<ResultSet> resultSetOptional;
        try {
            PreparedStatement statement = connection.prepareStatement(sqlQuerry,returnGeneratedKeys);
            int index = 1;
            for(FieldTypeAndValue actualField : actualFields){
                String type = actualField.getType();
                switch (type){
                    case "java.lang.Integer" : statement.setInt(index,Integer.parseInt(actualField.getValue()));
                        break;
                    case "java.lang.String" : statement.setString(index,actualField.getValue());
                        break;
                    case "java.lang.Long" : statement.setLong(index,Long.parseLong(actualField.getValue()));
                        break;
                    case "java.lang.Float" : statement.setFloat(index,Float.parseFloat(actualField.getValue()));
                        break;
                    case "java.lang.Double" : statement.setDouble(index,Double.parseDouble(actualField.getValue()));
                }
                index++;
            }
            try {
                statement.executeUpdate();
                resultSetOptional = Optional.of(statement.getGeneratedKeys());
            }catch (SQLException e ){
                resultSetOptional = Optional.empty();
            }

        } catch (SQLException e) {
            throw new PersistenceException(e);
        }
        return resultSetOptional;
    }

    private List<String>getTypes(Field[] fields){
        List<String> types = new ArrayList<>();
        for(Field field : fields){
            types.add(field.getType().getSimpleName());
        }

        return types;
    }


    private ResultSet executeGetQuery(String selectQuery, long id){
        ResultSet resultSet;
        try {
            PreparedStatement statement = connection.prepareStatement(selectQuery);
            statement.setLong(1,id);
            resultSet = statement.executeQuery();
        } catch (SQLException e) {
            throw new PersistenceException(e);
        }

        return resultSet;
    }

    private <T> Optional<T> insertFieldsIntoInstance(ResultSet resultSet, List<String> types,T classInstance ){

        Class<?> clasReflexion = classInstance.getClass();
        List<Integer> positions;
        positions = getReferencePosition(clasReflexion);

            try{
                int index = 1;
                for (String type : types) {
                    if(checkIndexAgainstPositions(positions,index-1)){
                        insertNewReferenceIntoInstance(classInstance,index-1,resultSet.getInt(index));
                        index++;
                        continue;
                    }

                    switch (type) {
                        case "long": insertValueIntoInstance(resultSet.getLong(index),classInstance,index-1);
                        break;
                        case "String" : insertValueIntoInstance(resultSet.getString(index),classInstance,index-1);

                        break;
                        case "int" : insertValueIntoInstance(resultSet.getInt(index),classInstance,index-1);
                        break;
                        case "float" : insertValueIntoInstance(resultSet.getFloat(index),classInstance,index-1);
                        break;
                        case "double" : insertValueIntoInstance(resultSet.getDouble(index),classInstance,index-1);
                        default: throw new PersistenceException("Type not supported");
                    }
                    index++;
                }
            } catch (SQLException e) {
                throw new PersistenceException(e);
            }

        return Optional.of(classInstance);
    }

    private <T,F> void insertValueIntoInstance(F value, T classInstance,int fieldIndex) {
        Field[] fields = classInstance.getClass().getDeclaredFields();
        Field field = fields[fieldIndex];
        field.setAccessible(true);
        try {
            field.set(classInstance,value);
        } catch (IllegalAccessException e) {
            throw new PersistenceException(e);
            }
        }


        private boolean hasReference(Class<?> cls ){

            Field[] fields = cls.getDeclaredFields();
            for(Field field : fields){
                try{
                    field.getType().getDeclaredField("id");
                    return true;
                } catch (NoSuchFieldException e) {
                }
            }
            return false;
        }

        private Optional<Class<?>> getReference(Class<?> cls){

        Field[] fields = cls.getDeclaredFields();
        for(Field field : fields){
            try{
                field.getType().getDeclaredField("id");
                return Optional.of(field.getClass());
            } catch (NoSuchFieldException e) {
            }
        }
        return Optional.empty();
    }

    private List<Integer> getReferencePosition(Class<?> cls){
        List<Integer> positions = new ArrayList<>();
        Field[] fields = cls.getDeclaredFields();
        Integer index = 0;
        for(Field field : fields){
            try{
                field.getType().getDeclaredField("id");
                positions.add(index);
            } catch (NoSuchFieldException e) {
            }
            index++;
        }
        return positions;
    }

    private boolean checkIndexAgainstPositions(List<Integer> positions, Integer index){
        for(Integer position : positions){
            if (index == position) return true;
        }
        return false;
    }


    private <T> void insertNewReferenceIntoInstance(T classInstance, Integer index, Integer id ){
        Field affectedField = classInstance.getClass().getDeclaredFields()[index];
        affectedField.setAccessible(true);
        Class<?> fieldClass = affectedField.getType();
        try {
           Optional<?> newReference = get(fieldClass,id);
            affectedField.set(classInstance,newReference.get());
        } catch (IllegalAccessException e) {
            throw new PersistenceException(e);
        }
    }





}

