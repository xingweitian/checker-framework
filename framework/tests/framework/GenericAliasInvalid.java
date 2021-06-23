import testlib.util.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GenericAliasInvalid {

    public static class SuperSetOne
            extends HashSet<@Odd Map<@Odd List<@Odd String>, @Odd String>> {}

    public void test() {
        // :: error: (assignment.type.incompatible)
        Set<Map<@Odd List<@Odd String>, @Odd String>> t = new SuperSetOne();
    }
}
