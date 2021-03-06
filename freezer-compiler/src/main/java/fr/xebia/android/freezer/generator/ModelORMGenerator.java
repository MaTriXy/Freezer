package fr.xebia.android.freezer.generator;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

import fr.xebia.android.freezer.Constants;
import fr.xebia.android.freezer.ProcessUtils;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;

/**
 * Created by florentchampigny on 08/01/2016.
 */
public class ModelORMGenerator {

    private final Element element;

    String modelName;
    String modelPackage;
    TypeName modelClassName;

    TypeName modelCursorHelperClassName;

    TypeName queryBuilderClassName;

    TypeName enumColums;

    String TABLE_NAME;

    TypeSpec queryBuilder;
    TypeSpec dao;

    Element fieldId;
    List<VariableElement> fields;
    List<VariableElement> otherClassFields;
    List<VariableElement> collections;

    public ModelORMGenerator(Element element) {
        this.element = element;
        this.modelName = ProcessUtils.getObjectName(element);
        this.modelPackage = ProcessUtils.getObjectPackage(element);

        this.modelClassName = TypeName.get(element.asType());
        this.modelCursorHelperClassName = ProcessUtils.getCursorHelper(element);

        this.TABLE_NAME = ProcessUtils.getTableName(element);

        this.queryBuilderClassName = ProcessUtils.getQueryBuilder(element);
        this.enumColums = ProcessUtils.getElementEnumColumn(element);

        this.fields = ProcessUtils.getPrimitiveFields(element);
        this.otherClassFields = ProcessUtils.getNonPrimitiveClassFields(element);
        this.collections = ProcessUtils.getCollectionsOfPrimitiveFields(element);

        this.fieldId = ProcessUtils.getIdField(element);
    }

    public TypeSpec getDao() {
        return dao;
    }

    public TypeSpec getQueryBuilder() {
        return queryBuilder;
    }

    public ModelORMGenerator generate() {

        TypeName listObjectsClassName = ProcessUtils.listOf(modelClassName);

        this.queryBuilder = TypeSpec.classBuilder(ProcessUtils.getQueryBuilderName(modelName)) //UserDAOQueryBuilder
            .addModifiers(Modifier.PUBLIC)

            .superclass(Constants.queryBuilderClassName)

            .addField(ProcessUtils.listOf(enumColums), "fields")

            .addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement("super()")
                .build())

