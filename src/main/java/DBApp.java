package main.java;

import index.Octree;
import org.junit.platform.commons.function.Try;

import java.io.*;
import java.lang.reflect.Array;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class DBApp implements Serializable {

    private Vector<String> tables;
    private Metadata metaData;
    private String pagesFilepath;
    private String tablesFilepath;
    private int maxPageSize;
    public DBApp(){
        this.tables = new Vector<String>();
        //src/resources/data/pages
        this.pagesFilepath = "src/main/resources/data/";
        //resources/data/tables
        this.tablesFilepath = "src/main/resources/data/tables/";

    }
    public void init(){
        try {
            FileManipulation.createDirectory("src/main/resources/data");
//            System.out.println(1);
            FileManipulation.createDirectory(this.pagesFilepath);
//            System.out.println(2);
            FileManipulation.createDirectory(this.tablesFilepath);
//            System.out.println(3);
            metaData = new Metadata("src/main/resources/metadata.csv");
//            System.out.println(4);
            this.maxPageSize = FileManipulation.readFromConfig("MaximumRowsCountinPage");
//            System.out.println(5);
            this.tables = FileManipulation.loadFilesFromDirectory(this.tablesFilepath);
        }catch(Exception e){
            System.out.println("init: "+e);
        }
    }

    public void createTable(String strTableName,
                            String strClusteringKeyColumn,
                            Hashtable<String,String> htblColNameType,
                            Hashtable<String,String> htblColNameMin,
                            Hashtable<String,String> htblColNameMax ) throws DBAppException {

        try{
            for(int i = 0; i< tables.size(); i++){
                Table currTable = FileManipulation.loadTable(this.tablesFilepath,tables.get(i));
                String currentTableName = currTable.getTableName();
                if (currentTableName.equals(strTableName)){
                    throw new DBAppException("This table already exists");
                }
            }

            Set<Map.Entry<String, String>> entrySet = htblColNameType.entrySet();
            for (Map.Entry<String, String> entry : entrySet) {
                if (!isSupported(entry.getValue())){
                    throw new DBAppException("data type: "+entry.getValue()+" is not supported");
                }
            }
            checkMinAndMaxHtbl(htblColNameType, htblColNameMin, htblColNameMax);
            metaData.writeMetaData(
                    strTableName,
                    strClusteringKeyColumn,
                    htblColNameType,
                    htblColNameMin,
                    htblColNameMax
            );
            Table newTable = new Table(strTableName , htblColNameType.size() , this.maxPageSize, strClusteringKeyColumn);
            tables.add(newTable.getTableName());
        }catch (Exception e){
            throw new DBAppException(e.getMessage());
        }
    }
    public void checkMinAndMaxHtbl(Hashtable<String,String> htblColNameType,
                                   Hashtable<String,String> htblColNameMin,
                                   Hashtable<String,String> htblColNameMax) throws DBAppException {
        if(htblColNameType.size()!=htblColNameMax.size()){
            throw new DBAppException("the size of htblColNameType is not equal to size of htblColNameMax");
        }
        if(htblColNameType.size()!=htblColNameMin.size()){
            throw new DBAppException("the size of htblColNameType is not equal to size of htblColNameMin");
        }
        HashSet<String> maxColumnNames = new HashSet<String>();
        for(String key: htblColNameMax.keySet()){
            maxColumnNames.add(key);
        }
        HashSet<String> minColumnNames = new HashSet<String>();
        for(String key: htblColNameMin.keySet()){
            minColumnNames.add(key);
        }
        //check same keys in htbl type and htbl max and min
        for(String key: htblColNameType.keySet()){
            if(!maxColumnNames.contains(key)){
                throw new DBAppException("htblColNameMax does not contain the key: "+key);
            }
            if(!minColumnNames.contains(key)){
                throw new DBAppException("htblColNameMin does not contain the key: "+key);
            }
        }
        // check values of max and min are of correct corresponding types
        for(String key: htblColNameMax.keySet()){
            String value = htblColNameMax.get(key);
            String type = htblColNameType.get(key);
            try{
                Column.adjustDataType(value,type);
            } catch (Exception e){
                throw new DBAppException(value+" is not of type "+ type);
            }
        }

        for(String key: htblColNameMin.keySet()){
            String value = htblColNameMin.get(key);
            String type = htblColNameType.get(key);
            try{
                Column.adjustDataType(value,type);
            } catch (Exception e){
                throw new DBAppException(value+" is not of type "+ type);
            }
        }

    }
    public void createIndex(String strTableName , String[] strarrColName) throws DBAppException, IOException, ClassNotFoundException, ParseException {
        if(strarrColName.length<3){
            throw new DBAppException("Array missing index attributes ");
        } else if (strarrColName.length>3) {
            throw new DBAppException("index attributes should be only 3 ");
        }
        int tableIndex = checkTablePresent(strTableName);
        if(tableIndex==-1){
            throw new DBAppException("table not found");
        }
        Vector<String> columnNames = metaData.getColumnNames(strTableName);
        for (int i=0;i<3;i++){
            if (!columnNames.contains(strarrColName[i])){
                throw new DBAppException("columns specified are not in the table");
            }
        }
        Object[] firstAttribute= null;
        Object[] secondAttribute= null;
        Object[] thirdAttribute= null;
        Table toBeInsertedInTable = FileManipulation.loadTable(this.tablesFilepath,this.tables.get(tableIndex));
        firstAttribute=getMinMax(columnNames,strarrColName[0],strTableName);
        secondAttribute=getMinMax(columnNames,strarrColName[1],strTableName);
        thirdAttribute=getMinMax(columnNames,strarrColName[2],strTableName);
        Octree octree=new Octree(firstAttribute[0],firstAttribute[1],secondAttribute[0],secondAttribute[1],thirdAttribute[0],thirdAttribute[1],1);
        if(!toBeInsertedInTable.isTableEmpty()){

             // insert all the existing values in octree
            //ezay b2a el 3elm 3end allah
        }

    }
    public Object[] getMinMax(Vector<String> columnNames,String columnNeededString,String strTableName) {
        String[] minMax=null;
        Column columnNeeded=null;
//        for (int i = 0; i < columnNames.size(); i++) {
//            if (columnNames.get(i) == columnNeededString) {
//                type = metaData.getColumnType(strTableName, columnNeededString);
//                break;
//            }
//        }
        Vector<Column> columns = metaData.getColumnsOfMetaData().get(strTableName);
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getColumnName() == columnNeededString) {
                columnNeeded=columns.get(i);
                break;
            }
        }
        minMax[0]=columnNeeded.getMin();
        minMax[1]=columnNeeded.getMax();
        return minMax;

    }
    public int checkTablePresent(String strTableName) throws DBAppException, IOException, ClassNotFoundException {
        int tableIndex = -1;
        for(int i = 0; i < tables.size(); i++){
            Table currTable = FileManipulation.loadTable(this.tablesFilepath,this.tables.get(i));
            String currentTableName = currTable.getTableName();
            if (currentTableName.equals(strTableName)){
                tableIndex = i;
                break;
            }
        }
        if (tableIndex==-1) {
            throw new DBAppException("This Table is not present");
        }
        return tableIndex;
    }
    public void insertingNulls(Vector<String> missingColumnNames,Hashtable<String,Object> htblColNameValue){
        for(int i=0;i<missingColumnNames.size();i++){
            htblColNameValue.put(missingColumnNames.get(i), new SimulatingNull());
        }
    }
    public void checkHtblValid(String strTableName, Hashtable<String,Object> htblColNameValue, boolean insert) throws Exception {
        Vector<String> columnNames = metaData.getColumnNames(strTableName);
        Set<Map.Entry<String, Object>> entrySet = htblColNameValue.entrySet();
        if (insert) {
            String clusteringKey = metaData.getTableClusteringKey(strTableName);
            if(!htblColNameValue.containsKey(clusteringKey)){
                throw new DBAppException(clusteringKey+" is not found in the entry");
            }
            if(entrySet.size() > columnNames.size()){
                throw new DBAppException("There are extra column(s)");
            }else {
                for (Map.Entry<String, Object> entry : entrySet) {
                    String key = entry.getKey();
                    if(!columnNames.contains(key)){
                        throw new DBAppException(key+" is a non existing column");
                    }
                }
                Vector<String> missingColumnNames = new Vector<String>();
                for(int i=0;i<columnNames.size();i++) {
                    if(!htblColNameValue.containsKey(columnNames.get(i))) {
                        missingColumnNames.add(columnNames.get(i));
                    }
                }
                insertingNulls(missingColumnNames,htblColNameValue);
            }
        }
        for (Map.Entry<String, Object> entry : entrySet) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if(!columnNames.contains(key)){
                throw new DBAppException("The Column names aren't correct");
            }
            String columnType = metaData.getColumnType(strTableName,key);
            String valClass = ((""+value.getClass()).replaceAll("class","")).replaceAll(" ","");
//            System.out.println(key+": "+valClass+","+columnType);
            if(valClass.compareTo("main.java.SimulatingNull")==0){
                continue;
            }
            if(valClass.compareTo(columnType)!=0){
                if(!(valClass.equals("java.lang.Integer")&&columnType.equals("java.lang.Double"))){
                    throw new DBAppException("Please check " + key + " as it has a wrong data type");
                }
                else {
                    htblColNameValue.put(key,Double.parseDouble("" + value));
                }
//                System.out.println(valClass+"-"+columnType);
            }
//            System.out.println(key+": "+value);
            Vector<Object> columnMinAndMax = metaData.getColumnMinAndMax(strTableName,key,columnType);
            Object min = columnMinAndMax.get(0);
            Object max = columnMinAndMax.get(1);
//            System.out.println(max.toString());
            boolean lessThanMin = Comparison.compareTo(value,min,columnType)<0;
            boolean greaterThanMax = Comparison.compareTo(value,max,columnType)>0;
//            System.out.println(lessThanMin+" ,"+ greaterThanMax);
            if(lessThanMin || greaterThanMax){
                throw new DBAppException("Please Check " + key + " as " + value + " is out of range.");
            }
        }
    }
    public void insertIntoTable(String strTableName, Hashtable<String,Object> htblColNameValue) throws DBAppException {
        try{
            metaData.loadMetaData();
            int tableIndex = checkTablePresent(strTableName);
            checkHtblValid(strTableName, htblColNameValue, true);
            Table toBeInsertedInTable = FileManipulation.loadTable(this.tablesFilepath,this.tables.get(tableIndex));
            toBeInsertedInTable.insert(htblColNameValue);
        }catch (Exception e){
            e.printStackTrace();
            throw new DBAppException(e.getMessage());
        }
    }
    public void updateTable(String strTableName, String strClusteringKeyValue, Hashtable<String,Object> htblColNameValue ) throws DBAppException {
        try{
            metaData.loadMetaData();
//            Vector<Column> columns = metaData.getColumnsOfMetaData().get(strTableName);
            int tableIndex = checkTablePresent(strTableName);
            checkHtblValid(strTableName, htblColNameValue, false);
            Table currTable = FileManipulation.loadTable(this.tablesFilepath,this.tables.get(tableIndex));
            if(htblColNameValue.containsKey(currTable.getClusteringKey())){
                throw new DBAppException(currTable.getClusteringKey()+" clustering key should not be in the htbl");
            }
            currTable.update(strClusteringKeyValue, htblColNameValue);
        } catch (Exception e){
            throw new DBAppException(e.getMessage());
        }
    }
    public void deleteFromTable(String strTableName, Hashtable<String,Object> htblColNameValue) throws DBAppException{
        try {
            metaData.loadMetaData();
            int tableIndex = checkTablePresent(strTableName);
            checkHtblValid(strTableName, htblColNameValue, false);
            Table currTable = FileManipulation.loadTable(this.tablesFilepath, this.tables.get(tableIndex));
            currTable.delete(htblColNameValue);
        } catch (Exception e){
            throw new DBAppException(e.getMessage());
        }
    }
