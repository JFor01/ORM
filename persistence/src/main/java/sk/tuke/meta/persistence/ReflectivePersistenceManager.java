package sk.tuke.meta.persistence;

import sk.tuke.meta.annotations.Column;
import sk.tuke.meta.annotations.Id;
import sk.tuke.meta.annotations.Table;

import javax.swing.text.AttributeSet;
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
        LinkedHashMap<String,List<Pair<String,Class<?>>>> classFields = getClassFields(types);
        List<Optional<Integer>> idPositions = getIdPositions(types);
        List<Optional<List<Optional<Integer>>>> columnPositions = getColumnPositions(types);
        List<String> sqlStrings = getSqlStrings(classFields,idPositions,columnPositions);
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
        String className = getTableName(type);
        String selectQuery = "SELECT * FROM " + "'" + className + "' " + " WHERE ID = (?)";
        Field[] fields = type.getDeclaredFields();
        List<String> types = getTypes(fields);

        for(String fieldType :types){
            System.out.println(fieldType);
        }

        ResultSet resultSet = executeGetQuery(selectQuery,id);
        List<Optional<Integer>> annotationPositions = getColumnPositions(type).get(0).get();
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


        return insertFieldsIntoInstance(resultSet,types,classInstance,annotationPositions);
    }

    @Override
    public <T> List<T> getAll(Class<T> type) {
        String className = getTableName(type);
        String selectQuery = "SELECT * FROM " + "'" + className + "' ";
        Field[] fields = type.getDeclaredFields();
        List<String> types = getTypes(fields);
        List<Optional<Integer>> annotationPositions = getColumnPositions(type).get(0).get();

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
                Optional<T> optClassInstance = insertFieldsIntoInstance(resultSet,types,classInstance,annotationPositions);
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
        System.out.println(entity.getClass().getAnnotation(Id.class));
        try {
            List<Optional<Integer>> idPosition = getIdPositions(entity.getClass());
            Field field;
            Integer idNumber;
            if(idPosition.get(0).isPresent()){
                 idNumber = idPosition.get(0).get();
                 field = entity.getClass().getDeclaredFields()[idNumber];
            }
            else {
                field = entity.getClass().getDeclaredField("id");
                idNumber = 0;
            }
            field.setAccessible(true);
            String idString = field.get(entity).toString();
            long id =Long.parseLong(idString);
            if(id == 0) insert(entity,idNumber);
            else update(entity,id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new PersistenceException(e);
        }

    }

    @Override
    public void delete(Object entity) {
        String name = getTableName( entity.getClass());
        long id;
        try {
            Field field;
            Optional<Integer> idPosition = getIdPositions( entity.getClass()).get(0);
            if(idPosition.isPresent()){
                field = entity.getClass().getDeclaredFields()[idPosition.get()];
            }
            else field = entity.getClass().getDeclaredField("id");
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


    private LinkedHashMap<String,List<Pair<String,Class<?>>>> getClassFields(Class<?>... types){
        LinkedHashMap<String,List<Pair<String,Class<?>>>> classFields = new LinkedHashMap<String,List<Pair<String,Class<?>>>>();
        for (Class<?> type :types){
            List<Pair<String,Class<?>>> fieldDataTypes = new ArrayList<Pair<String,Class<?>>>();
            Field[] fields = type.getDeclaredFields();
            String className = getTableName(type);
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

    public List<String> getSqlStrings(LinkedHashMap<String, List<Pair<String,Class<?>>>> classFields, List<Optional<Integer>> anotationPositions, List<Optional<List<Optional<Integer>>>> columnPositions){
        List<String> createTableSqlStrings = new ArrayList<String>();
        int counter = 0;
        for (String className : classFields.keySet()) {
            Optional<Integer> annotationPostition = anotationPositions.get(counter);
            Optional<List<Optional<Integer>>> columnPosition = columnPositions.get(counter);
            if(columnPosition.isEmpty()) continue;
            List<String> foreignKeys = new ArrayList<>();
            String createTableSqlString = "CREATE TABLE IF NOT EXISTS ";
            List<Pair<String,Class<?>>> fieldTypes = classFields.get(className);
            createTableSqlString = createTableSqlString + "'"+className+"'" + " (%s)";
            System.out.println(createTableSqlString);
            String collumString = "";

            for (int i = 0; i < fieldTypes.size(); i++) {
                if(columnPosition.get().get(i).isEmpty()) continue;
                String sqlType = convertJavaTypesToSql(fieldTypes.get(i).getSecond());
                if (sqlType.startsWith("FOREIGN:")) {
                    sqlType = sqlType.substring(8);
                    String name = sqlType.split(":")[0];
                    String type = sqlType.split(":")[1];
                    foreignKeys.add(name);
                    sqlType = type;
                }
                String fieldname = fieldTypes.get(i).getFirst();
                if(annotationPostition.isPresent()) collumString = formatId(i,collumString,fieldname,sqlType,fieldTypes,annotationPostition.get());
                else collumString = formatId(i,collumString,fieldname,sqlType,fieldTypes,0);
            }
            int lenghtOfString = collumString.length();
            if(collumString.substring(lenghtOfString - 2,lenghtOfString-1).equals(String.valueOf(','))){
                collumString = collumString.substring(0,lenghtOfString-2);

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
            counter ++;
        }
        return createTableSqlStrings;
    }





    private void updateClassId(Object entity,long id){
        try {
            Field idField;
            Optional<Integer> idPosition = getIdPositions(entity.getClass()).get(0);
            if(idPosition.isPresent()) idField = entity.getClass().getDeclaredFields()[idPosition.get()];
            else idField =  entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity,id);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new PersistenceException(e);
        }

    }

    private void insert(Object entity, int idPosition){
        String className = getTableName(entity.getClass());
        String insertString = "INSERT INTO " + "'" + className+ "'"+ "(%s) VALUES (%s)";
        Optional<ResultSet> generatedKeys;
        List<String> declaredFields = getDeclaredFields(entity);
        List<FieldTypeAndValue> actualFields= getActualFields(entity);

        //Getting rid of the unnecessary id
        declaredFields.remove(idPosition);
        actualFields.remove(idPosition);

        List<Optional<Integer>> columnsPositions = getColumnPositions(entity.getClass()).get(0).get();

        columnsPositions.remove(idPosition);

        actualFields = removeNonAnnotatedFields(actualFields,columnsPositions);

        String dataString = formatInsertString(declaredFields,columnsPositions);
        String dataString2 = formatInsertStringTokens(declaredFields.size(),columnsPositions);
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
        String name = getTableName(entity.getClass());
        String strId = Long.toString(id);
        List<String> declaredFields = getDeclaredFields(entity);
        List<FieldTypeAndValue> actualFields = getActualFields(entity);
        List<Optional<Integer>> columnsPositions = getColumnPositions(entity.getClass()).get(0).get();
        actualFields = removeNonAnnotatedFields(actualFields,columnsPositions);

        String sqlQuerry = "UPDATE " + "'" + name + "'" + " SET %s \nWHERE " + "'" + name + "'"+".id == " + strId;
        String dataString = formatDataUpdate(declaredFields,columnsPositions);
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

    private String formatDataUpdate(List<String> declaredFields, List<Optional<Integer>> annotationPositions){
        String dataString = "";
        for (int i = 0;i<declaredFields.size();i++){
            if(annotationPositions.get(i).isEmpty()) continue;
            if (i != declaredFields.size()-1) dataString = dataString+declaredFields.get(i) + " =?,";
            else dataString = dataString+declaredFields.get(i) + " =?";
        }
        int stringLenght = dataString.length();
        if(dataString.substring(stringLenght-1).equals(String.valueOf(','))){
            dataString = dataString.substring(0,stringLenght-1);
        }

        return dataString;
    }
    private String formatInsertString(List<String> declaredFields, List<Optional<Integer>> columnPositions){
        String dataString = "";
        for (int i = 0;i<declaredFields.size();i++){
            if(columnPositions.get(i).isEmpty()) continue;
            if (i != declaredFields.size()-1) dataString = dataString+declaredFields.get(i) + ",";
            else dataString = dataString+declaredFields.get(i) ;
        }
        int stringLenght = dataString.length();
        if(dataString.substring(stringLenght-1).equals(String.valueOf(','))){
            dataString = dataString.substring(0,stringLenght-1);
        }
        return dataString;
    }

    private String formatInsertStringTokens(int size, List<Optional<Integer>> columnPositions){
        String dataString = "";
        for (int i = 0;i<size;i++){
            if(columnPositions.get(i).isEmpty()) continue;
            if (i != size-1) dataString = dataString+ "?,";
            else dataString = dataString+ "?";
        }
        int stringLenght = dataString.length();
        if(dataString.substring(stringLenght-1).equals(String.valueOf(','))){
            dataString = dataString.substring(0,stringLenght-1);
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

    private <T> Optional<T> insertFieldsIntoInstance(ResultSet resultSet, List<String> types,T classInstance,List<Optional<Integer>> annotationPositions){

        Class<?> clasReflexion = classInstance.getClass();
        List<Integer> positions;
        positions = getReferencePosition(clasReflexion);

            try{
                int index = 1;
                int indexOffset = 0;
                for (String type : types) {
                    if(annotationPositions.get(index-1).isEmpty()) {
                        index++;
                        indexOffset += 1;
                        continue;
                    }
                    if(checkIndexAgainstPositions(positions,index-1)){
                        insertNewReferenceIntoInstance(classInstance,index-1,resultSet.getInt(index));
                        index++;
                        continue;
                    }

                    switch (type) {
                        case "long": insertValueIntoInstance(resultSet.getLong(index-indexOffset),classInstance,index-1);
                        break;
                        case "String" : insertValueIntoInstance(resultSet.getString(index-indexOffset),classInstance,index-1);

                        break;
                        case "int" : insertValueIntoInstance(resultSet.getInt(index-indexOffset),classInstance,index-1);
                        break;
                        case "float" : insertValueIntoInstance(resultSet.getFloat(index-indexOffset),classInstance,index-1);
                        break;
                        case "double" : insertValueIntoInstance(resultSet.getDouble(index-indexOffset),classInstance,index-1);
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


    private List<Optional<Integer>> getIdPositions(Class<?>... types){
        List<Optional<Integer>> idPositions = new ArrayList<>();

        for(Class<?> type : types){
            Field[] fields = type.getDeclaredFields();
            for(int i = 0; i<fields.length;i++ ){
                if(fields[i].getAnnotation(Id.class)!=null){
                    idPositions.add(Optional.of(i));
                    break;
                }
                if(i == fields.length-1){
                    idPositions.add(Optional.empty());
                }
            }
        }
        System.out.println(idPositions);


        return idPositions;
    }

    private String formatId(int i, String collumString,String fieldname, String sqlType,List<Pair<String,Class<?>>> fieldTypes, int comparedValue ){
        String newString = null;
        if(i == comparedValue) {
            newString = collumString + "'"+ fieldname + "' " + sqlType + " PRIMARY KEY AUTOINCREMENT,\n";
        }
        else if(i==fieldTypes.size()-1){newString = collumString + "'"+ fieldname + "' " + sqlType ;}
        else {newString = collumString +  "'"+ fieldname + "' " + sqlType + ",\n";}
        return newString;
    }


    private String getTableName(Class<?> entity){
        String name;
        if (Objects.equals(entity.getAnnotation(Table.class).name(), "None")){
            name = entity.getSimpleName();

        }
        else name = entity.getAnnotation(Table.class).name();
        return name;

    }


    private List<Optional<List<Optional<Integer>>>> getColumnPositions(Class<?>... types){
        List<Optional<List<Optional<Integer>>>> columnPositions= new ArrayList<>();

        for(Class<?> type : types){
            Field[] fields = type.getDeclaredFields();
            List<Optional<Integer>> positions = new ArrayList<>();
            for(int i = 0; i<fields.length;i++ ) {
                if (fields[i].getAnnotation(Column.class) != null) {
                    positions.add(Optional.of(i));
                } else {
                    positions.add(Optional.empty());
                }
            }
            if(allEmpty(positions)) {
                columnPositions.add(Optional.empty());
            }
            else columnPositions.add(Optional.of(positions));
            }
        System.out.println(columnPositions);


        return columnPositions;
    }

    private boolean allEmpty(List<Optional<Integer>> positions){
        for (Optional<Integer> position : positions){
            if (position.isPresent()) return false;
        }
        return true;
    }


    private List<FieldTypeAndValue> removeNonAnnotatedFields(List<FieldTypeAndValue> actualFields,List<Optional<Integer>> annotationPositions ){
        List<FieldTypeAndValue> newFields = new ArrayList<>();
        for(int i = 0;i < actualFields.size(); i++ ){
            if(annotationPositions.get(i).isPresent()){
                newFields.add(actualFields.get(i));
            }


        }
        return newFields;

    }




}

