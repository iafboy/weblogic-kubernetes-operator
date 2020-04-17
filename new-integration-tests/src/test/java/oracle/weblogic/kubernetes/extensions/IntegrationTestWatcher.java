// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.extensions;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.annotations.ITNamespaces;
import oracle.weblogic.kubernetes.utils.LoggingUtil;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestWatcher;

import static oracle.weblogic.kubernetes.actions.TestActions.createUniqueNamespace;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * JUnit5 extension class to intercept test execution at various
 * levels and collect logs in Kubernetes cluster for all artifacts
 * in the namespace used by the tests. The tests has to tag their classes
 * with @ExtendWith(IntegrationTestWatcher.class) for the automatic log
 * collection to work.
 */
public class IntegrationTestWatcher implements
    AfterAllCallback,
    AfterEachCallback,
    BeforeAllCallback,
    BeforeEachCallback,
    BeforeTestExecutionCallback,
    InvocationInterceptor,
    LifecycleMethodExecutionExceptionHandler,
    ParameterResolver,
    TestExecutionExceptionHandler,
    TestWatcher {

  private String className;
  private String methodName;
  private List namespaces = null;

  /**
   * Directory to store logs.
   */
  private static final String LOGS_DIR = System.getProperty("java.io.tmpdir");

  /**
   * Determine if this resolver supports resolution of an argument for the
   * Parameter in the supplied ParameterContext for the supplied ExtensionContext.
   * @param parameterContext the context for the parameter for which an argument should be resolved
   * @param extensionContext the current extension context
   * @return true if this resolver can resolve an argument for the parameter
   * @throws ParameterResolutionException when parameter resolution fails
   */
  @Override
  public boolean supportsParameter(ParameterContext parameterContext,
      ExtensionContext extensionContext) throws ParameterResolutionException {
    return parameterContext.getParameter().getType() == List.class;
  }

  /**
   * Resolve an argument for the Parameter in the supplied ParameterContext for the supplied ExtensionContext.
   * @param parameterContext the context for the parameter for which an argument should be resolved
   * @param extensionContext the extension context for the Executable about to be invoked
   * @return Object the resolved argument for the parameter
   * @throws ParameterResolutionException Thrown if an error is encountered in the execution of a ParameterResolver.
   */
  @Override
  public Object resolveParameter(ParameterContext parameterContext,
      ExtensionContext extensionContext) throws ParameterResolutionException {
    ITNamespaces get = parameterContext.findAnnotation(ITNamespaces.class).get();
    List<String> namespaces = new ArrayList();
    for (int i = 1; i <= get.numofns(); i++) {
      String namespace = assertDoesNotThrow(() -> createUniqueNamespace(),
          "Failed to create unique namespace due to ApiException");
      namespaces.add(namespace);
      logger.info("Created a new namespace called {0}", namespace);
    }
    if (this.namespaces != null) {
      this.namespaces.addAll(namespaces);
    } else {
      this.namespaces = namespaces;
    }
    logger.info(this.namespaces.toString());
    return namespaces;
  }

  /**
   * Prints log messages to separate the beforeAll methods.
   * @param context the current extension context
   */
  @Override
  public void beforeAll(ExtensionContext context) {
    className = context.getRequiredTestClass().getName();
    printHeader(String.format("Starting Test Suite %s", className), "+");
    printHeader(String.format("Starting beforeAll for %s", className), "-");
  }

  /**
   * Gets called when any exception is thrown in beforeAll and collects logs.
   * @param context current extension context
   * @param throwable to handle
   * @throws Throwable in case of failures
   */
  @Override
  public void handleBeforeAllMethodExecutionException​(ExtensionContext context, Throwable throwable)
      throws Throwable {
    printHeader(String.format("BeforeAll failed %s", className), "!");
    collectLogs(context, "beforeAll");
  }

  /**
   * Prints log message to separate the beforeEach messages.
   * @param context the current extension context
   */
  @Override
  public void beforeEach(ExtensionContext context) {
    methodName = context.getRequiredTestMethod().getName();
    printHeader(String.format("Starting beforeEach for %s.%s()", className, methodName), "-");
  }

  /**
   * Gets called when any exception is thrown in beforeEach and collects logs.
   * @param context current extension context
   * @param throwable to handle
   * @throws Throwable in case of failures
   */
  @Override
  public void handleBeforeEachMethodExecutionException​(ExtensionContext context, Throwable throwable)
      throws Throwable {
    printHeader(String.format("BeforeEach failed for %s.%s()", className, methodName), "!");
    collectLogs(context, "beforeEach");
  }

  /**
   * Prints log messages to mark the end of beforeEach method.
   * @param context the current extension context
   */
  @Override
  public void beforeTestExecution(ExtensionContext context) {
    printHeader(String.format("Ending beforeEach for %s.%s()", className, methodName), "-");
  }

  /**
   * Intercept the invocation of a @Test method.
   * Prints log messages to separate the test method logs.
   * @param invocation the invocation that is being intercepted
   * @param invocationContext  the context of the invocation that is being intercepted
   * @param context the current extension context
   * @throws Throwable in case of failures
   */
  @Override
  public void interceptTestMethod​(Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext,
      ExtensionContext context) throws Throwable {
    printHeader(String.format("Starting Test %s.%s()", className, methodName), "-");
    invocation.proceed();
  }

  /**
   * Gets called when any exception is thrown in test and collects logs.
   * @param context current extension context
   * @param throwable to handle
   * @throws Throwable in case of failures
   */
  @Override
  public void handleTestExecutionException​(ExtensionContext context, Throwable throwable)
      throws Throwable {
    printHeader(String.format("Test failed %s.%s()", className, methodName), "!");
    collectLogs(context, "test");
    throw throwable;
  }

  /**
   * Intercept the invocation of a @AfterEach method.
   * Prints log messages to separate the afterEach method logs.
   * @param invocation the invocation that is being intercepted
   * @param invocationContext  the context of the invocation that is being intercepted
   * @param context the current extension context
   * @throws Throwable in case of failures
   */
  @Override
  public void interceptAfterEachMethod​(InvocationInterceptor.Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext,
      ExtensionContext context) throws Throwable {
    printHeader(String.format("Starting afterEach for %s.%s()", className, methodName), "-");
    invocation.proceed();
  }

  /**
   * Prints log message to mark the end of afterEach methods.
   * @param context the current extension context
   */
  @Override
  public void afterEach(ExtensionContext context) {
    printHeader(String.format("Ending afterEach for %s.%s()", className, methodName), "-");
  }

  /**
   * Gets called when any exception is thrown in afterEach and collects logs.
   * @param context current extension context
   * @param throwable to handle
   * @throws Throwable in case of failures
   */
  @Override
  public void handleAfterEachMethodExecutionException​(ExtensionContext context, Throwable throwable)
      throws Throwable {
    printHeader(String.format("AfterEach failed for %s.%s()", className, methodName), "!");
    collectLogs(context, "afterEach");
  }

  /**
   * Called when the test method is successful.
   * @param context the current extension context
   */
  @Override
  public void testSuccessful(ExtensionContext context) {
    printHeader(String.format("Test PASSED %s.%s()", className, methodName), "+");
  }

  /**
   * Called when the test method fails.
   * @param context the current extension context
   * @param cause of failures throwable
   */
  @Override
  public void testFailed(ExtensionContext context, Throwable cause) {
    printHeader(String.format("Test FAILED %s.%s()", className, methodName), "!");
  }

  /**
   * Intercept the invocation of a @AfterAll method.
   * Prints log messages to separate the afterAll method logs.
   * @param invocation the invocation that is being intercepted
   * @param invocationContext  the context of the invocation that is being intercepted
   * @param context the current extension context
   * @throws Throwable in case of failures
   */
  @Override
  public void interceptAfterAllMethod​(InvocationInterceptor.Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext,
      ExtensionContext context) throws Throwable {
    printHeader(String.format("Starting afterAll for %s", className), "-");
    invocation.proceed();
  }

  /**
   * Prints log message to mark end of test suite.
   * @param context the current extension context
   */
  @Override
  public void afterAll(ExtensionContext context) {
    printHeader(String.format("Ending Test Suite %s", className), "+");
  }


  /**
   * Gets called when any exception is thrown in afterAll and collects logs.
   * @param context current extension context
   * @param throwable to handle
   * @throws Throwable in case of failures
   */
  @Override
  public void handleAfterAllMethodExecutionException​(ExtensionContext context, Throwable throwable)
      throws Throwable {
    printHeader(String.format("AfterAll failed for %s", className), "!");
    collectLogs(context, "afterAll");
  }

  /**
   * Collects logs in namespaces used by the current running test and writes in the LOGS_DIR.
   * @param extensionContext current extension context
   * @param failedStage the stage in which the test failed
   */
  private void collectLogs(ExtensionContext extensionContext, String failedStage) {
    logger.info("Collecting logs...");
    if (namespaces == null || namespaces.isEmpty()) {
      logger.warning("Namespace list is empty, "
          + "see if the test class is annotated with @ITNamespaces(numofns = <n>)");
      return;
    }
    Path resultDir = null;
    try {
      resultDir = Files.createDirectories(Paths.get(LOGS_DIR,
              extensionContext.getRequiredTestClass().getSimpleName(),
              getExtDir(failedStage)));
    } catch (IOException ex) {
      logger.warning(ex.getMessage());
    }
    try {
      for (var namespace : namespaces) {
        LoggingUtil.generateLog((String)namespace, resultDir);
      }
    } catch (IOException | ApiException ex) {
      logger.warning(ex.getMessage());
    }
  }

  /**
   * Gets the extension name for the directory based on where the test failed.
   * @param failedStage the test execution failed stage
   * @return String extension directory name
   */
  private String getExtDir(String failedStage) {
    String ext;
    switch (failedStage) {
      case "beforeEach":
      case "afterEach":
        ext = methodName + "_" + failedStage;
        break;
      case "test":
        ext = methodName;
        break;
      default:
        ext = failedStage;
    }
    return ext;
  }

  /**
   * Print start/end/failure messages highlighted.
   * @param message to print
   * @param rc repeater string
   */
  private void printHeader(String message, String rc) {
    logger.info("\n" + rc.repeat(message.length()) + "\n" + message + "\n" + rc.repeat(message.length()) + "\n");
  }
}
