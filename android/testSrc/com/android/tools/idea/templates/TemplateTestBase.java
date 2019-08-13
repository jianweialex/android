/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.templates;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_ANDROIDX_SUPPORT;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_API;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_API_STRING;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_HAS_APPLICATION_THEME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LAUNCHER;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LIBRARY_MODULE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_KOTLIN_VERSION;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_LANGUAGE;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MIN_API;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MIN_API_LEVEL;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MODULE_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_PACKAGE_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_SOURCE_PROVIDER_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_TARGET_API;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_TARGET_API_STRING;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_THEME_EXISTS;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_TOP_OUT;
import static com.android.tools.idea.templates.TemplateMetadata.getBuildApiString;
import static com.android.tools.idea.templates.TemplateTestUtils.addIconsIfNecessary;
import static com.android.tools.idea.templates.TemplateTestUtils.cleanupProjectFiles;
import static com.android.tools.idea.templates.TemplateTestUtils.createNewProjectState;
import static com.android.tools.idea.templates.TemplateTestUtils.createRenderingContext;
import static com.android.tools.idea.templates.TemplateTestUtils.getDefaultValue;
import static com.android.tools.idea.templates.TemplateTestUtils.getModifiedProjectName;
import static com.android.tools.idea.templates.TemplateTestUtils.getModuleTemplateForFormFactor;
import static com.android.tools.idea.templates.TemplateTestUtils.getOption;
import static com.android.tools.idea.templates.TemplateTestUtils.invokeGradleForProjectDir;
import static com.android.tools.idea.templates.TemplateTestUtils.isInterestingApiLevel;
import static com.android.tools.idea.templates.TemplateTestUtils.lintIfNeeded;
import static com.android.tools.idea.templates.TemplateTestUtils.setAndroidSupport;
import static com.android.tools.idea.templates.TemplateTestUtils.setUpFixtureForProject;
import static com.android.tools.idea.templates.TemplateTestUtils.validateTemplate;
import static com.android.tools.idea.templates.TemplateTestUtils.verifyLanguageFiles;
import static com.android.tools.idea.templates.TemplateTestUtils.verifyLastLoggedUsage;
import static com.android.tools.idea.testing.AndroidGradleTests.getLocalRepositoriesForGroovy;
import static com.android.tools.idea.testing.AndroidGradleTests.updateLocalRepositories;
import static com.google.common.base.Charsets.UTF_8;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static java.lang.annotation.ElementType.METHOD;
import static org.mockito.Mockito.mock;

import com.android.SdkConstants;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.testutils.TestUtils;
import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate;
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor;
import com.android.tools.idea.npw.assetstudio.IconGenerator;
import com.android.tools.idea.npw.assetstudio.LauncherIconGenerator;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.npw.platform.Language;
import com.android.tools.idea.npw.project.AndroidGradleModuleUtils;
import com.android.tools.idea.projectsystem.AndroidModuleTemplate;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import java.io.File;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

/**
 * Test for template instantiation.
 * <p>
 * Remaining work on templates:
 * Fix type conversion, to make the service and fragment templates work
 * <p>
 * Remaining work on template test:
 * <ul>
 * <li>Add mechanism to ensure that test coverage is comprehensive (made difficult by </li>
 * <li>Start using new NewProjectModel etc to initialise TemplateParameters and set parameter values</li>
 * <li>Fix clean model syncing, and hook up clean lint checks</li>
 * <li>We should test more combinations of parameters</li>
 * <li>We should test all combinations of build tools</li>
 * <li>Test creating a project <b>without</b> a template</li>
 * </ul>
 */
public class TemplateTestBase extends AndroidGradleTestCase {
  /**
   * A UsageTracker implementation that allows introspection of logged metrics in tests.
   */
  private TestUsageTracker myUsageTracker;

  /**
   * Whether we should run comprehensive tests or not. This flag allows a simple run to just check a small set of
   * template combinations, and when the flag is set on the build server, a much more comprehensive battery of
   * checks to be performed.
   */
  private static final boolean COMPREHENSIVE =
    Boolean.parseBoolean(System.getProperty("com.android.tools.idea.templates.TemplateTest.COMPREHENSIVE")) ||
    Boolean.TRUE.toString().equals(System.getenv("com.android.tools.idea.templates.TemplateTest.COMPREHENSIVE"));

