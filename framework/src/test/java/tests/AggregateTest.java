package tests;

import org.checkerframework.framework.test.FrameworkPerDirectoryTest;
import org.junit.runners.Parameterized.Parameters;

import testlib.aggregate.AggregateOfCompoundChecker;

import java.io.File;
import java.util.List;

public class AggregateTest extends FrameworkPerDirectoryTest {

    /** @param testFiles the files containing test code, which will be type-checked */
    public AggregateTest(List<File> testFiles) {
        super(
                testFiles,
                AggregateOfCompoundChecker.class,
                "aggregate",
                "-Anomsgtext",
                "-AresolveReflection");
    }

    @Parameters
    public static String[] getTestDirs() {
        return new String[] {"aggregate"};
    }
}
