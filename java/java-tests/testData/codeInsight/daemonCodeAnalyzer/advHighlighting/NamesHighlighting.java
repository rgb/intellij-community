import <symbolName descr="null" type="CLASS_NAME">java.io</symbolName>.*; // highlight on demand import as class name

class <symbolName descr="null" type="CLASS_NAME">a</symbolName> {
 void <symbolName descr="null" type="METHOD_DECLARATION">method</symbolName>() {
   <symbolName descr="null" type="METHOD_CALL">method</symbolName>();

   new <symbolName descr="null" type="CONSTRUCTOR_CALL">Exception</symbolName>();
   new <symbolName descr="null" type="CONSTRUCTOR_CALL">java.lang.Exception</symbolName>();
 }
 <symbolName descr="null" type="CONSTRUCTOR_DECLARATION">a</symbolName>() {
   new <symbolName descr="null" type="CONSTRUCTOR_CALL">a</symbolName>();
 }
 
 /**
   * @see <symbolName descr="null" type="INTERFACE_NAME">itf</symbolName>#<symbolName descr="null" type="METHOD_CALL">method</symbolName>(double)
  */
  static void <symbolName descr="null" type="METHOD_DECLARATION">f</symbolName>() {
   <symbolName descr="null" type="CLASS_NAME">Integer</symbolName>.<symbolName descr="null" type="STATIC_METHOD">parseInt</symbolName>("");
   <symbolName descr="null" type="CLASS_NAME">java.lang.Integer</symbolName>.<symbolName descr="null" type="STATIC_METHOD">parseInt</symbolName>("");
   <symbolName descr="null" type="STATIC_METHOD">f</symbolName>();
 }

 interface <symbolName descr="null" type="INTERFACE_NAME">itf</symbolName>{
   int <symbolName descr="null" type="STATIC_FIELD">CONST</symbolName> = 0;
   /** .
     * @param <symbolName descr="null" type="PARAMETER">d</symbolName> Important param
     */
   void <symbolName descr="null" type="METHOD_DECLARATION">method</symbolName>(double <symbolName descr="null" type="PARAMETER">d</symbolName>);
 }
 void <symbolName descr="null" type="METHOD_DECLARATION">ff</symbolName>(<symbolName descr="null" type="INTERFACE_NAME">Runnable</symbolName> <symbolName descr="null" type="PARAMETER">r</symbolName>) {
   <symbolName descr="null" type="METHOD_CALL">ff</symbolName>(
    new <symbolName descr="null" type="INTERFACE_NAME">java.lang.Runnable</symbolName>()
       {
         public void <symbolName descr="null" type="METHOD_DECLARATION">run</symbolName>() {}
         int <symbolName descr="null" type="INSTANCE_FIELD">instance</symbolName> = 0;
       }
   );

   int <symbolName descr="null" type="LOCAL_VARIABLE">i</symbolName> = <symbolName descr="null" type="CLASS_NAME">java.lang.Integer</symbolName>.<symbolName descr="null" type="STATIC_FIELD">MIN_VALUE</symbolName>;
   int <symbolName descr="null" type="LOCAL_VARIABLE">j</symbolName> = <symbolName descr="null" type="INTERFACE_NAME">itf</symbolName>.<symbolName descr="null" type="STATIC_FIELD">CONST</symbolName>;
 }
}

class <symbolName descr="null" type="CLASS_NAME">NoCtrClass</symbolName> {
  {
    // default constructor call looks like class
    new <symbolName descr="null" type="CLASS_NAME">NoCtrClass</symbolName>();
  }
  void <symbolName descr="null" type="METHOD_DECLARATION">ff</symbolName>(int <symbolName descr="null" type="REASSIGNED_PARAMETER">param</symbolName>) {
    int <symbolName descr="null" type="REASSIGNED_LOCAL_VARIABLE">i</symbolName> = 1;
    <symbolName descr="null" type="REASSIGNED_LOCAL_VARIABLE">i</symbolName> ++;

    <symbolName descr="null" type="REASSIGNED_PARAMETER">param</symbolName> = 0;
  }
}

class <symbolName descr="null" type="CLASS_NAME">Generic</symbolName><<symbolName descr="null" type="TYPE_PARAMETER_NAME">TT</symbolName> extends <symbolName descr="null" type="INTERFACE_NAME">Runnable</symbolName>> {
  <symbolName descr="null" type="TYPE_PARAMETER_NAME">TT</symbolName> <symbolName descr="null" type="INSTANCE_FIELD">field</symbolName>;
}