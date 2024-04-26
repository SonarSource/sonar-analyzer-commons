class NoQuickFixes{
  void foo() {
    "foo".equals("bar"); // Noncompliant [[sc=18;ec=23;]]
  }
}
