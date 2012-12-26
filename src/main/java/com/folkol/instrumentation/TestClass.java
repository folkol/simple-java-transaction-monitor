package com.folkol.instrumentation;

public class TestClass {

    public static void main(String[] adsfkjhagsdjfgajskdhgf) {
        int x = 4;
        TestClassHelper r = new TestClassHelper();
        new TestClassHelper().sayHello();
    }

}

class TestClassHelper {
    int o = 4;

    public void sayHello() {
        int x = 1;
        int y = 2;

        int o = 2;

//        sub(1, 2);
        add(3, 6);

        add(7, 2);
        add(9, 26);




        System.out.println("Hello, I am " + this.getClass().getSimpleName() + " And my add is: " + add(x, y));
    }

    public int add(int x, int y) {
        int j;
        return x + y + o;
    }

    int sub(int x, int y) {
        o = 4;
        return 666;
    }
}