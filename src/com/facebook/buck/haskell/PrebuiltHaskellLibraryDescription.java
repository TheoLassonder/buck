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

import com.facebook.buck.cxx.CxxHeadersDir;
import com.facebook.buck.cxx.CxxPlatform;
import com.facebook.buck.cxx.CxxPreprocessables;
import com.facebook.buck.cxx.CxxPreprocessorDep;
import com.facebook.buck.cxx.CxxPreprocessorInput;
import com.facebook.buck.cxx.CxxSourceRuleFactory;
import com.facebook.buck.cxx.HeaderSymlinkTree;
import com.facebook.buck.cxx.HeaderVisibility;
import com.facebook.buck.cxx.ImmutableCxxPreprocessorInputCacheKey;
import com.facebook.buck.cxx.Linker;
import com.facebook.buck.cxx.NativeLinkable;
import com.facebook.buck.cxx.NativeLinkableInput;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.parser.NoSuchBuildTargetException;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildRuleType;
import com.facebook.buck.rules.Description;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.TargetGraph;
import com.facebook.buck.rules.args.SourcePathArg;
import com.facebook.buck.rules.args.StringArg;
import com.facebook.infer.annotation.SuppressFieldNotInitialized;
import com.google.common.base.Optional;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;

public class PrebuiltHaskellLibraryDescription
    implements Description<PrebuiltHaskellLibraryDescription.Arg> {

  private static final BuildRuleType TYPE = BuildRuleType.of("haskell_prebuilt_library");

  @Override
  public BuildRuleType getBuildRuleType() {
    return TYPE;
  }

  @Override
  public Arg createUnpopulatedConstructorArg() {
    return new Arg();
  }

  @Override
  public <A extends Arg> BuildRule createBuildRule(
      TargetGraph targetGraph,
      BuildRuleParams params,
      BuildRuleResolver resolver,
      final A args) throws NoSuchBuildTargetException {
    SourcePathResolver pathResolver = new SourcePathResolver(resolver);
    return new PrebuiltHaskellLibrary(params, pathResolver) {

      private final LoadingCache<
                  CxxPreprocessables.CxxPreprocessorInputCacheKey,
                  ImmutableMap<BuildTarget, CxxPreprocessorInput>
              > transitiveCxxPreprocessorInputCache =
          CxxPreprocessables.getTransitiveCxxPreprocessorInputCache(this);

      @Override
      public HaskellCompileInput getCompileInput(
          CxxPlatform cxxPlatform,
          CxxSourceRuleFactory.PicType picType) throws NoSuchBuildTargetException {
        return HaskellCompileInput.builder()
            .addAllFlags(args.exportedCompilerFlags.or(ImmutableList.<String>of()))
            .addAllIncludes(
                picType == CxxSourceRuleFactory.PicType.PDC ?
                    args.staticInterfaces.asSet() :
                    args.sharedInterfaces.asSet())
            .build();
      }

      @Override
      public Iterable<? extends NativeLinkable> getNativeLinkableDeps(CxxPlatform cxxPlatform) {
        return ImmutableList.of();
      }

      @Override
      public Iterable<? extends NativeLinkable> getNativeLinkableExportedDeps(
          CxxPlatform cxxPlatform) {
        return FluentIterable.from(getDeclaredDeps())
            .filter(NativeLinkable.class);
      }

      @Override
      public NativeLinkableInput getNativeLinkableInput(
          CxxPlatform cxxPlatform,
          Linker.LinkableDepType type) {
        NativeLinkableInput.Builder builder = NativeLinkableInput.builder();
        builder.addAllArgs(StringArg.from(args.exportedLinkerFlags.or(ImmutableList.<String>of())));
        if (type == Linker.LinkableDepType.SHARED) {
          builder.addAllArgs(
              SourcePathArg.from(getResolver(),
                  args.sharedLibs.or(ImmutableMap.<String, SourcePath>of()).values()));
        } else {
          builder.addAllArgs(
              SourcePathArg.from(getResolver(),
                  args.staticLibs.or(ImmutableList.<SourcePath>of())));
        }
        return builder.build();
      }

      @Override
      public Linkage getPreferredLinkage(CxxPlatform cxxPlatform) {
        return Linkage.ANY;
      }

      @Override
      public ImmutableMap<String, SourcePath> getSharedLibraries(CxxPlatform cxxPlatform) {
        return args.sharedLibs.or(ImmutableMap.<String, SourcePath>of());
      }

      @Override
      public Iterable<? extends CxxPreprocessorDep> getCxxPreprocessorDeps(
          CxxPlatform cxxPlatform) {
        return FluentIterable.from(getDeps())
            .filter(CxxPreprocessorDep.class);
      }

      @Override
      public Optional<HeaderSymlinkTree> getExportedHeaderSymlinkTree(CxxPlatform cxxPlatform) {
        return Optional.absent();
      }

      @Override
      public CxxPreprocessorInput getCxxPreprocessorInput(
          CxxPlatform cxxPlatform,
          HeaderVisibility headerVisibility)
          throws NoSuchBuildTargetException {
        CxxPreprocessorInput.Builder builder = CxxPreprocessorInput.builder();
        for (SourcePath headerDir : args.cxxHeaderDirs.or(ImmutableSortedSet.<SourcePath>of())) {
          builder.addIncludes(CxxHeadersDir.of(CxxPreprocessables.IncludeType.SYSTEM, headerDir));
        }
        return builder.build();
      }

      @Override
      public ImmutableMap<BuildTarget, CxxPreprocessorInput> getTransitiveCxxPreprocessorInput(
          CxxPlatform cxxPlatform,
          HeaderVisibility headerVisibility)
          throws NoSuchBuildTargetException {
        return transitiveCxxPreprocessorInputCache.getUnchecked(
            ImmutableCxxPreprocessorInputCacheKey.of(cxxPlatform, headerVisibility));
      }

    };
  }

  @SuppressFieldNotInitialized
  public class Arg {
    public Optional<SourcePath> staticInterfaces;
    public Optional<SourcePath> sharedInterfaces;
    public Optional<ImmutableList<SourcePath>> staticLibs;
    public Optional<ImmutableMap<String, SourcePath>> sharedLibs;
    public Optional<ImmutableSortedSet<BuildTarget>> deps;
    public Optional<ImmutableList<String>> exportedLinkerFlags;
    public Optional<ImmutableList<String>> exportedCompilerFlags;
    public Optional<ImmutableSortedSet<SourcePath>> cxxHeaderDirs;
  }

}
