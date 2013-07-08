var app = angular.module("app", ["ngResource"])
  .factory('Person', ["$resource", function($resource){
    return $resource(
      'persons/:id',
      { "id" : "@id" },
      {
        query: { method: 'GET', params: { q: "[:find ?e :where [?e :person/name]]"}, isArray: true},
        update: { method: 'PUT' }
      }
    );
  }])
  .controller("PersonCtrl", ["$scope", "Person", function($scope, Person) {

    $scope.createForm = {};
    $scope.persons = Person.query();
    $scope.charactersOut = [ 
      ":person.characters/violent", 
      ":person.characters/weak", 
      ":person.characters/clever", 
      ":person.characters/dumb", 
      ":person.characters/stupid"
    ];
    $scope.charactersIn = [ 
      "person.characters/violent", 
      "person.characters/weak", 
      "person.characters/clever", 
      "person.characters/dumb", 
      "person.characters/stupid"
    ];

    $scope.create = function() {
      var person = new Person({name: $scope.createForm.name, age: $scope.createForm.age, characters: $scope.createForm.characters});
      person.$save(function(){
        $scope.createForm = {};
        $scope.persons = Person.query();
      })
    }

    $scope.remove = function(person) {
      person.$remove(function() {
        $scope.persons = Person.query();
      })
    }

    $scope.update = function(person) {
      for(var i=0; i<person.characters.length; i++){
        var c = person.characters[i];
        person.characters[i] = c.slice(1);
      }
      person.$update(function() {
        $scope.persons = Person.query();
      })
    }
}]);