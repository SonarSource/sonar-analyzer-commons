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

    alert(msg); // Noncompliant {{Error}} [[effortToFix=2.5]]
 // ^^^^^

    alert("Hello"); // Noncompliant
 // ^^^^^  ^^^^^<

 // there's one "tab character" at the beginning of the following two lines
	alert(msg); // Noncompliant
	//    ^^^

}

// Noncompliant@0 {{Issue on file}}
