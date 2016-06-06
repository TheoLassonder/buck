/*
 * Copyright 2015-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.lua;

import static org.junit.Assert.assertThat;

import com.facebook.buck.cli.FakeBuckConfig;
import com.facebook.buck.cxx.CxxBuckConfig;
import com.facebook.buck.cxx.CxxLibraryBuilder;
import com.facebook.buck.cxx.CxxPlatformUtils;
import com.facebook.buck.cxx.CxxTestBuilder;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.FlavorDomain;
import com.facebook.buck.model.ImmutableFlavor;
import com.facebook.buck.python.CxxPythonExtensionBuilder;
import com.facebook.buck.python.PythonEnvironment;
import com.facebook.buck.python.PythonLibrary;
import com.facebook.buck.python.PythonLibraryBuilder;
import com.facebook.buck.python.PythonPlatform;
import com.facebook.buck.python.PythonVersion;
import com.facebook.buck.rules.DefaultTargetNodeToBuildRuleTransformer;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.CommandTool;
import com.facebook.buck.rules.FakeSourcePath;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourceWithFlags;
import com.facebook.buck.rules.SymlinkTree;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.rules.coercer.PatternMatchedCollection;
import com.facebook.buck.rules.coercer.SourceList;
import com.facebook.buck.testutil.TargetGraphFactory;
import com.facebook.buck.util.HumanReadableException;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;

import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.nio.file.Paths;
import java.util.regex.Pattern;

public class LuaBinaryDescriptionTest {

  private static final BuildTarget PYTHON2_DEP_TARGET =
      BuildTargetFactory.newInstance("//:python2_dep");
  private static final PythonPlatform PY2 =
      PythonPlatform.of(
          ImmutableFlavor.of("py2"),
          new PythonEnvironment(Paths.get("python2"), PythonVersion.of("CPython", "2.6")),
          Optional.of(PYTHON2_DEP_TARGET));

  private static final BuildTarget PYTHON3_DEP_TARGET =
      BuildTargetFactory.newInstance("//:python3_dep");
  private static final PythonPlatform PY3 =
      PythonPlatform.of(
          ImmutableFlavor.of("py3"),
          new PythonEnvironment(Paths.get("python3"), PythonVersion.of("CPython", "3.5")),
          Optional.of(PYTHON3_DEP_TARGET));

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void mainModule() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    LuaBinary binary =
        (LuaBinary) new LuaBinaryBuilder(BuildTargetFactory.newInstance("//:rule"))
            .setMainModule("hello.world")
            .build(resolver);
    assertThat(binary.getMainModule(), Matchers.equalTo("hello.world"));
  }

  @Test
  public void extensionOverride() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    LuaBinary binary =
        (LuaBinary) new LuaBinaryBuilder(
                BuildTargetFactory.newInstance("//:rule"),
                FakeLuaConfig.DEFAULT
                    .withExtension(".override"))
            .setMainModule("main")
            .build(resolver);
    assertThat(binary.getBinPath().toString(), Matchers.endsWith(".override"));
  }

  @Test
  public void toolOverride() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    Tool override = new CommandTool.Builder().addArg("override").build();
    LuaBinary binary =
        (LuaBinary) new LuaBinaryBuilder(
            BuildTargetFactory.newInstance("//:rule"),
            FakeLuaConfig.DEFAULT
                .withLua(override)
                .withExtension(".override"))
            .setMainModule("main")
            .build(resolver);
    assertThat(binary.getLua(), Matchers.is(override));
  }

  @Test
  public void versionLessNativeLibraryExtension() throws Exception {
    CxxLibraryBuilder cxxLibraryBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:lib"))
            .setSoname("libfoo.so.1.0")
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("hello.c"))));
    LuaBinaryBuilder binaryBuilder =
        new LuaBinaryBuilder(
            BuildTargetFactory.newInstance("//:rule"),
            FakeLuaConfig.DEFAULT.withPackageStyle(LuaConfig.PackageStyle.INPLACE))
            .setMainModule("main")
            .setDeps(ImmutableSortedSet.of(cxxLibraryBuilder.getTarget()));
    BuildRuleResolver resolver =
        new BuildRuleResolver(
            TargetGraphFactory.newInstance(
                cxxLibraryBuilder.build(),
                binaryBuilder.build()),
            new DefaultTargetNodeToBuildRuleTransformer());
    cxxLibraryBuilder.build(resolver);
    binaryBuilder.build(resolver);
    SymlinkTree tree =
        resolver.getRuleWithType(
            LuaBinaryDescription.getNativeLibsSymlinkTreeTarget(binaryBuilder.getTarget()),
            SymlinkTree.class);
    assertThat(
        tree.getLinks().keySet(),
        Matchers.hasItem(
            tree.getProjectFilesystem().getRootPath().getFileSystem().getPath("libfoo.so")));
  }

  @Test
  public void duplicateIdenticalModules() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    LuaLibrary libraryA =
        (LuaLibrary) new LuaLibraryBuilder(BuildTargetFactory.newInstance("//:a"))
            .setSrcs(
                ImmutableSortedMap.<String, SourcePath>of("foo.lua", new FakeSourcePath("test")))
            .build(resolver);
    LuaLibrary libraryB =
        (LuaLibrary) new LuaLibraryBuilder(BuildTargetFactory.newInstance("//:b"))
            .setSrcs(
                ImmutableSortedMap.<String, SourcePath>of("foo.lua", new FakeSourcePath("test")))
            .build(resolver);
    new LuaBinaryBuilder(BuildTargetFactory.newInstance("//:rule"))
        .setMainModule("hello.world")
        .setDeps(ImmutableSortedSet.of(libraryA.getBuildTarget(), libraryB.getBuildTarget()))
        .build(resolver);
  }

  @Test
  public void duplicateConflictingModules() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    LuaLibrary libraryA =
        (LuaLibrary) new LuaLibraryBuilder(BuildTargetFactory.newInstance("//:a"))
            .setSrcs(
                ImmutableSortedMap.<String, SourcePath>of("foo.lua", new FakeSourcePath("foo")))
            .build(resolver);
    LuaLibrary libraryB =
        (LuaLibrary) new LuaLibraryBuilder(BuildTargetFactory.newInstance("//:b"))
            .setSrcs(
                ImmutableSortedMap.<String, SourcePath>of("foo.lua", new FakeSourcePath("bar")))
            .build(resolver);
    expectedException.expect(HumanReadableException.class);
    expectedException.expectMessage(Matchers.containsString("conflicting modules for foo.lua"));
    new LuaBinaryBuilder(BuildTargetFactory.newInstance("//:rule"))
        .setMainModule("hello.world")
        .setDeps(ImmutableSortedSet.of(libraryA.getBuildTarget(), libraryB.getBuildTarget()))
        .build(resolver);
  }

  @Test
  public void pythonDeps() throws Exception {
    BuildRuleResolver resolver =
        new BuildRuleResolver(TargetGraph.EMPTY, new DefaultTargetNodeToBuildRuleTransformer());
    PythonLibrary pythonLibrary =
        (PythonLibrary) new PythonLibraryBuilder(BuildTargetFactory.newInstance("//:dep"))
            .setSrcs(
                SourceList.ofUnnamedSources(
                    ImmutableSortedSet.<SourcePath>of(new FakeSourcePath("foo.py"))))
            .build(resolver);
    LuaBinary luaBinary =
        (LuaBinary) new LuaBinaryBuilder(BuildTargetFactory.newInstance("//:rule"))
            .setMainModule("hello.world")
            .setDeps(ImmutableSortedSet.of(pythonLibrary.getBuildTarget()))
            .build(resolver);
    assertThat(
        luaBinary.getComponents().getPythonModules().keySet(),
        Matchers.hasItem("foo.py"));
  }


  @Test
  public void platformDeps() throws Exception {
    FlavorDomain<PythonPlatform> pythonPlatforms = FlavorDomain.of("Python Platform", PY2, PY3);
    CxxBuckConfig cxxBuckConfig = new CxxBuckConfig(FakeBuckConfig.builder().build());

    CxxLibraryBuilder py2LibBuilder = new CxxLibraryBuilder(PYTHON2_DEP_TARGET);
    CxxLibraryBuilder py3LibBuilder = new CxxLibraryBuilder(PYTHON3_DEP_TARGET);
    CxxLibraryBuilder py2CxxLibraryBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:py2_library"))
            .setSoname("libpy2.so")
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("hello.c"))));
    CxxLibraryBuilder py3CxxLibraryBuilder =
        new CxxLibraryBuilder(BuildTargetFactory.newInstance("//:py3_library"))
            .setSoname("libpy3.so")
            .setSrcs(ImmutableSortedSet.of(SourceWithFlags.of(new FakeSourcePath("hello.c"))));
    CxxPythonExtensionBuilder cxxPythonExtensionBuilder =
        new CxxPythonExtensionBuilder(
            BuildTargetFactory.newInstance("//:extension"),
            pythonPlatforms,
            cxxBuckConfig,
            CxxTestBuilder.createDefaultPlatforms())
        .setPlatformDeps(
            PatternMatchedCollection.<ImmutableSortedSet<BuildTarget>>builder()
                .add(
                    Pattern.compile(PY2.getFlavor().toString()),
                    ImmutableSortedSet.of(py2CxxLibraryBuilder.getTarget()))
                .add(
                    Pattern.compile(PY3.getFlavor().toString()),
                    ImmutableSortedSet.of(py3CxxLibraryBuilder.getTarget()))
                .build());
    LuaBinaryBuilder luaBinaryBuilder =
        new LuaBinaryBuilder(
            new LuaBinaryDescription(
                FakeLuaConfig.DEFAULT,
                cxxBuckConfig,
                CxxPlatformUtils.DEFAULT_PLATFORM,
                CxxPlatformUtils.DEFAULT_PLATFORMS,
                pythonPlatforms),
            BuildTargetFactory.newInstance("//:binary"))
        .setMainModule("main")
        .setDeps(ImmutableSortedSet.of(cxxPythonExtensionBuilder.getTarget()));

    BuildRuleResolver resolver =
        new BuildRuleResolver(
            TargetGraphFactory.newInstance(
                py2LibBuilder.build(),
                py3LibBuilder.build(),
                py2CxxLibraryBuilder.build(),
                py3CxxLibraryBuilder.build(),
                cxxPythonExtensionBuilder.build(),
                luaBinaryBuilder.build()),
            new DefaultTargetNodeToBuildRuleTransformer());

    py2LibBuilder.build(resolver);
    py3LibBuilder.build(resolver);
    py2CxxLibraryBuilder.build(resolver);
    py3CxxLibraryBuilder.build(resolver);
    cxxPythonExtensionBuilder.build(resolver);
    LuaBinary luaBinary = (LuaBinary) luaBinaryBuilder.build(resolver);

    LuaPackageComponents components = luaBinary.getComponents();
    assertThat(
        components.getNativeLibraries().keySet(),
        Matchers.hasItem("libpy2.so"));
    assertThat(
        components.getNativeLibraries().keySet(),
        Matchers.not(Matchers.hasItem("libpy3.so")));
  }

}