            .addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(TypeName.BOOLEAN, "named")
                .addParameter(ClassName.get(Constants.DAO_PACKAGE, Constants.QUERY_LOGGER), "logger")
                .addStatement("this()")
                .addStatement("this.named = named")
                .addStatement("this.logger = logger")
                .build())

            .addMethods(generateQueryMethods())

            .addMethod(MethodSpec.methodBuilder("or")
                .returns(queryBuilderClassName)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("super.appendOr()")
                .addStatement("return this")
                .build())

            .addMethod(MethodSpec.methodBuilder("and")
                .returns(queryBuilderClassName)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("super.appendAnd()")
                .addStatement("return this")
                .build())

            .addMethod(MethodSpec.methodBuilder("beginGroup")
                .returns(queryBuilderClassName)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("super.appendBeginGroup()")
                .addStatement("return this")
                .build())

            .addMethod(MethodSpec.methodBuilder("endGroup")
                .returns(queryBuilderClassName)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("super.appendEndGroup()")
                .addStatement("return this")
                .build())

            .addMethod(MethodSpec.methodBuilder("limit")
                .returns(queryBuilderClassName)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(TypeName.INT, "start")
                .addParameter(TypeName.INT, "count")
                .addStatement("super.limitStartNumber(start, count)")
                .addStatement("return this")
                .build())

            .addMethod(MethodSpec.methodBuilder("asList")
                .returns(listObjectsClassName)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("return execute()")
                .build())

            .addMethod(MethodSpec.methodBuilder("asObservable")
                .returns(ParameterizedTypeName.get(ClassName.get(Observable.class), listObjectsClassName))
                .addModifiers(Modifier.PUBLIC)
                .addCode("return $T.create(new $T<$T>(){\n", ClassName.get(Observable.class), ClassName.get(ObservableOnSubscribe.class), listObjectsClassName)
                .addCode("@$T\n", ClassName.get(Override.class))
                .addCode("public void subscribe($T<$T> subscriber) {\n", ClassName.get(ObservableEmitter.class), listObjectsClassName)
                .addStatement("subscriber.onNext(asList())")
                .addStatement("subscriber.onComplete()")
                .addCode("}\n")
                .addCode("});\n")
                .build())

            .addMethod(MethodSpec.methodBuilder("async")
                .addParameter(ParameterizedTypeName.get(Constants.callback, listObjectsClassName), "callback")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("final $T<$T<$T>> weakReference = new $T<>(callback)", ClassName.get(WeakReference.class), Constants.callback, listObjectsClassName, ClassName.get(WeakReference.class))
                .addStatement("final $T current = new Handler($T.myLooper())", ClassName.get("android.os", "Handler"), ClassName.get("android.os", "Looper"))
                .addCode("new Handler().post(new $T() {\n", ClassName.get(Runnable.class))
                .addCode("@$T public void run() {\n", ClassName.get(Override.class))
                .addStatement("final $T objects = asList()", listObjectsClassName)
                .addCode("current.post(new Runnable() {\n")
                .addCode("@Override public void run() {\n")
                .addStatement("$T callback1 = weakReference.get()", ParameterizedTypeName.get(Constants.callback, listObjectsClassName))
                .addStatement("if(callback1 != null) callback1.onSuccess(objects)")
                .addCode("}\n")
                .addCode("});\n")
                .addCode("}\n")
                .addCode("});\n")
                .build())

            .addMethod(MethodSpec.methodBuilder("first")
                .returns(modelClassName)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("$T objects = asList()", listObjectsClassName)
                .addStatement("if(objects.isEmpty()) return null")
                .addStatement("else return objects.get(0)")
                .build())

            .addMethod(MethodSpec.methodBuilder("sortAsc")
                .returns(queryBuilderClassName)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(enumColums, "column")
                .addStatement("super.appendSortAsc($S,column.getName())", " " + TABLE_NAME + " .")
                .addStatement("return this")
                .build())

            .addMethod(MethodSpec.methodBuilder("sortDesc")
                .returns(queryBuilderClassName)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(enumColums, "column")
                .addStatement("super.appendSortDesc($S,column.getName())", " " + TABLE_NAME + " .")
                .addStatement("return this")
                .build())

            .addMethod(MethodSpec.methodBuilder("sum")
                .returns(TypeName.FLOAT)
                .addParameter(enumColums, "column")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("$T db = $T.getInstance().open().getDatabase()", Constants.databaseClassName, Constants.daoClassName)
                .addStatement("$T cursor = db.rawQuery($S + column.getName() + $S + constructQuery(), constructArgs())", Constants.cursorClassName, String.format("select sum(%s.", TABLE_NAME), String.format(") from %s ", TABLE_NAME))
                .addStatement("cursor.moveToNext()")
                .addStatement("float value = cursor.getFloat(0)")

                .addStatement("cursor.close()")
                .addStatement("$T.getInstance().close()", Constants.daoClassName)

                .addStatement("return value")
                .build())

            .addMethod(MethodSpec.methodBuilder("min")
                .returns(TypeName.FLOAT)
                .addParameter(enumColums, "column")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("$T db = $T.getInstance().open().getDatabase()", Constants.databaseClassName, Constants.daoClassName)
                .addStatement("$T cursor = db.rawQuery($S + column.getName() + $S + constructQuery(), constructArgs())", Constants.cursorClassName, String.format("select min(%s.", TABLE_NAME), String.format(") from %s ", TABLE_NAME))
                .addStatement("cursor.moveToNext()")
                .addStatement("float value = cursor.getFloat(0)")

                .addStatement("cursor.close()")
                .addStatement("$T.getInstance().close()", Constants.daoClassName)

                .addStatement("return value")
                .build())

            .addMethod(MethodSpec.methodBuilder("max")
                .returns(TypeName.FLOAT)
                .addParameter(enumColums, "column")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("$T db = $T.getInstance().open().getDatabase()", Constants.databaseClassName, Constants.daoClassName)
                .addStatement("$T cursor = db.rawQuery($S + column.getName() + $S + constructQuery(), constructArgs())", Constants.cursorClassName, String.format("select max(%s.", TABLE_NAME), String.format(") from %s ", TABLE_NAME))
                .addStatement("cursor.moveToNext()")
                .addStatement("float value = cursor.getFloat(0)")

                .addStatement("cursor.close()")
                .addStatement("$T.getInstance().close()", Constants.daoClassName)

                .addStatement("return value")
                .build())

            .addMethod(MethodSpec.methodBuilder("average")
                .returns(TypeName.FLOAT)
                .addParameter(enumColums, "column")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("$T db = $T.getInstance().open().getDatabase()", Constants.databaseClassName, Constants.daoClassName)
                .addStatement("$T cursor = db.rawQuery($S + column.getName() + $S + constructQuery(), constructArgs())", Constants.cursorClassName, String.format("select avg(%s.", TABLE_NAME), String.format(") from %s ", TABLE_NAME))
                .addStatement("cursor.moveToNext()")
                .addStatement("float value = cursor.getFloat(0)")

                .addStatement("cursor.close()")
                .addStatement("$T.getInstance().close()", Constants.daoClassName)
                .addStatement("return value")

                .build())

            .addMethod(MethodSpec.methodBuilder("count")
                .returns(TypeName.INT)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("$T db = $T.getInstance().open().getDatabase()", Constants.databaseClassName, Constants.daoClassName)
                .addStatement("$T cursor = db.rawQuery($S + constructQuery(), constructArgs())", Constants.cursorClassName, String.format("select count(distinct(%s.%s)) from %s ", TABLE_NAME, Constants.FIELD_ID, TABLE_NAME))
                .addStatement("cursor.moveToNext()")
                .addStatement("int value = cursor.getInt(0)")

                .addStatement("cursor.close()")
                .addStatement("$T.getInstance().close()", Constants.daoClassName)
                .addStatement("return value")

                .build())

            .addMethod(MethodSpec.methodBuilder("fields")
                .returns(queryBuilderClassName)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ArrayTypeName.of(enumColums), "fields")
                .varargs()
                .addStatement("this.fields = new $T<>()", TypeName.get(ArrayList.class))
                .addStatement("this.fields.addAll($T.asList(fields))", TypeName.get(Arrays.class))
                .addStatement("return this")

                .build())

            .addMethod(MethodSpec.methodBuilder("fieldsWithout")
                .returns(queryBuilderClassName)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ArrayTypeName.of(enumColums), "fields")
                .varargs()
                .addStatement("this.fields = new $T<>($T.asList($L.values()))", TypeName.get(ArrayList.class), TypeName.get(Arrays.class), enumColums)
                .addStatement("this.fields.removeAll($T.asList(fields))", TypeName.get(Arrays.class))
                .addStatement("return this")

                .build())

            .addMethod(MethodSpec.methodBuilder("field")
                .returns(queryBuilderClassName)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(enumColums, "field")
                .addStatement("return this")

                .build())

            .addMethod(MethodSpec.methodBuilder("execute")
                .returns(listObjectsClassName)
                .addModifiers(Modifier.PRIVATE)
                .addStatement("$T db = $T.getInstance().open().getDatabase()", Constants.databaseClassName, Constants.daoClassName)
                .addStatement("$T stringBuilder =  new $T()", Constants.stringBuilderClassName, Constants.stringBuilderClassName)
                .addStatement("stringBuilder.append($S)", "select distinct ")
                .addStatement("if(fields == null) stringBuilder.append(\"$L.* \")", TABLE_NAME)
                .beginControlFlow("else")
                .addStatement("stringBuilder.append($S)", TABLE_NAME + "." + Constants.FIELD_ID)
                .addStatement("final int fieldsSize = fields.size()")
                .beginControlFlow("for(int i=0;i<fieldsSize;++i)")
                .addStatement("$T c = fields.get(i)", enumColums)
                .beginControlFlow("if(c.isPrimitive())")
                .addStatement("stringBuilder.append($S)", ", ")
                .addStatement("stringBuilder.append($S+fields.get(i))", TABLE_NAME + ".")
                .endControlFlow()
                .endControlFlow()
                .endControlFlow()
                .addStatement("stringBuilder.append($S)", String.format(" from %s ", TABLE_NAME))
                .addStatement("stringBuilder.append(constructQuery())")
                .addStatement("$T query = stringBuilder.toString()", ClassName.get(String.class))
                .addStatement("String[] args = constructArgs()")
                .addStatement("if(logger != null) logger.onQuery(query,args)")
                .addStatement("$T cursor = db.rawQuery(query, args)", Constants.cursorClassName)
                .addStatement("$T objects = $T.get(cursor,db)", listObjectsClassName, modelCursorHelperClassName)
                .addStatement("cursor.close()")
                .addStatement("$T.getInstance().close()", Constants.daoClassName)
                .addStatement("return objects")
                .build())

            .build();

        this.dao = TypeSpec.classBuilder(ProcessUtils.getModelDaoName(modelName)) //UserDAO
            .addModifiers(Modifier.PUBLIC)

            .addField(ClassName.get(Constants.DAO_PACKAGE, Constants.QUERY_LOGGER), "logger")

            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).build())

            .addMethod(MethodSpec.methodBuilder("create")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ArrayTypeName.get(String[].class))

                //for
                .addStatement("return new $T[]{$L}", ClassName.get(String.class), generateCreationString())
                .build())

            .addMethod(MethodSpec.methodBuilder("update")
                .returns(ClassName.get(String.class))
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addStatement("return $S", "")
                .build())

            //.addMethod(MethodSpec.methodBuilder("drop")
            //        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            //        .returns(ArrayTypeName.get(String[].class))

            //                //for
            //        .addStatement("return new $T[]{$L}", ClassName.get(String.class), generateDropString())
            //        .build())

            .addMethod(MethodSpec.methodBuilder("select")
                .addModifiers(Modifier.PUBLIC)
                .returns(queryBuilderClassName)
                .addStatement("return new $T(false,logger)", queryBuilderClassName)
                .build())

            .addMethod(MethodSpec.methodBuilder("where")
                .addModifiers(Modifier.PUBLIC)
                .addModifiers(Modifier.STATIC)
                .returns(queryBuilderClassName)
                .addStatement("return new $T(true,null)", queryBuilderClassName)
                .build())

            .addMethod(MethodSpec.methodBuilder("add")
                .addParameter(modelClassName, "object")
                .returns(TypeName.LONG)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("$T database = $T.getInstance().open().getDatabase()", Constants.databaseClassName, Constants.daoClassName)
                .addStatement("long objectId = $T.insert(database,object)", modelCursorHelperClassName)
                .addStatement("$T.getInstance().close()", Constants.daoClassName)
                .addStatement("return objectId")
                .build())

            .addMethod(MethodSpec.methodBuilder("add")
                .addParameter(ProcessUtils.listOf(modelClassName), "objects")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("$T database = $T.getInstance().open().getDatabase()", Constants.databaseClassName, Constants.daoClassName)
                .addStatement("database.beginTransaction()")
                .addStatement("for($T object : objects) $T.insert(database, object)", modelClassName, modelCursorHelperClassName)
                .addStatement("database.setTransactionSuccessful()")
                .addStatement("database.endTransaction()")
                .addStatement("$T.getInstance().close()", Constants.daoClassName)
                .build())

            .addMethod(MethodSpec.methodBuilder("addAsync")
                .addParameter(modelClassName, "object", Modifier.FINAL)
                .addParameter(ParameterizedTypeName.get(Constants.callback, modelClassName), "callback")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("final $T<$T<$T>> weakReference = new WeakReference<>(callback)", ClassName.get(WeakReference.class), Constants.callback, modelClassName)
                .addStatement("final $T current = new Handler($T.myLooper())", ClassName.get("android.os", "Handler"), ClassName.get("android.os", "Looper"))
                .addCode("new Handler().post(new $T() {\n", ClassName.get(Runnable.class))
                .addCode("@$T public void run() {\n", ClassName.get(Override.class))
                .addStatement("add(object)")
                .addCode("current.post(new Runnable() {\n")
                .addCode("@Override public void run() {\n")
                .addStatement("$T callback1 = weakReference.get()", ParameterizedTypeName.get(Constants.callback, modelClassName))
                .addStatement("if(callback1 != null) callback1.onSuccess(object)")
                .addCode("}\n")
                .addCode("});\n")
                .addCode("}\n")
                .addCode("});\n")
                .build())

            .addMethod(MethodSpec.methodBuilder("addAsync")
                .addParameter(modelClassName, "object", Modifier.FINAL)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("addAsync(object, null)")
                .build())

            .addMethod(MethodSpec.methodBuilder("addAsync")
                .addParameter(ProcessUtils.listOf(modelClassName), "objects", Modifier.FINAL)
                .addParameter(ParameterizedTypeName.get(Constants.callback, ProcessUtils.listOf(modelClassName)), "callback")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("final $T weakReference = new WeakReference<>(callback)",
                    ParameterizedTypeName.get(ClassName.get(WeakReference.class), ParameterizedTypeName.get(Constants.callback, ProcessUtils.listOf(modelClassName))))
                .addStatement("final $T current = new Handler($T.myLooper())", ClassName.get("android.os", "Handler"), ClassName.get("android.os", "Looper"))
                .addCode("new Handler().post(new $T() {\n", ClassName.get(Runnable.class))
                .addCode("@$T public void run() {\n", ClassName.get(Override.class))
                .addStatement("add(objects)")
                .addCode("current.post(new Runnable() {\n")
                .addCode("@Override public void run() {\n")
                .addStatement("$T callback1 = weakReference.get()", ParameterizedTypeName.get(Constants.callback, ProcessUtils.listOf(modelClassName)))
                .addStatement("if(callback1 != null) callback1.onSuccess(objects)")
                .addCode("}\n")
                .addCode("});\n")
                .addCode("}\n")
                .addCode("});\n")
                .build())

            .addMethod(MethodSpec.methodBuilder("addAsync")
                .addParameter(ProcessUtils.listOf(modelClassName), "objects", Modifier.FINAL)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("addAsync(objects, null)")
                .build())

            .addMethod(MethodSpec.methodBuilder("update")
                .addParameter(modelClassName, "object")
                .returns(TypeName.LONG)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("$T database = $T.getInstance().open().getDatabase()", Constants.databaseClassName, Constants.daoClassName)
                .addStatement("long objectId = $T.update(database,object)", modelCursorHelperClassName)
                .addStatement("$T.getInstance().close()", Constants.daoClassName)
                .addStatement("return objectId")
                .build())

            .addMethod(MethodSpec.methodBuilder("update")
                .addParameter(ProcessUtils.listOf(modelClassName), "objects")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("$T database = $T.getInstance().open().getDatabase()", Constants.databaseClassName, Constants.daoClassName)
                .addStatement("database.beginTransaction()")
                .addStatement("for($T object : objects) $T.update(database, object)", modelClassName, modelCursorHelperClassName)
                .addStatement("database.setTransactionSuccessful()")
                .addStatement("database.endTransaction()")
                .addStatement("$T.getInstance().close()", Constants.daoClassName)
                .build())

            .addMethod(MethodSpec.methodBuilder("updateAsync")
                .addParameter(modelClassName, "object", Modifier.FINAL)
                .addParameter(ParameterizedTypeName.get(Constants.callback, modelClassName), "callback")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("final $T<$T<$T>> weakReference = new WeakReference<>(callback)", ClassName.get(WeakReference.class), Constants.callback, modelClassName)
                .addStatement("final $T current = new Handler($T.myLooper())", ClassName.get("android.os", "Handler"), ClassName.get("android.os", "Looper"))
                .addCode("new Handler().post(new $T() {\n", ClassName.get(Runnable.class))
                .addCode("@$T public void run() {\n", ClassName.get(Override.class))
                .addStatement("update(object)")
                .addCode("current.post(new Runnable() {\n")
                .addCode("@Override public void run() {\n")
                .addStatement("$T callback1 = weakReference.get()", ParameterizedTypeName.get(Constants.callback, modelClassName))
                .addStatement("if(callback1 != null) callback1.onSuccess(object)")
                .addCode("}\n")
                .addCode("});\n")
                .addCode("}\n")
                .addCode("});\n")
                .build())

            .addMethod(MethodSpec.methodBuilder("updateAsync")
                .addParameter(modelClassName, "object", Modifier.FINAL)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("updateAsync(object, null)")
                .build())

            .addMethod(MethodSpec.methodBuilder("updateAsync")
                .addParameter(ProcessUtils.listOf(modelClassName), "objects", Modifier.FINAL)
                .addParameter(ParameterizedTypeName.get(Constants.callback, ProcessUtils.listOf(modelClassName)), "callback")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("final $T weakReference = new WeakReference<>(callback)",
                    ParameterizedTypeName.get(ClassName.get(WeakReference.class), ParameterizedTypeName.get(Constants.callback, ProcessUtils.listOf(modelClassName))))
                .addStatement("final $T current = new Handler($T.myLooper())", ClassName.get("android.os", "Handler"), ClassName.get("android.os", "Looper"))
                .addCode("new Handler().post(new $T() {\n", ClassName.get(Runnable.class))
                .addCode("@$T public void run() {\n", ClassName.get(Override.class))
                .addStatement("update(objects)")
                .addCode("current.post(new Runnable() {\n")
                .addCode("@Override public void run() {\n")
                .addStatement("$T callback1 = weakReference.get()", ParameterizedTypeName.get(Constants.callback, ProcessUtils.listOf(modelClassName)))
                .addStatement("if(callback1 != null) callback1.onSuccess(objects)")
                .addCode("}\n")
                .addCode("});\n")
                .addCode("}\n")
                .addCode("});\n")
                .build())

            .addMethod(MethodSpec.methodBuilder("updateAsync")
                .addParameter(ProcessUtils.listOf(modelClassName), "objects", Modifier.FINAL)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("updateAsync(objects, null)")
                .build())

            .addMethod(MethodSpec.methodBuilder("delete")
                .addParameter(Constants.databaseClassName, "db")
                .addParameter(modelClassName, "object")
                .addModifiers(Modifier.PRIVATE)
                .returns(TypeName.VOID)
                .addStatement(ProcessUtils.getModelId(element, "object", "id"))
                .addStatement("db.delete($S, $S, new String[]{String.valueOf(id)})", TABLE_NAME, "_id = ?")
                .build())

            .addMethod(MethodSpec.methodBuilder("exists")
                    .addParameter(Constants.databaseClassName, "db")
                    .addParameter(modelClassName, "object")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(TypeName.BOOLEAN)
                    .addStatement(ProcessUtils.getModelId(element, "object", "id"))
                    .addStatement("$T.getInstance().close()", Constants.daoClassName)
                    .addStatement("return id != null")
                    .build())

            .addMethod(MethodSpec.methodBuilder("delete")
                .addParameter(modelClassName, "object")
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.VOID)
                .addStatement("$T db = $T.getInstance().open().getDatabase()", Constants.databaseClassName, Constants.daoClassName)
                .addStatement("delete(db,object)")
                .addStatement("$T.getInstance().close()", Constants.daoClassName)
                .build())

            .addMethod(MethodSpec.methodBuilder("delete")
                .addParameter(listObjectsClassName, "objects")
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.VOID)
                .addStatement("$T db = $T.getInstance().open().getDatabase()", Constants.databaseClassName, Constants.daoClassName)
                .beginControlFlow("for($T object : objects)", modelClassName)
                .addStatement("delete(db,object)")
                .endControlFlow()
                .addStatement("$T.getInstance().close()", Constants.daoClassName)
                .build())

            .addMethod(MethodSpec.methodBuilder("deleteAll")
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.VOID)
                .addStatement("$T.getInstance().open().getDatabase().execSQL($S)", Constants.daoClassName, "delete from " + TABLE_NAME)
                .addStatement("$T.getInstance().close()", Constants.daoClassName)
                .build())

            .addMethod(MethodSpec.methodBuilder("deleteAllAsync")
                .addParameter(modelClassName, "object", Modifier.FINAL)
                .addParameter(ParameterizedTypeName.get(Constants.callback, modelClassName), "callback")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("final $T<$T<$T>> weakReference = new WeakReference<>(callback)", ClassName.get(WeakReference.class), Constants.callback, modelClassName)
                .addStatement("final $T current = new Handler($T.myLooper())", ClassName.get("android.os", "Handler"), ClassName.get("android.os", "Looper"))
                .addCode("new Handler().post(new $T() {\n", ClassName.get(Runnable.class))
                .addCode("@$T public void run() {\n", ClassName.get(Override.class))
                .addStatement("deleteAll()")
                .addCode("current.post(new Runnable() {\n")
                .addCode("@Override public void run() {\n")
                .addStatement("$T callback1 = weakReference.get()", ParameterizedTypeName.get(Constants.callback, modelClassName))
                .addStatement("if(callback1 != null) callback1.onSuccess(null)")
                .addCode("}\n")
                .addCode("});\n")
                .addCode("}\n")
                .addCode("});\n")
                .build())

            .addMethod(MethodSpec.methodBuilder("deleteAsync")
                .addParameter(modelClassName, "object", Modifier.FINAL)
                .addParameter(ParameterizedTypeName.get(Constants.callback, modelClassName), "callback")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("final $T<$T<$T>> weakReference = new WeakReference<>(callback)", ClassName.get(WeakReference.class), Constants.callback, modelClassName)
                .addStatement("final $T current = new Handler($T.myLooper())", ClassName.get("android.os", "Handler"), ClassName.get("android.os", "Looper"))
                .addCode("new Handler().post(new $T() {\n", ClassName.get(Runnable.class))
                .addCode("@$T public void run() {\n", ClassName.get(Override.class))
                .addStatement("delete(object)")
                .addCode("current.post(new Runnable() {\n")
                .addCode("@Override public void run() {\n")
                .addStatement("$T callback1 = weakReference.get()", ParameterizedTypeName.get(Constants.callback, modelClassName))
                .addStatement("if(callback1 != null) callback1.onSuccess(object)")
                .addCode("}\n")
                .addCode("});\n")
                .addCode("}\n")
                .addCode("});\n")
                .build())

            .addMethod(MethodSpec.methodBuilder("deleteAsync")
                .addParameter(modelClassName, "object", Modifier.FINAL)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("deleteAsync(object, null)")
                .build())

            .addMethod(MethodSpec.methodBuilder("deleteAsync")
                .addParameter(ProcessUtils.listOf(modelClassName), "objects", Modifier.FINAL)
                .addParameter(ParameterizedTypeName.get(Constants.callback, ProcessUtils.listOf(modelClassName)), "callback")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("final $T weakReference = new WeakReference<>(callback)",
                    ParameterizedTypeName.get(ClassName.get(WeakReference.class), ParameterizedTypeName.get(Constants.callback, ProcessUtils.listOf(modelClassName))))
                .addStatement("final $T current = new Handler($T.myLooper())", ClassName.get("android.os", "Handler"), ClassName.get("android.os", "Looper"))
                .addCode("new Handler().post(new $T() {\n", ClassName.get(Runnable.class))
                .addCode("@$T public void run() {\n", ClassName.get(Override.class))
                .addStatement("delete(objects)")
                .addCode("current.post(new Runnable() {\n")
                .addCode("@Override public void run() {\n")
                .addStatement("$T callback1 = weakReference.get()", ParameterizedTypeName.get(Constants.callback, ProcessUtils.listOf(modelClassName)))
                .addStatement("if(callback1 != null) callback1.onSuccess(objects)")
                .addCode("}\n")
                .addCode("});\n")
                .addCode("}\n")
                .addCode("});\n")
                .build())

            .addMethod(MethodSpec.methodBuilder("deleteAsync")
                .addParameter(ProcessUtils.listOf(modelClassName), "objects", Modifier.FINAL)
                .addModifiers(Modifier.PUBLIC)
                .addStatement("deleteAsync(objects, null)")
                .build())

            .addMethod(MethodSpec.methodBuilder("count")
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.INT)
                .addStatement("$T db = $T.getInstance().open().getDatabase()", Constants.databaseClassName, Constants.daoClassName)
                .addStatement("$T cursor = db.rawQuery($S,null)", Constants.cursorClassName, "select count(*) from " + TABLE_NAME)
                .addStatement("cursor.moveToFirst()")
                .addStatement("int recCount = cursor.getInt(0)")
                .addStatement("cursor.close()")
                .addStatement("$T.getInstance().close()", Constants.daoClassName)
                .addStatement("return recCount")
                .build())

            .addMethod(MethodSpec.methodBuilder("logQueries")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get(Constants.DAO_PACKAGE, Constants.QUERY_LOGGER), "logger")
                .addStatement("this.logger = logger")
                .build())

            .build();

        return this;
    }

    protected List<MethodSpec> generateQueryMethods() {
        List<MethodSpec> methodSpecs = new ArrayList<>();

        for (VariableElement variableElement : fields) {
            ClassName className = ProcessUtils.getSelectorName(variableElement);
            if (className != null) {
                TypeName selector = null;
                if (className == Constants.queryBuilder_NumberSelectorClassName) {
                    selector = ParameterizedTypeName.get(className, queryBuilderClassName, ProcessUtils.getUnboxedClass(variableElement));
                } else {
                    selector = ParameterizedTypeName.get(className, queryBuilderClassName);
                }

                methodSpecs.add(MethodSpec.methodBuilder(variableElement.getSimpleName().toString())
                    .returns(selector)
                    .addModifiers(Modifier.PUBLIC)

                    .addStatement("return new $T(this, $T.$L.getName())", selector, enumColums, variableElement.getSimpleName())

                    .build());
            }
        }

        for (VariableElement variableElement : collections) {
            ClassName className = ProcessUtils.getSelectorName(variableElement);
            if (className != null) {
                TypeName selector = null;

                if (className == Constants.queryBuilder_ListNumberSelectorClassName) {
                    selector = ParameterizedTypeName.get(className, queryBuilderClassName, ProcessUtils.getUnboxedClass(variableElement));
                } else {
                    selector = ParameterizedTypeName.get(className, queryBuilderClassName);
                }

                methodSpecs.add(MethodSpec.methodBuilder(variableElement.getSimpleName().toString())
                    .returns(selector)
                    .addModifiers(Modifier.PUBLIC)

                    .addStatement("return new $T(this, $T.$L.getName())", selector, enumColums, variableElement.getSimpleName())

                    .build());
            }
        }

        for (VariableElement variableElement : otherClassFields) {

            String JOINTABLE = ProcessUtils.getTableName(modelName) + "_" + ProcessUtils.getTableName(variableElement);

            methodSpecs.add(MethodSpec.methodBuilder(variableElement.getSimpleName().toString())
                .returns(queryBuilderClassName)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ProcessUtils.getFieldQueryBuilderClass(variableElement), "query")
                .addStatement("queryBuilder.append('(').append(query.query($S,getTableId($S),$S,$S,getTableId($S),$S,args)).append(')')", TABLE_NAME, JOINTABLE, ProcessUtils.getKeyName(modelName), ProcessUtils.getKeyName(variableElement), ProcessUtils.getTableName(variableElement), ProcessUtils.getObjectName(variableElement))
                .addStatement("return this")
                .build());
        }

        return methodSpecs;
    }

    protected String generateCreationString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append('"').append("create table ").append(TABLE_NAME).append(" (_id integer primary key autoincrement, ").append(generateTableCreate()).append(")").append('"');

        Set<String> addedTables = new HashSet<>();

        for (VariableElement variableElement : otherClassFields) {
            String table = TABLE_NAME + "_" + ProcessUtils.getTableName(variableElement);
            if (!addedTables.contains(table)) {
                stringBuilder
                    .append(",\n")
                    .append('"')
                    .append("create table ").append(table)
                    .append(" ( _id integer primary key autoincrement, ")
                    .append(ProcessUtils.getKeyName(modelName)).append(" integer, ")
                    .append(ProcessUtils.getKeyName(variableElement)).append(" integer, ")
                    .append(Constants.FIELD_NAME).append(" text )")
                    .append('"');
                addedTables.add(table);
            }
        }

        return stringBuilder.toString();
    }

    //protected String generateDropString() {
    //        StringBuilder stringBuilder = new StringBuilder();
    //        stringBuilder.append('"').append("drop table ").append(TABLE_NAME).append(")").append('"');

    //        Set<String> dropTables = new HashSet<>();

    //        for (VariableElement variableElement : otherClassFields) {
    //                String table = TABLE_NAME + "_" + ProcessUtils.getTableName(variableElement);
    //                if (!dropTables.contains(table)) {
    //                        stringBuilder
    //                                .append(",\n")
    //                                .append('"')
    //                                .append("drop table ").append(table)
    //                                .append('"');
    //                        dropTables.add(table);
    //                }
    //        }

    //        return stringBuilder.toString();
    //}

    protected String generateTableCreate() {
        StringBuilder stringBuilder = new StringBuilder();

        //filter / remove fieldId
        List<Element> elements = new ArrayList<>();
        for (VariableElement variableElement : fields) {
            if (variableElement != fieldId) {
                elements.add(variableElement);
            }
        }

        for (int i = 0; i < elements.size(); ++i) {
            Element variableElement = elements.get(i);
            if (!Constants.FIELD_ID.equals(variableElement.getSimpleName().toString())) {
                stringBuilder
                    .append(variableElement.getSimpleName())
                    .append(" ")
                    .append(ProcessUtils.getFieldTableType(variableElement));
                if (i < elements.size() - 1) {
                    stringBuilder.append(", ");
                }
            }
        }
        return stringBuilder.toString();
    }
}
