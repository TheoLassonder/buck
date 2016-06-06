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

package com.facebook.buck.haskell;

import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.AbstractNodeBuilder;
import com.facebook.buck.rules.SourcePath;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;

public class PrebuiltHaskellLibraryBuilder
    extends AbstractNodeBuilder<PrebuiltHaskellLibraryDescription.Arg> {

  public PrebuiltHaskellLibraryBuilder(BuildTarget target) {
    super(
        new PrebuiltHaskellLibraryDescription(),
        target);
  }

  public PrebuiltHaskellLibraryBuilder setStaticInterfaces(SourcePath interfaces) {
    arg.staticInterfaces = Optional.of(interfaces);
    return this;
  }

  public PrebuiltHaskellLibraryBuilder setSharedInterfaces(SourcePath interfaces) {
    arg.sharedInterfaces = Optional.of(interfaces);
    return this;
  }

  public PrebuiltHaskellLibraryBuilder setStaticLibs(ImmutableList<SourcePath> libs) {
    arg.staticLibs = Optional.of(libs);
    return this;
  }

  public PrebuiltHaskellLibraryBuilder setSharedLibs(ImmutableMap<String, SourcePath> libs) {
    arg.sharedLibs = Optional.of(libs);
    return this;
  }

  public PrebuiltHaskellLibraryBuilder setExportedLinkerFlags(ImmutableList<String> flags) {
    arg.exportedLinkerFlags = Optional.of(flags);
    return this;
  }

  public PrebuiltHaskellLibraryBuilder setExportedCompilerFlags(ImmutableList<String> flags) {
    arg.exportedCompilerFlags = Optional.of(flags);
    return this;
  }

  public PrebuiltHaskellLibraryBuilder setCxxHeaderDirs(
      ImmutableSortedSet<SourcePath> cxxHeaderDirs) {
    arg.cxxHeaderDirs = Optional.of(cxxHeaderDirs);
    return this;
  }

  public PrebuiltHaskellLibraryBuilder setDeps(ImmutableSortedSet<BuildTarget> deps) {
    arg.deps = Optional.of(deps);
    return this;
  }

}
