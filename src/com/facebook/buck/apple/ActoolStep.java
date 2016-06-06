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

package com.facebook.buck.apple;

import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.ExecutionContext;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import java.nio.file.Path;
import java.util.List;
import java.util.SortedSet;

class ActoolStep extends ShellStep {

  private final String applePlatformName;
  private final ImmutableMap<String, String> environment;
  private final ImmutableList<String> actoolCommand;
  private final SortedSet<Path> assetCatalogDirs;
  private final Path output;
  private final Path outputPlist;
  private final Optional<String> appIcon;
  private final Optional<String> launchImage;

  public ActoolStep(
      Path workingDirectory,
      String applePlatformName,
      ImmutableMap<String, String> environment,
      List<String> actoolCommand,
      SortedSet<Path> assetCatalogDirs,
      Path output,
      Path outputPlist,
      Optional<String> appIcon,
      Optional<String> launchImage) {
    super(workingDirectory);
    this.applePlatformName = applePlatformName;
    this.environment = environment;
    this.actoolCommand = ImmutableList.copyOf(actoolCommand);
    this.assetCatalogDirs = assetCatalogDirs;
    this.output = output;
    this.outputPlist = outputPlist;
    this.appIcon = appIcon;
    this.launchImage = launchImage;
  }

  @Override
  protected ImmutableList<String> getShellCommandInternal(ExecutionContext context) {
    ImmutableList.Builder<String> commandBuilder = ImmutableList.builder();

    //TODO(k21): Let apps select their minimum target.
    String target = "7.0";

    commandBuilder.addAll(actoolCommand);
    commandBuilder.add(
        "--output-format", "human-readable-text",
        "--notices",
        "--warnings",
        "--errors",
        "--platform", applePlatformName,
        "--minimum-deployment-target", target,
        //TODO(k21): Let apps decide which device they want to target (iPhone / iPad / both)
        "--target-device", "iphone",
        "--target-device", "ipad",
        "--compress-pngs",
        "--compile",
        output.toString(),
        "--output-partial-info-plist",
        outputPlist.toString());

    if (appIcon.isPresent()) {
      commandBuilder.add("--app-icon", appIcon.get());
    }

    if (launchImage.isPresent()) {
      commandBuilder.add("--launch-image", launchImage.get());
    }

    commandBuilder.addAll(Iterables.transform(assetCatalogDirs, Functions.toStringFunction()));

    return commandBuilder.build();
  }

  @Override
  public ImmutableMap<String, String> getEnvironmentVariables(ExecutionContext context) {
    return environment;
  }

  @Override
  public String getShortName() {
    return "actool";
  }
}
