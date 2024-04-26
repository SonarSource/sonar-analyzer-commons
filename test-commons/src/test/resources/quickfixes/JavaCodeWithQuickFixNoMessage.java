class QuickFixes{
  void foo() {
    "foo".equals("bar"); // Noncompliant [[sc=18;ec=23;quickfixes=qf1]]
    // fix@qf1
    // edit@qf1 [[sc=18;ec=23]] {{"foo"}}
    // edit@qf1 [[sc=5;ec=10]] {{"bar"}}
  }
}
