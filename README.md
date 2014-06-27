#### EXPERIMENTAL / DRAFT

# Play Autosource

## An automatic full REST + Typesafe CRUD abstract Datasource for bootstrapping a Play Framework App

Implementations available for:
- [ReactiveMongo](http://www.reactivemongo.org) ( _play-autosource:reactivemongo:2.0-SNAPSHOT_ )
- [Couchbase](http://www.couchbase.com) based on Mathieu Ancelin's [ReactiveCouchbase](http://reactivecouchbase.org/) ( _play-autosource:couchbase:2.0-SNAPSHOT_ )
- [Datomic](http://www.datomic.com) based on [Pellucid](http://www.pellucidanalytics.com)'s [Datomisca](http://pellucidanalytics.github.io/datomisca/) ( _play-autosource:datomisca:2.0_ )
- [Slick/JDBC](http://slick.typesafe.com/) based on [Play2-Slick](https://github.com/freekh/play-slick) (thanks to [Loic Descotte](https://github.com/loicdescotte) and [Renato Cavalcanti](https://github.com/rcavalcanti)) ( _play-autosource:slick:2.0-SNAPSHOT_ )

<br/>

# 2'30 tutorial

Here we go:

### 0' : Create App

```scala
> play2 new auto-persons
       _            _
 _ __ | | __ _ _  _| |
| '_ \| |/ _' | || |_|
|  __/|_|\____|\__ (_)
|_|            |__/

play! 2.1.1 (using Java 1.7.0_21 and Scala 2.10.0), http://www.playframework.org

The new application will be created in /Users/pvo/zenexity/workspaces/workspace_mandubian/auto-persons

What is the application name? [auto-persons]
>

Which template do you want to use for this new application?

  1             - Create a simple Scala application
  2             - Create a simple Java application

> 1
OK, application auto-persons is created.

Have fun!
```

### 10' : edit project/Build.scala, add `play-autosource:reactivemongo` dependency

```scala
val mandubianRepo = Seq(
  "Mandubian repository snapshots" at "https://github.com/mandubian/mandubian-mvn/raw/master/snapshots/",
  "Mandubian repository releases" at "https://github.com/mandubian/mandubian-mvn/raw/master/releases/"
)

val appDependencies = Seq()

val main = play.Project(appName, appVersion, appDependencies).settings(
  resolvers ++= mandubianRepo,
  libraryDependencies ++= Seq(
    "play-autosource"   %% "reactivemongo"       % "2.0-SNAPSHOT",
    "org.specs2"        %% "specs2"              % "1.13"        % "test",
    "junit"              % "junit"               % "4.8"         % "test"
  )
)
```

### 30' : Create new ReactiveMongo AutoSource Controller in app/Person.scala

```scala
package controllers

import play.api._
import play.api.mvc._

// BORING IMPORTS
// Json
import play.api.libs.json._
import play.api.libs.functional.syntax._
// Reactive JSONCollection
import play.modules.reactivemongo.json.collection.JSONCollection
// Autosource
import play.autosource.reactivemongo._
// AutoSource is Async so imports Scala Future implicits
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.current

// >>> THE IMPORTANT PART <<<
object Persons extends ReactiveMongoAutoSourceController[JsObject] {
  val coll = db.collection[JSONCollection]("persons")
}
```

### 50' : Add AutoSource routes at beginning `conf/routes`

```scala
->      /person                     controllers.Persons
```

### 60' : Create `conf/play.plugins` to initialize ReactiveMongo Plugin

```scala
400:play.modules.reactivemongo.ReactiveMongoPlugin
```

### 70' : Append to `conf/application.conf` to initialize MongoDB connection

```scala
mongodb.uri ="mongodb://localhost:27017/persons"
```

### 80' : Launch application

```scala
> play2 run

[info] Loading project definition from /.../auto-persons/project
[info] Set current project to auto-persons (in build file:/.../auto-persons/)

[info] Updating {file:/.../auto-persons/}auto-persons...
[info] Done updating.
--- (Running the application from SBT, auto-reloading is enabled) ---

[info] play - Listening for HTTP on /0:0:0:0:0:0:0:0:9000

(Server started, use Ctrl+D to stop and go back to the console...)
[info] Compiling 5 Scala sources and 1 Java source to /.../auto-persons/target/scala-2.10/classes...
[warn] there were 2 feature warnings; re-run with -feature for details
[warn] one warning found
[success] Compiled in 6s
```

### 100' : Insert your first 2 persons with Curl

```scala
>curl -X POST -d '{ "name":"bob", "age":25 }' --header "Content-Type:application/json" http://localhost:9000/person --include

HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
Content-Length: 33

{"id":"51b868ef31d4002c0bac8ba4"} -> oh a BSONObjectId

>curl -X POST -d '{ "name":"john", "age":43 }' --header "Content-Type:application/json" http://localhost:9000/person --include

HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
Content-Length: 33

{"id":"51b868fa31d4002c0bac8ba5"}
```

### 110' : Get all persons

```scala
>curl http://localhost:9000/person --include

HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
Content-Length: 118

[
  {"name":"bob","age":25.0,"id":"51b868ef31d4002c0bac8ba4"},
  {"name":"john","age":43.0,"id":"51b868fa31d4002c0bac8ba5"}
]
```


### 115' : Delete one person

```scala
>curl -X DELETE http://localhost:9000/person/51b868ef31d4002c0bac8ba4 --include

HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
Content-Length: 33

{"id":"51b868ef31d4002c0bac8ba4"}
```

### 120' : Verify person was deleted

```scala
>curl -X GET http://localhost:9000/person/51b868ef31d4002c0bac8ba4 --include

HTTP/1.1 404 Not Found
Content-Type: text/plain; charset=utf-8
Content-Length: 37

ID 51b868ef31d4002c0bac8ba4 not found
```

### 125' : Update person

```scala
>curl -X PUT -d '{ "name":"john", "age":35 }' --header "Content-Type:application/json" http://localhost:9000/person/51b868fa31d4002c0bac8ba5 --include

HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
Content-Length: 33

{"id":"51b868fa31d4002c0bac8ba5"}
```

### 130' : Batch insert 2 persons (johnny & tom) with more properties

```scala
>curl -X POST -d '[{ "name":"johnny", "age":15, "address":{"city":"Paris", "street":"rue quincampoix"} },{ "name":"tom", "age":3, "address":{"city":"Trifouilly", "street":"rue des accidents de poucettes"} }]' --header "Content-Type:application/json" http://localhost:9000/person/batch --include

HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
Content-Length: 8

{"nb":2}
```

### 135' : Get all persons whose name begins by "john"

```scala
>curl -X POST -d '{"name":{"$regex":"^john"}}' --header "Content-Type:application/json" http://localhost:9000/person/find --include

HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
Content-Length: 175

[
  {"name":"john","age":35.0,"id":"51b868fa31d4002c0bac8ba5"},
  {"id":"51b86a1931d400bc01ac8ba8","name":"johnny","age":15.0,"address":{"city":"Paris","street":"rue quincampoix"}}
]
```

### 140' : Delete all persons

```scala
>curl -X DELETE -d '{}' --header "Content-Type:application/json" http://localhost:9000/person/batch --include

HTTP/1.1 200 OK
Content-Type: text/plain; charset=utf-8
Content-Length: 7

deleted
```

### 145' : Take 5' rest
<br/>
### 150' : Done

<br/>
<br/>
##So what was demonstrated here?

With Play-Autosource, in a few lines, you obtain :

- **A backed abstract datasource (here implemented for [ReactiveMongo](http://www.reactivemongo.org) but it could be implemented for other DBs)**
- **All CRUD operations are exposed as pure REST services**
- **The datasource is typesafe (here `JsObject` but we'll show later that we can use any type)**

It can be useful to kickstart any application in which you're going to work iteratively on our data models in direct interaction with front-end.

It could also be useful to Frontend developers who need to bootstrap frontend code with Play Framework application backend. With _Autosource_, they don't have to care about modelizing strictly a datasource on server-side and can dig into their client-side code quite quickly.

<br/>
<br/>
## Adding constraints & validation
>Now you tell me: "Hey that's stupid, you store directly `JsObject` but my data are structured and must be validated before inserting them"

Yes you're right so let's add some type constraints on our data:

```scala
object Persons extends ReactiveMongoAutoSourceController[JsObject] {
  val coll = db.collection[JSONCollection]("persons")

  // we validate the received Json as JsObject because the autosource type is JsObject
  // and we add classic validations on types
  override val reader = __.read[JsObject] keepAnd (
    (__ \ "name").read[String] and
    (__ \ "age").read[Int](Reads.min(0) keepAnd Reads.max(117))
  ).tupled
}
```

Try it now:

```scala
curl -X POST -d '{ "nameXXX":"bob", "age":25 }' --header "Content-Type:application/json" http://localhost:9000/person --include

HTTP/1.1 400 Bad Request
Content-Type: application/json; charset=utf-8
Content-Length: 62

{"obj.name":[{"msg":"validate.error.missing-path","args":[]}]}
```

You can add progressively constraints on your data in a few lines. With `AutoSource`, you don't need to determine immediately the exact shape of your models and you can work with `JsObject` directly as long as you need. Sometimes, you'll even discover that you don't even need a structured model and `JsObject` will be enough. (_but I also advise to design a bit things before implementing ;)_)

> Keep in mind that our sample is based on an implementation for _ReactiveMongo_ so using Json is natural. For other DB, other data structure might be more idiomatic...

<br/>
<br/>
## Use typesafe models

>Now you tell me: "Funny but but but `JsObject` is evil because it's not strict enough. I'm a OO developer (maybe abused by ORM gurus when I was young) and my models are case-classes..."

Yes you're right, sometimes, you need more business logic or you want to separate concerns very strictly and your model will be shaped as case-classes.

So let's replace our nice little `JsObject` by a more serious `case class`.

```scala
// the model
case class Person(name: String, age: Int)
object Person{
  // the famous Json Macro which generates at compile-time a Reads[Person] in a one-liner
  implicit val fmt = Json.format[Person]
}

// The autosource... shorter than before
object Persons extends ReactiveMongoAutoSourceController[Person] {
  val coll = db.collection[JSONCollection]("persons")
}
```

Please note that I removed the validations I had introduced before because there are not useful anymore: using Json macros, I created an implicit `Format[Person]` which is used implicitly by AutoSource.

> So, now you can see why I consider AutoSource as a *typesafe datasource*.

<br/>
<br/>
## Let's be front-sexy with AngularJS

You all know that [AngularJS](http://www.angularjs.org/) is the new kid on the block and that you must use it if you want to be sexy nowadays.

I'm already sexy so I must be able to use it without understanding anything to it and that's exactly what I've done: in 30mn without knowing anything about Angular (but a few concepts), I wrote a dumb CRUD front page plugged on my wonderful `AutoSource`.

<br/>
### Client DS in assets/javascripts/persons.js

This is the most important part of this sample: we need to call our CRUD autosource endpoints from angularJS.

We are going to use _Angular resources_ for it even if it's not really the best feature of AngularJS. Anyway, in a few lines, it works pretty well in my raw case.

_(thanks to Paul Dijou for reviewing this code because I repeat I don't know angularJS at all and I wrote this in 20mn without trying to understand anything :D)_

```javascript

 // my.resources (http://kirkbushell.me/angular-js-using-ng-resource-in-a-more-restful-manner/), in order to support PUT updates
var module = angular.module('my.resource', [ 'ngResource' ]);

module.factory('Resource', [ '$resource', function ($resource) {
    return function (url, params, methods) {
        var defaults = {
            update: { method: 'put', isArray: false },
            create: { method: 'post' }
        };

        methods = angular.extend(defaults, methods);

        var resource = $resource(url, params, methods);

        resource.prototype.$save = function () {
            if (!this.id) {
                this.$create();
            }
            else {
                this.$update();
            }
        };

        return resource;
    };
}]);

var app =
  // injects my.resources to support PUT updates
  angular.module("app", ["my.resources"])
  // creates the Person factory backed by our autosource
  // Please remark the url person/:id which will use transparently our CRUD AutoSource endpoints
  .factory('Person', ["$resource", function($resource){
    return $resource('person/:id', { "id" : "@id" }, { update: { method: 'PUT' }});
  }])
  // creates a controller
  .controller("PersonCtrl", ["$scope", "Person", function($scope, Person) {

    $scope.createForm = {};

    // retrieves all persons
    $scope.persons = Person.query();

    // creates a person using createForm and refreshes list
    $scope.create = function() {
      var person = new Person({name: $scope.createForm.name, age: $scope.createForm.age});
      person.$save(function(){
        $scope.createForm = {};
        $scope.persons = Person.query();
      })
    }

    // removes a person and refreshes list
    $scope.remove = function(person) {
      person.$remove(function() {
        $scope.persons = Person.query();
      })
    }

    // updates a person and refreshes list
    $scope.update = function(person) {
      person.$update(function() {
        $scope.persons = Person.query();
      })
    }
}]);
```

### CRUD UI in index.scala.html

Now let's create our CRUD UI page using angular directives. We need to be able to:

- list persons
- update/delete each person
- create new persons

```html
@(message: String)

@main("Welcome to Play 2.1") {

    <div ng-controller="PersonCtrl">
      <!-- create form -->
      <form>
        <label for="name">name:</label><input id="name" ng-model="createForm.name"/>
        <label for="age">age:</label><input id="age" ng-model="createForm.age" type="number"/>
        <button ng-click="create()">Create new person</button>
      </form>
      <hr/>
      <!-- List of persons with update/delete buttons -->
      <table>
        <thead><th>name</th><th>age</th><th>actions</th></thead>
        <tbody>
          <tr ng-repeat="person in persons">
            <td><input ng-model="person.name"/></td>
            <td><input type="number" ng-model="person.age"/></td>
            <td><button ng-click="update(person)">Update</button><button ng-click="remove(person)">Delete</button></td>
          </tr>
        </tbody>
      </table>
    </div>

}
```

### Import Angular in main.scala.html

We need to import angularjs in our application and create angular application using `ng-app`

```html
@(title: String)(content: Html)

<!DOCTYPE html>

<!-- please note the directive ng-app to initialize angular app-->
<html ng-app="app">
    <head>
        <title>@title</title>
        <link rel="stylesheet" media="screen" href="@routes.Assets.at("stylesheets/main.css")">
        <link rel="shortcut icon" type="image/png" href="@routes.Assets.at("images/favicon.png")">
        <script src="@routes.Assets.at("javascripts/jquery-1.9.0.min.js")" type="text/javascript"></script>
        <script src="https://ajax.googleapis.com/ajax/libs/angularjs/1.1.5/angular.min.js"></script>
        <script src="https://ajax.googleapis.com/ajax/libs/angularjs/1.1.5/angular-resource.min.js"></script>

        <script src="@routes.Assets.at("javascripts/persons.js")" type="text/javascript"></script>
    </head>
    <body>
        @content
    </body>
</html>
```

## What else??? Oh yes Security...

>I know what you think: "Uhuh, the poor guy who exposes his DB directly on the network and who is able to delete everything without any security"

Once again, you're right. _(yes I know I love flattery)_

Autosource is by default not secured in any way and actually I don't really care about security because this is your job to secure your exposed APIs and there are so many ways to secure services that I prefer to let you choose the one you want.

Anyway, I'm a nice boy and I'm going to show you how you could secure the `DELETE` endpoint using the authentication action composition sample given in [Play Framework documentation](http://www.playframework.com/documentation/2.1.1/ScalaActionsComposition).

```scala
import play.api.libs.iteratee.Done
import reactivemongo.bson.BSONObjectID

// FAKE USER class to simulate a user extracted from DB.
case class User(name: String)
object User {
  def find(name: String) = Some(User(name))
}

object Persons extends ReactiveMongoAutoSourceController[Person] {
  // The action composite directly copied for PlayFramework doc
  def Authenticated(action: User => EssentialAction): EssentialAction = {
    // Let's define a helper function to retrieve a User
    def getUser(request: RequestHeader): Option[User] = {
      request.session.get("user").flatMap(u => User.find(u))
    }

    // Now let's define the new Action
    EssentialAction { request =>
      getUser(request).map(u => action(u)(request)).getOrElse {
        Done(Unauthorized)
      }
    }
  }

  val coll = db.collection[JSONCollection]("persons")

  // >>> IMPORTANT PART <<<
  // We simply override the delete action
  // If authenticated, we call the original action
  override def delete(id: BSONObjectID) = Authenticated { _ =>
    super.delete(id)
  }

  def index = Action {
    Ok(views.html.index("ok"))
  }

  // the login action which log any user
  def login(name: String) = Action {
    Ok("logged in").withSession("user" -> name)
  }

  // the logout action which log out any user
  def logout = Action {
    Ok("logged out").withNewSession
  }
}
```

Now, you can add routes to handle login and logout actions
```scala
POST    /login/:name                controllers.Persons.login(name: String)
POST    /logout                     controllers.Persons.logout
```

Nothing to complicated here.
If you need to add headers in your responses and params to querystring, it's easy to wrap autosource actions. Please refer to Play Framework doc for more info...

> I won't try it here, the article is already too long but it should work...

<br/>
<br/>
## Play-Autosource is DB agnostic

`Play-Autosource` Core is independent of the DB and provides Reactive (Async/Nonblocking) APIs to fulfill PlayFramework requirements.

Naturally this 1st implementation uses [ReactiveMongo](http://www.reactivemongo.org) which is one of the best sample of DB reactive driver. MongoDB fits very well in this concept too because document records are really compliant to JSON datasources.

But other implementations for other DB can be done and I count on you people to contribute them.

>DB implementation contributions are welcome (Play-Autosource is just _Apache2 licensed_) and AutoSource API are subject to evolutions if they appear to be erroneous.

<br/>
<br/>
## Conclusion

Play-Autosource provides a very fast & lightweight way to create a REST CRUD typesafe datasource in your Play/Scala application. You can begin with blob data such as `JsObject` and then elaborate the model of your data progressively by adding constraints or types to it.

There would be many more things to say about `Play/Autosource`:

- you can also override writers to change output format
- you have some alpha streaming API also
- etc...

There are also lots of features to improve/add because it's still a very draft module.


If you like it and have ideas, don't hesitate to discuss, to contribute, to improve etc...

`curl -X POST -d '{ "coding" : "Have fun"}' http://localhost:9000/developer`

<br/>
<br/>
<br/>
<br/>