  /**
   * Whether we should run these tests or not.
   */
  protected static final boolean DISABLED =
    Boolean.parseBoolean(System.getProperty("DISABLE_STUDIO_TEMPLATE_TESTS")) ||
    Boolean.TRUE.toString().equals(System.getenv("DISABLE_STUDIO_TEMPLATE_TESTS"));

  /**
   * Whether we should enforce that lint passes cleanly on the projects
   */
  static final boolean CHECK_LINT = false; // Needs work on closing projects cleanly

  /**
   * Manual sdk version selections
   **/
  private static final int MANUAL_BUILD_API =
    Integer.parseInt(System.getProperty("com.android.tools.idea.templates.TemplateTest.MANUAL_BUILD_API", "-1"));
  private static final int MANUAL_MIN_API =
    Integer.parseInt(System.getProperty("com.android.tools.idea.templates.TemplateTest.MANUAL_MIN_API", "-1"));
  private static final int MANUAL_TARGET_API =
    Integer.parseInt(System.getProperty("com.android.tools.idea.templates.TemplateTest.MANUAL_TARGET_API", "-1"));

  /**
   * The following templates are known to be broken! We need to work through these and fix them such that tests
   * on them can be re-enabled.
   */
  protected static boolean isBroken(@NotNull String templateName) {
    // See http://b.android.com/253296
    if (SystemInfo.isWindows) {
      if ("AidlFile".equals(templateName)) return true;
    }
    return false;
  }

  /**
   * The following templates parameters are not very interesting (change only one small bit of text etc).
   * We can skip them when not running in comprehensive mode.
   * TODO(qumeric): update or remove
   */
  private static final Set<String> SKIPPABLE_PARAMETERS = ImmutableSet.of();

  /**
   * Flags used to quickly check each template once (for one version), to get
   * quicker feedback on whether something is broken instead of waiting for
   * all the versions for each template first
   */
  public static final boolean TEST_FEWER_API_VERSIONS = !COMPREHENSIVE;
  private static final boolean TEST_JUST_ONE_MIN_SDK = !COMPREHENSIVE;
  private static final boolean TEST_JUST_ONE_BUILD_TARGET = !COMPREHENSIVE;
  private static final boolean TEST_JUST_ONE_TARGET_SDK_VERSION = !COMPREHENSIVE;

  // TODO: this is used only in TemplateTest. We should pass this value without changing template values.
  final static String ATTR_CREATE_ACTIVITY = "createActivity";

  public void testSilenceNoTestsWarning() {
  }

