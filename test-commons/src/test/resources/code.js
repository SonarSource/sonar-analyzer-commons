// ignored comment
/* multiline comment */

function test() { // Noncompliant 2

    var msg = "Hello";
    //  ^^^>

    alert(msg); // Noncompliant
    //    ^^^ 1

    alert("Hello");// Noncompliant {{Rule message}}
 // ^^^^^ 2
 //      ^^^^^^^^^@-1< {{Secondary location message1}}

    alert("Hello");
 // ^^^^^< {{Secondary location message2}}

    var size = 0;
 //     ^^^^> 1.1

    alert("Hello"); // Noncompliant
 // ^^^^^

    if (size) {
 //     ^^^^< 1.2
    }

    alert("Hello"); // Noncompliant
 // ^^^^^  ^^^^^<

}

// Noncompliant@0 {{Issue on file}}
