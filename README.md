# Freezer

[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-Freezer-brightgreen.svg?style=flat)](http://android-arsenal.com/details/1/3080)
[![CircleCI](https://circleci.com/gh/florent37/Freezer.svg?style=svg)](https://circleci.com/gh/florent37/Freezer)


<a href="https://goo.gl/WXW8Dc">
  <img alt="Android app on Google Play" src="https://developer.android.com/images/brand/en_app_rgb_wo_45.png" />
</a>


A simple & fluent Android ORM, how can it be easier ?
And it's compatible with RxJava2 !

```java
UserEntityManager userEntityManager = new UserEntityManager();

userEntityManager.add(new User("Florent", 6));
userEntityManager.add(new User("Florian", 3));
userEntityManager.add(new User("Bastien", 3));

List<User> allUsers = userEntityManager.select()
                             .name().startsWith("Flo")
                             .asList();

userEntityManager.select()
       .age().equals(3)
       .asObservable()

       .subscribeOn(Schedulers.newThread())
       .observeOn(AndroidSchedulers.mainThread())
       .subscribe(users ->
          //display the users
       );
```

# First, initialize !

Don't forget to initialise Freezer in your application:

```java
public class MyApplication extends Application {

    @Override public void onCreate() {
        super.onCreate();
        Freezer.onCreate(this);
    }

}
```

# Second, annotate your models

Use Annotations to mark classes to be persisted:

```java
@Model
public class User {
    int age;
    String name;
    Cat cat;
    List<Cat> pets;
}
```

```java
@Model
public class Cat {
    @Id long id;
    String name;
}
```

# Now, play with the managers !

## Persist datas

Persist your data easily:

```java
UserEntityManager userEntityManager = new UserEntityManager();

User user = ... // Create a new object
userEntityManager.add(user);
```

## Querying

Freezer query engine uses a fluent interface to construct multi-clause queries.

### Simple

To find all users:
```java  
List<User> allUsers = userEntityManager.select()
                             .asList();
```
                                                  
To find the first user who is 3 years old:             
```java                              
User user3 = userEntityManager.select()
                    .age().equalsTo(3)
                    .first();
```

### Complex

To find all users 
- with `name` "Florent"
- or who own a pet with `named` "Java" 
    
you would write:             
```java  
List<User> allUsers = userEntityManager.select()
                                .name().equalsTo("Florent")
                             .or()
                                .cat(CatEntityManager.where().name().equalsTo("Java"))
                             .or()
                                .pets(CatEntityManager.where().name().equalsTo("Sasha"))
                             .asList();
```

### Selectors

```java
//strings
     .name().equalsTo("florent")
     .name().notEqualsTo("kevin")
     .name().contains("flo")
     .name().in("flo","alex","logan")
//numbers
     .age().equalsTo(10)
     .age().notEqualsTo(30)
     .age().greatherThan(5)
     .age().between(10,20)
     .age().in(10,13,16)
//booleans
     .hacker().equalsTo(true)
     .hacker().isTrue()
     .hacker().isFalse()
//dates
     .myDate().equalsTo(OTHER_DATE)
     .myDate().notEqualsTo(OTHER_DATE)
     .myDate().before(OTHER_DATE)
     .myDate().after(OTHER_DATE)
```

### Aggregation

The `QueryBuilder` offers various aggregation methods:

```java
float agesSum      = userEntityManager.select().sum(UserColumns.age);
float agesAverage  = userEntityManager.select().average(UserColumns.age);
float ageMin       = userEntityManager.select().min(UserColumns.age);
float ageMax       = userEntityManager.select().max(UserColumns.age);
int count          = userEntityManager.select().count();
```

### Limit

The `QueryBuilder` offers a limitation method, for example, getting 10 users, starting from the 5th:

```java
List<User> someUsers = userEntityManager.select()
                                .limit(5, 10) //start, count
                                .asList();
```

## Asynchronous

Freezer offers various asynchronous methods:

### Add / Delete / Update

```java
userEntityManager
                .addAsync(users)
                .async(new SimpleCallback<List<User>>() {
                    @Override
                    public void onSuccess(List<User> data) {

                    }
                });
```

### Querying

```java
userEntityManager
                .select()
                ...
                .async(new SimpleCallback<List<User>>() {
                    @Override
                    public void onSuccess(List<User> data) {

                    }
                });
```

### Observables

[With RxJava](https://github.com/ReactiveX/RxJava)

```java
userEntityManager
                .select()
                ...
                .asObservable()
                ... //rx operations
                .subscribe(new Action1<List<User>>() {
                    @Override
                    public void call(List<User> users) {
                    
                    }
                });
```

## Entities

Freezer makes it possible, yes you can design your entities as your wish:

```java
@Model
public class MyEntity {

    // primitives
    [ int / float / boolean / String / long / double ] field;
    
    //dates
    Date myDate;

    // arrays
    [ int[] / float[] / boolean[] / String[] / long[] / double ] array; 
    
    // collections
    [ List<Integer> / List<Float> / List<Boolean> / List<String> / List<Long> / List<Double> ] collection;
    
    // One To One
    MySecondEntity child;
    
    // One To Many
    List<MySecondEntity> childs;
}
```

## Update

You can update a model:

```java
user.setName("laurent");
userEntityManager.update(user);
```

## Id

You can optionnaly set a field as an identifier:

```java
@Model
public class MyEntity {
    @Id long id;
}
```
The identifier must be a `long`

## Ignore

You can ignore a field:

```java
@Model
public class MyEntity {
    @Ignore
    int field;    
}
```


## Logging

You can log all SQL queries from entities managers:

```java
userEntityManager.logQueries((query, datas) -> Log.d(TAG, query) }
```

## Migration

To handle schema migration, just add `@Migration(newVersion)` in a static method,
then describe the modifications:

```java
public class DatabaseMigration {

    @Migration(2)
    public static void migrateTo2(Migrator migrator) {
        migrator.update("User")
                .removeField("age")
                .renameTo("Man");
    }

    @Migration(3)
    public static void migrateTo3(Migrator migrator) {
        migrator.update("Man")
                .addField("birth", ColumnType.Primitive.Int);
    }
    
    @Migration(4)
    public static void migrateTo4(Migrator migrator) {
        migrator.addTable(migrator.createModel("Woman")
                .field("name", ColumnType.Primitive.String)
                .build());
    }
}
```

Migration isn't yet capable of:
- changing type of field
- adding/modifying One To One
- adding/modifying One To Many
- handling collections/arrays

# Download


<a href="https://goo.gl/WXW8Dc">
  <img alt="Android app on Google Play" src="https://developer.android.com/images/brand/en_app_rgb_wo_45.png" />
</a>


<a href='https://ko-fi.com/A160LCC' target='_blank'><img height='36' style='border:0px;height:36px;' src='https://az743702.vo.msecnd.net/cdn/kofi1.png?v=0' border='0' alt='Buy Me a Coffee at ko-fi.com' /></a>

[ ![Download](https://api.bintray.com/packages/florent37/maven/freezer-compiler/images/download.svg) ](https://bintray.com/florent37/maven/freezer-compiler/_latestVersion)
```java
buildscript {
  dependencies {
    classpath 'com.neenbedankt.gradle.plugins:android-apt:1.8'
  }
}

apply plugin: 'com.neenbedankt.android-apt'

dependencies {
  compile 'fr.xebia.android.freezer:freezer:2.0.6'
  provided 'fr.xebia.android.freezer:freezer-annotations:2.0.6'
  apt 'fr.xebia.android.freezer:freezer-compiler:2.0.6'
}
```

# Changelog

## 1.0.1

Introduced Migration Engine.

## 1.0.2

- Support long & double
- Support arrays
- Improved QueryBuilder
- Refactored cursors helpers

## 1.0.3

- Support dates
- Added unit tests
- Fixed one to many

## 1.0.4

- Added @Id & @Ignore

## 1.0.5

- Model update

## 2.0.0

- Async API
- Support Observables
- Added @DatabaseName

## 2.0.1

- Limit

## 2.0.2

- Added query.in(...values...)

## 2.0.3

- Freezer.onCreate is no longer dynamic

## 2.0.5

- Improved performace for batch add & update (thanks to graphee-gabriel)

## 2.0.6

- Add or update object if same `@Id on `add, addAll`

## 2.1.0

- Added RxJava2 support

# A project initiated by Xebia

This project was first developed by Xebia and has been open-sourced since. We will continue working on it.
We encourage the community to contribute to the project by opening tickets and/or pull requests.

[![logo xebia](https://raw.githubusercontent.com/florent37/Freezer/master/logo_xebia.jpg)](http://www.xebia.fr/)

<a href="https://play.google.com/store/apps/details?id=com.github.florent37.florent.champigny">
  <img alt="Android app on Google Play" src="https://developer.android.com/images/brand/en_app_rgb_wo_45.png" />
</a>


<a href="https://goo.gl/WXW8Dc">
  <img alt="Android app on Google Play" src="https://developer.android.com/images/brand/en_app_rgb_wo_45.png" />
</a>


License
--------

    Copyright 2015 Xebia, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