  @Override
  protected boolean createDefaultProject() {
    // We'll be creating projects manually except for the following tests
    String testName = getName();
    return testName.equals("testTemplateFormatting") || testName.equals("testCreateGradleWrapper");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    VirtualTimeScheduler scheduler = new VirtualTimeScheduler();
    myUsageTracker = new TestUsageTracker(scheduler);
    UsageTracker.setWriterForTest(myUsageTracker);
    myApiSensitiveTemplate = true;

    // Replace the default RepositoryUrlManager with one that enables repository checks in tests. (myForceRepositoryChecksInTests)
    // This is necessary to fully resolve dynamic gradle coordinates such as ...:appcompat-v7:+ => appcompat-v7:25.3.1
    // keeping it exactly the same as they are resolved within the NPW flow.
    new IdeComponents(null, getTestRootDisposable()).replaceApplicationService(
      RepositoryUrlManager.class,
      new RepositoryUrlManager(IdeGoogleMavenRepository.INSTANCE, OfflineIdeGoogleMavenRepository.INSTANCE, true));
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myUsageTracker.close();
      UsageTracker.cleanAfterTesting();
    }
    finally {
      super.tearDown();
    }
  }

  /**
   * If true, check this template with all the interesting (
   * {@link #isInterestingApiLevel(int, int)}) api versions
   */
  protected boolean myApiSensitiveTemplate;

  protected final ProjectStateCustomizer withKotlin = ((templateMap, projectMap) -> {
    projectMap.put(ATTR_KOTLIN_VERSION, TestUtils.getKotlinVersionForTests());
    projectMap.put(ATTR_LANGUAGE, Language.KOTLIN.toString());
    templateMap.put(ATTR_LANGUAGE, Language.KOTLIN.toString());
    templateMap.put(ATTR_PACKAGE_NAME, "test.pkg.in"); // Add in a Kotlin keyword ("in") in the package name to trigger escape code too
  });

  //--- Test support code ---

  /**
   * Checks the given template in the given category, adding it to an existing project
   */
  protected void checkCreateTemplate(String category, String name) throws Exception {
    checkCreateTemplate(category, name, false);
  }

  protected void checkCreateTemplate(String category, String name, boolean createWithProject) throws Exception {
    checkCreateTemplate(category, name, createWithProject, null);
  }

  /**
   * Checks the given template in the given category
   *
   * @param category          the template category
   * @param name              the template name
   * @param createWithProject whether the template should be created as part of creating the project (which should
   *                          only be done for activities), or whether it should be added as as a separate template
   *                          into an existing project (which is created first, followed by the template)
   * @param customizer        An instance of {@link ProjectStateCustomizer} used for providing template and project overrides.
   * @throws Exception
   */
  protected void checkCreateTemplate(String category, String name, boolean createWithProject,
                                     @Nullable ProjectStateCustomizer customizer) throws Exception {
    if (DISABLED) {
      return;
    }
    File templateFile = findTemplate(category, name);
    assertNotNull(templateFile);
    if (isBroken(templateFile.getName())) {
      return;
    }
    Stopwatch stopwatch = Stopwatch.createStarted();
    if (customizer == null) {
      checkTemplate(templateFile, createWithProject);
    }
    else {
      checkTemplate(templateFile, createWithProject, customizer);
    }
    stopwatch.stop();
    System.out.println("Checked " + templateFile.getName() + " successfully in " + stopwatch.toString());
  }

  @NotNull
  protected File findTemplate(String category, String name) {
    ensureSdkManagerAvailable();
    File templateRootFolder = TemplateManager.getTemplateRootFolder();
    assertNotNull(templateRootFolder);
    File file = new File(templateRootFolder, category + File.separator + name);
    assertTrue(file.getPath(), file.exists());
    return file;
  }

  private void checkTemplate(File templateFile, boolean createWithProject) throws Exception {
    checkTemplate(templateFile, createWithProject, ImmutableMap.of(), ImmutableMap.of());
  }

  private void checkTemplate(File templateFile, boolean createWithProject, @NotNull ProjectStateCustomizer customizer) throws Exception {
    Map<String, Object> templateOverrides = new HashMap<>();
    Map<String, Object> projectOverrides = new HashMap<>();
    customizer.customize(templateOverrides, projectOverrides);
    checkTemplate(templateFile, createWithProject, templateOverrides, projectOverrides);
  }

  private void checkTemplate(File templateFile,
                             boolean createWithProject,
                             @NotNull Map<String, Object> overrides,
                             @NotNull Map<String, Object> projectOverrides) throws Exception {
    if (isBroken(templateFile.getName())) {
      return;
    }

    AndroidSdkData sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();

    TestNewProjectWizardState projectState =
      createNewProjectState(createWithProject, sdkData, getModuleTemplateForFormFactor(templateFile));

    String projectNameBase = templateFile.getName();

    TestTemplateWizardState activityState = projectState.getActivityTemplateState();
    activityState.setTemplateLocation(templateFile);

    TestTemplateWizardState moduleState = projectState.getModuleTemplateState();

    // Iterate over all (valid) combinations of build target, minSdk and targetSdk
    // TODO: Assert that the SDK manager has a minimum set of SDKs installed needed to be certain
    // the test is comprehensive
    // For now make sure there's at least one
    boolean ranTest = false;
    int lowestMinApiForProject =
      Math.max(Integer.parseInt((String)moduleState.get(ATTR_MIN_API)), moduleState.getTemplateMetadata().getMinSdk());

    IAndroidTarget[] targets = sdkData.getTargets();
    for (int i = targets.length - 1; i >= 0; i--) {
      IAndroidTarget target = targets[i];
      if (!target.isPlatform()) {
        continue;
      }
      if (!isInterestingApiLevel(target.getVersion().getApiLevel(), MANUAL_BUILD_API, myApiSensitiveTemplate)) {
        continue;
      }

      TemplateMetadata activityMetadata = activityState.getTemplateMetadata();
      TemplateMetadata moduleMetadata = moduleState.getTemplateMetadata();

      int lowestSupportedApi = Math.max(lowestMinApiForProject, activityMetadata.getMinSdk());

      for (int minSdk = lowestSupportedApi; minSdk <= SdkVersionInfo.HIGHEST_KNOWN_API; minSdk++) {
        // Don't bother checking *every* single minSdk, just pick some interesting ones
        if (!isInterestingApiLevel(minSdk, MANUAL_MIN_API, myApiSensitiveTemplate)) {
          continue;
        }

        for (int targetSdk = minSdk;
             targetSdk <= SdkVersionInfo.HIGHEST_KNOWN_API;
             targetSdk++) {
          if (!isInterestingApiLevel(targetSdk, MANUAL_TARGET_API, myApiSensitiveTemplate)) {
            continue;
          }

          String status = validateTemplate(moduleMetadata, minSdk, target.getVersion().getApiLevel());
          if (status != null) {
            continue;
          }

          // Also make sure activity is enabled for these versions
          status = validateTemplate(activityMetadata, minSdk, target.getVersion().getApiLevel());
          if (status != null) {
            continue;
          }

          // Iterate over all new new project templates

          // should I try all options of theme with all platforms?
          // or just try all platforms, with one setting for each?
          // doesn't seem like I need to multiply
          // just pick the best setting that applies instead for each platform
          Collection<Parameter> parameters = moduleMetadata.getParameters();
          // Does it have any enums?
          boolean hasEnums = parameters.stream().anyMatch(p -> p.type == Parameter.Type.ENUM);
          if (!hasEnums || overrides != null) {
            String base = projectNameBase
                          + "_min_" + minSdk
                          + "_target_" + targetSdk
                          + "_build_" + target.getVersion().getApiLevel();
            if (overrides != null) {
              base += "_overrides";
            }
            checkApiTarget(minSdk, targetSdk, target, projectState, base, activityState, overrides, projectOverrides);
            ranTest = true;
          }
          else {
            // Handle all enums here. None of the projects have this currently at this level
            // so we will bite the bullet when we first encounter it.
            fail("Not expecting enums at the root level");
          }

          if (TEST_JUST_ONE_TARGET_SDK_VERSION) {
            break;
          }
        }

        if (TEST_JUST_ONE_MIN_SDK) {
          break;
        }
      }

      if (TEST_JUST_ONE_BUILD_TARGET) {
        break;
      }
    }
    assertTrue("Didn't run any tests! Make sure you have the right platforms installed.", ranTest);
  }

  /**
   * Checks creating the given project and template for the given SDK versions
   */
  protected void checkApiTarget(
    int minSdk,
    int targetSdk,
    @NotNull IAndroidTarget target,
    @NotNull TestNewProjectWizardState projectState,
    @NotNull String projectNameBase,
    @Nullable TestTemplateWizardState activityState,
    @NotNull Map<String, Object> overrides,
    @NotNull Map<String, Object> projectOverrides) throws Exception {

    TestTemplateWizardState moduleState = projectState.getModuleTemplateState();
    Boolean createActivity = (Boolean)moduleState.get(ATTR_CREATE_ACTIVITY);
    if (createActivity == null) {
      createActivity = true;
    }
    TestTemplateWizardState templateState = createActivity ? projectState.getActivityTemplateState() : activityState;
    assertNotNull(templateState);

    moduleState.put(ATTR_MIN_API, Integer.toString(minSdk));
    moduleState.put(ATTR_MIN_API_LEVEL, minSdk);
    moduleState.put(ATTR_TARGET_API, targetSdk);
    moduleState.put(ATTR_TARGET_API_STRING, Integer.toString(targetSdk));
    moduleState.put(ATTR_BUILD_API, target.getVersion().getApiLevel());
    moduleState.put(ATTR_BUILD_API_STRING, getBuildApiString(target.getVersion()));

    // Next check all other parameters, cycling through booleans and enums.
    Template templateHandler = templateState.getTemplate();
    assertNotNull(templateHandler);
    TemplateMetadata template = templateHandler.getMetadata();
    assertNotNull(template);
    Iterable<Parameter> parameters = template.getParameters();

    if (!createActivity) {
      templateState.setParameterDefaults();
    }
    else {
      TemplateMetadata moduleMetadata = moduleState.getTemplate().getMetadata();
      assertNotNull(moduleMetadata);
      parameters = Iterables.concat(parameters, moduleMetadata.getParameters());
    }

    for (Map.Entry<String, Object> entry : overrides.entrySet()) {
      templateState.put(entry.getKey(), entry.getValue());
    }
    for (Map.Entry<String, Object> entry : projectOverrides.entrySet()) {
      moduleState.put(entry.getKey(), entry.getValue());
    }

    String projectName;
    for (Parameter parameter : parameters) {
      if (parameter.type == Parameter.Type.SEPARATOR || parameter.type == Parameter.Type.STRING) {
        // TODO: Consider whether we should attempt some strings here
        continue;
      }

      if (!COMPREHENSIVE && SKIPPABLE_PARAMETERS.contains(parameter.id)) {
        continue;
      }

      if (overrides != null && overrides.containsKey(parameter.id)) {
        continue;
      }

      // revert to this one after cycling,
      Object initial = getDefaultValue(parameter, templateState);

      if (parameter.type == Parameter.Type.ENUM) {
        List<Element> options = parameter.getOptions();
        for (Element element : options) {
          Option option = getOption(element);
          String optionId = option.id;
          int optionMinSdk = option.minSdk;
          int optionMinBuildApi = option.minBuild;
          int projectMinApi = moduleState.getInt(ATTR_MIN_API_LEVEL);
          int projectBuildApi = moduleState.getInt(ATTR_BUILD_API);
          if (projectMinApi >= optionMinSdk && projectBuildApi >= optionMinBuildApi && !optionId.equals(initial)) {
            templateState.put(parameter.id, optionId);
            projectName = projectNameBase + "_" + parameter.id + "_" + optionId;
            checkProject(projectName, projectState, activityState);
            if (!COMPREHENSIVE) {
              break;
            }
          }
        }
      }
      else {
        assert parameter.type == Parameter.Type.BOOLEAN;
        if (parameter.id.equals(ATTR_IS_LAUNCHER) && createActivity) {
          // Skipping this one: always true when launched from new project
          continue;
        }
        boolean initialValue = (boolean)initial;
        // For boolean values, only run checkProject in the non-default setting.
        // The default value is already used when running checkProject in the default state for all variables.
        boolean value = !initialValue;
        templateState.put(parameter.id, value);
        projectName = projectNameBase + "_" + parameter.id + "_" + value;
        checkProject(projectName, projectState, activityState);
      }
      templateState.put(parameter.id, initial);
    }
    projectName = projectNameBase + "_default";
    checkProject(projectName, projectState, activityState);
  }

  private void checkProject(
    @NotNull String projectName,
    @NotNull TestNewProjectWizardState projectState,
    @Nullable TestTemplateWizardState activityState
  ) throws Exception {

    TestTemplateWizardState moduleState = projectState.getModuleTemplateState();
    boolean checkLib = false;
    if (activityState != null) {
      Template template = activityState.getTemplate();
      assert (template != null);
      TemplateMetadata templateMetadata = template.getMetadata();
      assert (templateMetadata != null);
      checkLib = "Activity".equals(templateMetadata.getCategory()) &&
                 "Mobile".equals(templateMetadata.getFormFactor()) &&
                 !moduleState.getBoolean(ATTR_CREATE_ACTIVITY);

      if (templateMetadata.getAndroidXRequired()) {
        setAndroidSupport(true, moduleState, activityState);
      }
    }

    if (!Boolean.TRUE.equals(moduleState.get(ATTR_ANDROIDX_SUPPORT))) {
      // Make sure we test all templates against androidx
      setAndroidSupport(true, moduleState, activityState);
      checkProjectNow(projectName + "_x", projectState, activityState);
      setAndroidSupport(false, moduleState, activityState);
    }

    if (checkLib) {
      moduleState.put(ATTR_IS_LIBRARY_MODULE, false);
      activityState.put(ATTR_IS_LIBRARY_MODULE, false);
      activityState.put(ATTR_HAS_APPLICATION_THEME, true);
    }
    checkProjectNow(projectName, projectState, activityState);

    // check that new Activities can be created on lib modules as well as app modules.
    if (checkLib) {
      moduleState.put(ATTR_IS_LIBRARY_MODULE, true);
      activityState.put(ATTR_IS_LIBRARY_MODULE, true);
      activityState.put(ATTR_HAS_APPLICATION_THEME, false);
      // For a library project a theme doesn't exist. This is derived in the IDE using FmGetApplicationThemeMethod
      moduleState.put(ATTR_THEME_EXISTS, false);
      checkProjectNow(projectName + "_lib", projectState, activityState);
    }
  }

  private void checkProjectNow(
    @NotNull String projectName,
    @NotNull TestNewProjectWizardState projectState,
    @Nullable TestTemplateWizardState activityState
  ) throws Exception {
    TestTemplateWizardState moduleState = projectState.getModuleTemplateState();
    String modifiedProjectName = getModifiedProjectName(projectName, activityState);

    moduleState.put(ATTR_MODULE_NAME, modifiedProjectName);
    assertNull(myFixture);
    myFixture = setUpFixtureForProject(modifiedProjectName);
    @NotNull Project project = Objects.requireNonNull(getProject());
    new IdeComponents(project).replaceProjectService(PostProjectBuildTasksExecutor.class, mock(PostProjectBuildTasksExecutor.class));
    AndroidGradleTests.setUpSdks(myFixture, findSdkPath());
    @NotNull File projectDir = getBaseDirPath(project);
    moduleState.put(ATTR_TOP_OUT, projectDir.getPath());

    System.out.println("Checking project " + projectName + " in " + ProjectUtil.guessProjectDir(project));
    try {
      createProject(projectState, CHECK_LINT);

      File projectRoot = virtualToIoFile(ProjectUtil.guessProjectDir(project));
      if (activityState != null && !moduleState.getBoolean(ATTR_CREATE_ACTIVITY)) {
        activityState.put(ATTR_TOP_OUT, projectDir.getPath());
        ApplicationManager.getApplication().runWriteAction(() -> {
          Template template = activityState.getTemplate();
          assert template != null;
          File moduleRoot = new File(projectRoot, modifiedProjectName);
          activityState.put(ATTR_MODULE_NAME, moduleRoot.getName());
          activityState.put(ATTR_SOURCE_PROVIDER_NAME, "main");
          activityState.populateDirectoryParameters();
          RenderingContext context = createRenderingContext(template, project, moduleRoot, moduleRoot, activityState.getParameters());
          template.render(context, false);

          addIconsIfNecessary(activityState);
        });
      }

      // Verify that a newly created Kotlin project only kotlin files (e.g. no Java).
      if (getTestName(false).endsWith("WithKotlin")) {
        verifyLanguageFiles(projectDir, Language.KOTLIN);
      }

      invokeGradleForProjectDir(projectDir);
      lintIfNeeded(project);
    }
    finally {
      if (myFixture != null) {
        myFixture.tearDown();
        myFixture = null;
      }

      Project[] openProjects = ProjectManagerEx.getInstanceEx().getOpenProjects();
      assertTrue(openProjects.length <= 1); // 1: the project created by default by the test case

      cleanupProjectFiles(projectDir);
    }
  }

  @SuppressWarnings("SameParameterValue")
  private void createProject(@NotNull TestNewProjectWizardState projectState, boolean syncProject) throws Exception {
    TestTemplateWizardState moduleState = projectState.getModuleTemplateState();
    ApplicationManager.getApplication().runWriteAction(() -> {
      int minSdkVersion = Integer.parseInt((String)moduleState.get(ATTR_MIN_API));
      IconGenerator iconGenerator = new LauncherIconGenerator(myFixture.getProject(), minSdkVersion, null);
      try {
        iconGenerator.outputName().set("ic_launcher");
        iconGenerator.sourceAsset().setValue(new ImageAsset());
        createProject(projectState, myFixture.getProject(), iconGenerator);
      }
      finally {
        Disposer.dispose(iconGenerator);
      }
      FileDocumentManager.getInstance().saveAllDocuments();
    });

    // Update to latest plugin / gradle and sync model
    File projectRoot = new File(moduleState.getString(ATTR_TOP_OUT));
    assertEquals(projectRoot, virtualToIoFile(ProjectUtil.guessProjectDir(myFixture.getProject())));
    AndroidGradleTests.createGradleWrapper(projectRoot, GRADLE_LATEST_VERSION);

    File gradleFile = new File(projectRoot, SdkConstants.FN_BUILD_GRADLE);
    String origContent = com.google.common.io.Files.asCharSource(gradleFile, UTF_8).read();
    String newContent = updateLocalRepositories(origContent, getLocalRepositoriesForGroovy());
    if (!newContent.equals(origContent)) {
      com.google.common.io.Files.asCharSink(gradleFile, UTF_8).write(newContent);
    }

    refreshProjectFiles();
    if (syncProject) {
      assertEquals(moduleState.getString(ATTR_MODULE_NAME), getProject().getName());
      assertEquals(projectRoot, getBaseDirPath(getProject()));
      importProject();
    }
  }

  private void createProject(@NotNull TestNewProjectWizardState projectState, @NotNull Project project,
                             @Nullable IconGenerator iconGenerator) {
    TestTemplateWizardState moduleState = projectState.getModuleTemplateState();
    List<String> errors = new ArrayList<>();
    try {
      moduleState.populateDirectoryParameters();
      String moduleName = moduleState.getString(ATTR_MODULE_NAME);
      String projectPath = moduleState.getString(ATTR_TOP_OUT);
      File projectRoot = new File(projectPath);
      AndroidModuleTemplate paths = GradleAndroidModuleTemplate.createDefaultTemplateAt(projectPath, moduleName).getPaths();
      if (FileUtilRt.createDirectory(projectRoot)) {
        if (iconGenerator != null) {
          // TODO test the icon generator
        }
        projectState.updateParameters();

        File moduleRoot = paths.getModuleRoot();
        assert moduleRoot != null;

        // If this is a new project, instantiate the project-level files
        Template projectTemplate = projectState.getProjectTemplate();
        final RenderingContext projectContext =
          createRenderingContext(projectTemplate, project, projectRoot, moduleRoot, moduleState.getParameters());
        projectTemplate.render(projectContext, false);
        // check usage tracker after project render
        verifyLastLoggedUsage(myUsageTracker, Template.titleToTemplateRenderer(projectTemplate.getMetadata().getTitle()),
                              projectContext.getParamMap());
        AndroidGradleModuleUtils.setGradleWrapperExecutable(projectRoot);

        final RenderingContext moduleContext =
          createRenderingContext(moduleState.getTemplate(), project, projectRoot, moduleRoot, moduleState.getParameters());
        Template moduleTemplate = moduleState.getTemplate();
        moduleTemplate.render(moduleContext, false);
        // check usage tracker after module render
        verifyLastLoggedUsage(myUsageTracker, Template.titleToTemplateRenderer(moduleTemplate.getMetadata().getTitle()),
                              moduleContext.getParamMap());
        if (moduleState.getBoolean(ATTR_CREATE_ACTIVITY)) {
          TestTemplateWizardState activityTemplateState = projectState.getActivityTemplateState();
          Template activityTemplate = activityTemplateState.getTemplate();
          assert activityTemplate != null;
          final RenderingContext activityContext =
            createRenderingContext(activityTemplate, project, moduleRoot, moduleRoot, activityTemplateState.getParameters());
          activityTemplate.render(activityContext, false);
          // check usage tracker after activity render
          verifyLastLoggedUsage(myUsageTracker, Template.titleToTemplateRenderer(activityTemplate.getMetadata().getTitle()),
                                activityContext.getParamMap());
          moduleContext.getFilesToOpen().addAll(activityContext.getFilesToOpen());
        }
      }
      else {
        errors.add(String.format("Unable to create directory '%1$s'.", projectRoot.getPath()));
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    assertEmpty(errors);
  }

  //--- Interfaces, annotations and helper classes ---

  public interface ProjectStateCustomizer {
    void customize(@NotNull Map<String, Object> templateMap, @NotNull Map<String, Object> projectMap);
  }

  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target({METHOD})
  public @interface TemplateCheck {
  }
}