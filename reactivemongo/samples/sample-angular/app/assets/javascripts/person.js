var app = angular.module("app", ["ngResource"])
  .factory('Person', ["$resource", function($resource){
    return $resource('persons/:id', { "id" : "@id" });
  }])
  .controller("PersonCtrl", ["$scope", "Person", function($scope, Person) {

    $scope.createForm = {};
    $scope.persons = Person.query();

    $scope.create = function() {
      var person = new Person({name: $scope.createForm.name, age: $scope.createForm.age});
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
      person.$save(function() {
        $scope.persons = Person.query();
      })
    }
}]);