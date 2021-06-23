import org.checkerframework.checker.nullness.qual.*;

import java.lang.ref.WeakReference;

class WeakRef {
    @PolyNull Object @Nullable [] foo(WeakReference<@PolyNull Object[]> lookup) {
        return lookup.get();
    }
}
