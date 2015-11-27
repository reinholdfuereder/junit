package org.junit.tests.experimental.rules;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.experimental.results.PrintableResult.testResult;
import static org.junit.experimental.results.ResultMatchers.failureCountIs;
import static org.junit.experimental.results.ResultMatchers.isSuccessful;

import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.results.PrintableResult;
import org.junit.rules.TemporaryFolder;

public class TemporaryFolderRuleAssuredDeletionTest {

    private static class StubTemporaryFolder extends TemporaryFolder {
        private StubTemporaryFolder(Builder builder) {
            super(builder);
        }

        /**
         * Don't need to create as we are overriding deletion.
         */
        @Override
        public void create() throws IOException {
        }

        /**
         * Simulates failure to clean-up temporary folder.
         */
        @Override
        protected boolean tryDelete() {
            return false;
        }

        public static Builder builder() {
            return new TemporaryFolder.Builder() {
                @Override
                public TemporaryFolder build() {
                    return new StubTemporaryFolder(this);
               }
            };
        }
    }

    public static class HasTempFolderWithAssuredDeletion {
        @Rule public TemporaryFolder folder = StubTemporaryFolder.builder()
                .assureDeletion()
                .build();

        @Test
        public void alwaysPasses() {
        }
    }

    @Test
    public void testStrictVerificationFailure() {
        PrintableResult result = testResult(HasTempFolderWithAssuredDeletion.class);
        assertThat(result, failureCountIs(1));
        assertThat(result.toString(), containsString("Unable to clean up temporary folder"));
    }

    public static class HasTempFolderWithoutAssuredDeletion {
        @Rule public TemporaryFolder folder = StubTemporaryFolder.builder().build();

        @Test
        public void alwaysPasses() {
        }
    }

    @Test
    public void testStrictVerificationSuccess() {
        PrintableResult result = testResult(HasTempFolderWithoutAssuredDeletion.class);
        assertThat(result, isSuccessful());
    }
    
    public static class HasTempFolderWithAssuredDeletionAndFailingTest {
        @Rule public TemporaryFolder folder = StubTemporaryFolder.builder()
                .assureDeletion()
                .build();

        @Test
        public void testThatFails() {
            fail("test case failure that might lead to temp folder deletion problem");
        }
    }

    @Test
    public void testStrictVerificationFailureDoesNotHideTestFailure() {
        PrintableResult result = testResult(HasTempFolderWithAssuredDeletionAndFailingTest.class);
        assertThat(result, failureCountIs(1)); // TODO: one could also want 2 test failures: first the test case failure, and then in addition the one from the clean up problem
        assertThat(result.toString(), containsString("test case failure that might lead to temp folder deletion problem"));
    }

}
