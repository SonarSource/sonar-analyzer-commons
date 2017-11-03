function test() {
    var x = 0; // Noncompliant {{Primary1}}
//  ^^^
//  ^^^@-1< {{Secondary1}}

    var y = 0; // Noncompliant {{Primary2}}
//  ^^^> {{Secondary2}}
//  ^^^@-1
}
