package sk.tuke.meta.persistence;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class SqlBuilder {
   static public String buildSqlInsert(String className, Field[] declaredFields, List<Object> actualFields) throws PersistenceException {
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


   static private String buildSqlInsertDeclared(List<Field> fields){
        String collString = "";
        for(int i = 0; i<fields.size();i++){
            collString = collString+fields.get(i).getName();
            if(i != fields.size()-1) collString = collString+", ";
        }
        return collString;
    }

    static private String buildSqlInsertActual(List<Object> actualFields) throws PersistenceException {
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

}