//  public Iterator selectFromTable(SQLTerm[] arrSQLTerms, String[] strarrOperators) throws main.java.DBAppException {
//
//  }
    public boolean isSupported(String dt){
        HashSet<String> supportedDataTypes = new HashSet<String>();
        supportedDataTypes.add("java.lang.Integer");
        supportedDataTypes.add("java.lang.String");
        supportedDataTypes.add("java.lang.Double");
        supportedDataTypes.add("java.util.Date");
        return supportedDataTypes.contains(dt);
    }


    public static void main(String[] args) throws Exception {
        // leba tests
////tests/*        Hashtable<String, Object> tuple0 = new Hashtable<>();
////        tuple0.put("age", 0);
////        tuple0.put("name", "Soubra");
////        tuple0.put("gpa", 1.6);*/

//        Hashtable<String, Object> tuple0 = new Hashtable<>();
//        tuple0.put("age", 0);
//        tuple0.put("name", "Soubra");
//        tuple0.put("gpa", 1.6);

        Hashtable<String, Object> tuple1 = new Hashtable<>();
        tuple1.put("age", 1);
        tuple1.put("name", "Kimo2");
        tuple1.put("gpa", 5.0);
        tuple1.put("dob" , new SimpleDateFormat("dd-MM-yyyy").parse("05-07-2002"));

        Hashtable<String, Object> tuple2 = new Hashtable<>();
        tuple2.put("age", 2);
        tuple2.put("name", "Omar");
        tuple2.put("gpa", 4.0);

        Hashtable<String, Object> tuple3 = new Hashtable<>();
        tuple3.put("age", 3);
        tuple3.put("name", "Ahmed");
        tuple3.put("gpa", 0.9);
        tuple3.put("dob" , new SimpleDateFormat("dd-MM-yyyy").parse("05-12-1999"));

        Hashtable<String, Object> tuple4 = new Hashtable<>();
        tuple4.put("age", 4);
        tuple4.put("name", "biso");
        tuple4.put("gpa", 2.3);
        tuple4.put("dob" , new SimpleDateFormat("dd-MM-yyyy").parse("01-01-2024"));

        Hashtable<String, Object> tuple5 = new Hashtable<>();
        tuple5.put("age", 5);
        tuple5.put("name", "Menna");
        tuple5.put("gpa", 0.8);

        Hashtable<String, Object> tuple6 = new Hashtable<>();
        tuple6.put("age", 6);
        tuple6.put("name", "Lobna");
        tuple6.put("gpa", 1.4);

        Hashtable<String, Object> tuple7 = new Hashtable<>();
        tuple7.put("age", 7);
        tuple7.put("name", "boni");
        tuple7.put("gpa", 3.2);

        Hashtable<String, Object> tuple8 = new Hashtable<>();
        tuple8.put("age", 8);
        tuple8.put("name", "nada");
        tuple8.put("gpa", 2.5);

        Hashtable<String, Object> tuple9 = new Hashtable<>();
        tuple9.put("age", 9);
        tuple9.put("name", "noura");
        tuple9.put("gpa", 3.4);

        Hashtable<String, Object> tuple10 = new Hashtable<>();
        tuple10.put("age", 10);
        tuple10.put("name", "nada");
        tuple10.put("gpa", 0.9);

        Hashtable<String, Object> tuple11 = new Hashtable<>();
        tuple11.put("age", 11);
        tuple11.put("name", "nada");
        tuple11.put("gpa", 0.9);

        Hashtable<String, Object> tuple12 = new Hashtable<>();
        tuple12.put("age", 12);
        tuple12.put("name", "sara");
        tuple12.put("gpa", 0.9);

        Hashtable<String, Object> tuple13 = new Hashtable<>();
        tuple13.put("age", 13);
        tuple13.put("name", "sara");
        tuple13.put("gpa", 0.9);

        Hashtable<String, Object> tuple14 = new Hashtable<>();
        tuple14.put("age", 14);

        Hashtable<String, Object> tuple15 = new Hashtable<>();
        tuple15.put("age", 29);
        tuple15.put("name", "afterMod");

        Hashtable<String, Object> tuple16 = new Hashtable<>();
        tuple16.put("age", 18);
        tuple16.put("name", "kimo");
        tuple15.put("gpa", 4);

        Hashtable<String, Object> tuple17 = new Hashtable<>();
        tuple17.put("age", 19);
//        tuple17.put("name", "kimo");
        tuple17.put("gpa", 3.0);

        Hashtable<String, Object> tuple18 = new Hashtable<>();
        tuple18.put("age", 17);
        tuple18.put("name", "kimo");
//        tuple18.put("gpa", 3.0);
        tuple18.put("gpa", 3.0);

        Hashtable<String, Object> tuple19 = new Hashtable<>();
        tuple19.put("age", 16);
        tuple19.put("name", "kimo");
//        tuple19.put("gpa", 3.0);
        tuple19.put("gpa", 3.0);

        Hashtable<String, String> htblColNameType = new Hashtable<>();
        htblColNameType.put("age", "java.lang.Integer");
        htblColNameType.put("name", "java.lang.String");
        htblColNameType.put("gpa", "java.lang.Double");
        htblColNameType.put("dob", "java.util.Date");
//        htblColNameType.put("job", "java.lang.String");

        Hashtable<String, String> htblColNameMin = new Hashtable<>();
        htblColNameMin.put("age", "0");
        htblColNameMin.put("name", "A");
        htblColNameMin.put("gpa", "0");
        htblColNameMin.put("dob", "1950-12-31");
//        htblColNameMin.put("job", "1900-012-31");

        Hashtable<String, String> htblColNameMax = new Hashtable<>();
        htblColNameMax.put("age", "40");
        htblColNameMax.put("name", "ZZZZZZZZZZ");
        htblColNameMax.put("gpa", "5");
        htblColNameMax.put("dob", "2023-12-31");
//        htblColNameMax.put("job", "e");
//
        DBApp dbApp = new DBApp();
        dbApp.init();
         dbApp.createTable("Students", "dob", htblColNameType, htblColNameMin, htblColNameMax);
//////        dbApp.createTable("Students2", "age", htblColNameType, htblColNameMin, htblColNameMax);
//////     //    dbApp.insertIntoTable("Students", tuple0);
         dbApp.insertIntoTable("Students", tuple1);
         dbApp.insertIntoTable("Students", tuple2);
//         dbApp.insertIntoTable("Students", tuple6);
//         dbApp.insertIntoTable("Students", tuple7);
//         dbApp.insertIntoTable("Students", tuple8);
//         dbApp.insertIntoTable("Students", tuple3);
//         dbApp.insertIntoTable("Students", tuple5);
//         dbApp.insertIntoTable("Students", tuple4);
//         dbApp.insertIntoTable("Students", tuple9);
//         dbApp.insertIntoTable("Students", tuple11);
//         dbApp.insertIntoTable("Students", tuple10);
//         dbApp.insertIntoTable("Students", tuple12);
//         dbApp.insertIntoTable("Students", tuple13);
//         dbApp.insertIntoTable("Students", tuple14);
//         dbApp.insertIntoTable("Students", tuple15);
//         dbApp.insertIntoTable("Students", tuple16);
//         dbApp.insertIntoTable("Students", tuple17);
//         dbApp.insertIntoTable("Students", tuple18);
//         dbApp.insertIntoTable("Students", tuple19);

// https://forms.gle/8LdxcCXyybNyxNtv7

         Hashtable<String, Object> updateHtbl = new Hashtable<>();
         updateHtbl.put("name", "a");
         updateHtbl.put("gpa", 2);
         dbApp.updateTable("Students", "2002-07-05", updateHtbl);

//         updateHtbl.put("name", "boniiiii");

         Hashtable<String,Object> deletingCriteria0 = new Hashtable<>();
//         Hashtable<String,Object> deletingCriteria1 = new Hashtable<>();
//         Hashtable<String,Object> deletingCriteria2 = new Hashtable<>();
//         Hashtable<String,Object> deletingCriteria3 = new Hashtable<>();
//         deletingCriteria0.put( "dob",new SimpleDateFormat("dd-MM-yyyy").parse("05-12-1999"));
         deletingCriteria0.put("gpa",2);
         deletingCriteria0.put("name","a");
//         deletingCriteria1.put("gpa", 0.9);
//         deletingCriteria2.put( "name", "nada");
//         deletingCriteria3.put( "age", 5);
//        dbApp.deleteFromTable("Students", deletingCriteria0);
//        deletingCriteria3.put( "age", 6);
//        dbApp.deleteFromTable("Students", deletingCriteria3);
//        deletingCriteria3.put( "age", 7);
//        dbApp.deleteFromTable("Students", deletingCriteria3);
//        deletingCriteria3.put( "age", 8);
//        dbApp.deleteFromTable("Students", deletingCriteria3);



       dbApp.deleteFromTable("Students", deletingCriteria0);
//        dbApp.deleteFromTable("Students", deletingCriteria1);
//        dbApp.insertIntoTable("Students", tuple12);
//        dbApp.insertIntoTable("Students", tuple13);
//        dbApp.insertIntoTable("Students", tuple13);

//        dbApp.deleteFromTable("Students", deletingCriteria2);
//        dbApp.deleteFromTable("Students", deletingCriteria3);
//    System.out.println(dbApp.tables);
        Table table = FileManipulation.loadTable(dbApp.tablesFilepath,dbApp.tables.get(0));

        for (String pageName : table.getTablePages()) {
            Page p = FileManipulation.loadPage(pageName);
            System.out.println("PAGE " + p.getPageID());
            System.out.println(p.getPageTuples());
            System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
//            p.serialize();
        }
    }
}