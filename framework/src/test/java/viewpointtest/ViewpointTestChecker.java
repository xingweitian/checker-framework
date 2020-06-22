package viewpointtest;

import org.checkerframework.common.basetype.BaseTypeChecker;

public class ViewpointTestChecker extends BaseTypeChecker {

    @Override
    public void initChecker() {
        super.initChecker();
        ViewpointTestAnnotationHolder.init(this);
    }
}
