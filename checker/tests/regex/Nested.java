import org.checkerframework.checker.regex.qual.Regex;

public class Nested {

    // :: warning: (cast.unsafe.constructor.invocation)
    OuterI.@Regex InnerA fa = new OuterI.@Regex InnerA() {};

    // :: warning: (cast.unsafe.constructor.invocation)
    OuterI.@Regex InnerB<Object> fb = new OuterI.@Regex InnerB<Object>() {};
}

class OuterI {
    static class InnerA {}

    static class InnerB<T> {}
}
