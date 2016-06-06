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

package com.facebook.buck.shell;

import com.facebook.buck.rules.BinaryBuildRule;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.HasRuntimeDeps;
import com.facebook.buck.rules.NoopBuildRule;
import com.facebook.buck.rules.SourcePathResolver;
import com.google.common.collect.ImmutableSortedSet;

public class WorkerTool extends NoopBuildRule implements HasRuntimeDeps {

  private final BinaryBuildRule exe;
  private final Iterable<BuildRule> depsFromStartupArgs;
  private final String args;

  protected WorkerTool(
      BuildRuleParams ruleParams,
      SourcePathResolver resolver,
      BinaryBuildRule exe,
      Iterable<BuildRule> depsFromStartupArgs,
      String args) {
    super(ruleParams, resolver);
    this.exe = exe;
    this.depsFromStartupArgs = depsFromStartupArgs;
    this.args = args;
  }

  public BinaryBuildRule getBinaryBuildRule() {
    return this.exe;
  }

  public String getArgs() {
    return this.args;
  }

  @Override
  public ImmutableSortedSet<BuildRule> getRuntimeDeps() {
    return ImmutableSortedSet.<BuildRule>naturalOrder()
        .add(exe)
        .addAll(exe.getExecutableCommand().getDeps(getResolver()))
        .addAll(depsFromStartupArgs)
        .build();
  }
}
