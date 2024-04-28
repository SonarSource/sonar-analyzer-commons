class QuickFixes{
  void foo() {
    "foo".equals("bar"); // Noncompliant [[sc=18;ec=23;quickfixes=qf1]]
    // fix@qf1 {{Move "bar" on the left side of .equals}}
    // edit@qf1 [[sc=18;ec=23]] {{"foo\n"}}
    // edit@qf1 [[sc=5;ec=10]] {{"bar"}}

    foo(); // Noncompliant {{Without quickfix}}

  }

}
