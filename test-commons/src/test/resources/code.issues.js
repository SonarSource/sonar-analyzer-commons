code.js
004: // Noncompliant 2
004: function test() {

009: // Noncompliant
006:     var msg = "Hello";
             ^^^>
009:     alert(msg);
               ^^^ 1


012: // Noncompliant {{Rule message}}
012:     alert("Hello");
         ^^^^^ 2
              ^^^^^^^^^< {{Secondary location message1}}
016:     alert("Hello");
         ^^^^^< {{Secondary location message2}}


022: // Noncompliant
019:     int size = 0;
             ^^^^> 1.1
022:     alert("Hello");
         ^^^^^ 2
025:     if (size) {
             ^^^^< 1.2


029: // Noncompliant
029:     alert("Hello");
         ^^^^^ 1^^^^^<
