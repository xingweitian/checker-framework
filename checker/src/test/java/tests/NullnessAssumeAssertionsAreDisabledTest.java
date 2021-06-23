package tests;

import org.checkerframework.framework.test.CheckerFrameworkPerDirectoryTest;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.util.List;

/** JUnit tests for the Nullness Checker. */
public class NullnessAssumeAssertionsAreDisabledTest extends CheckerFrameworkPerDirectoryTest {

    /**
     * Create a NullnessAssumeAssertionsAreDisabledTest.
     *
     * @param testFiles the files containing test code, which will be type-checked
     */
    public NullnessAssumeAssertionsAreDisabledTest(List<File> testFiles) {
        super(
                testFiles,
                org.checkerframework.checker.nullness.NullnessChecker.class,
                "nullness",
                "-AassumeAssertionsAreDisabled",
                "-Anomsgtext",
                "-Xlint:deprecation");
    }

    @Parameters
    public static String[] getTestDirs() {
        return new String[] {"nullness-assumeassertions"};
    }
}
