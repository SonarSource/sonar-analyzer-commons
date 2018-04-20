function test() { // Noncompliant

    var msg = "Hello";

    alert(msg); // Noncompliant

    alert("Hello");// Noncompliant {{Rule message}}

    alert(msg); // Noncompliant
  //      ^^^

}
