/*
 * Copyright 2016-present Facebook, Inc.
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

import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.step.fs.RmStep;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Builds a Lua executable into a standalone package using a given packager tool.
 */
public class LuaStandaloneBinary extends AbstractBuildRule {

  @AddToRuleKey
  private final Tool builder;

  @AddToRuleKey
  private final ImmutableList<String> builderArgs;

  @AddToRuleKey(stringify = true)
  private final Path output;

  @AddToRuleKey
  private final Optional<SourcePath> nativeStarter;

  @AddToRuleKey
  private final LuaPackageComponents components;

  @AddToRuleKey
  private final String mainModule;

  @AddToRuleKey
  private final Tool lua;

  private final boolean cache;

  public LuaStandaloneBinary(
      BuildRuleParams buildRuleParams,
      SourcePathResolver resolver,
      Tool builder,
      ImmutableList<String> builderArgs,
      Path output,
      Optional<SourcePath> nativeStarter,
      LuaPackageComponents components,
      String mainModule,
      Tool lua,
      boolean cache) {
    super(buildRuleParams, resolver);
    this.builder = builder;
    this.builderArgs = builderArgs;
    this.output = output;
    this.nativeStarter = nativeStarter;
    this.components = components;
    this.mainModule = mainModule;
    this.lua = lua;
    this.cache = cache;
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context,
      BuildableContext buildableContext) {

    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    buildableContext.recordArtifact(output);

    // Make sure the parent directory exists.
    steps.add(new MkdirStep(getProjectFilesystem(), output.getParent()));

    // Delete any other pex that was there (when switching between pex styles).
    steps.add(new RmStep(getProjectFilesystem(), output, /* force */ true, /* recurse */ true));

    steps.add(
        new ShellStep(getProjectFilesystem().getRootPath()) {

          @Override
          protected Optional<String> getStdin(ExecutionContext context) {
            try {
              return Optional.of(
                  context.getObjectMapper().writeValueAsString(
                      ImmutableMap.of(
                          "modules",
                          Maps.transformValues(
                              components.getModules(),
                              Functions.compose(
                                  Functions.toStringFunction(),
                                  getResolver().getAbsolutePathFunction())),
                          "pythonModules",
                          Maps.transformValues(
                              components.getPythonModules(),
                              Functions.compose(
                                  Functions.toStringFunction(),
                                  getResolver().getAbsolutePathFunction())),
                          "nativeLibraries",
                          Maps.transformValues(
                              components.getNativeLibraries(),
                              Functions.compose(
                                  Functions.toStringFunction(),
                                  getResolver().getAbsolutePathFunction())))));
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }

          @Override
          protected ImmutableList<String> getShellCommandInternal(ExecutionContext context) {
            ImmutableList.Builder<String> command = ImmutableList.builder();
            command.addAll(builder.getCommandPrefix(getResolver()));
            command.addAll(builderArgs);
            command.add("--entry-point", mainModule);
            command.add("--interpreter");
            if (nativeStarter.isPresent()) {
              command.add(getResolver().getAbsolutePath(nativeStarter.get()).toString());
            } else {
              command.add(lua.getCommandPrefix(getResolver()).get(0));
            }
            command.add(getProjectFilesystem().resolve(output).toString());
            return command.build();
          }

          @Override
          public String getShortName() {
            return "lua_package";
          }

        });


    return steps.build();
  }

  @Override
  public Path getPathToOutput() {
    return output;
  }

  @Override
  public boolean isCacheable() {
    return cache;
  }

}
