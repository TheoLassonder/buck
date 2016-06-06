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

package com.facebook.buck.rules.keys;

import com.facebook.buck.hashing.FileHashLoader;
import com.facebook.buck.rules.ArchiveMemberSourcePath;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.DependencyAggregation;
import com.facebook.buck.rules.RuleKey;
import com.facebook.buck.rules.RuleKeyAppendable;
import com.facebook.buck.rules.RuleKeyBuilder;
import com.facebook.buck.rules.SourcePath;
import com.facebook.buck.rules.SourcePathResolver;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import java.io.IOException;
import java.util.Collections;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A factory for generating input-based {@link RuleKey}s.
 *
 * @see SupportsInputBasedRuleKey
 */
public class InputBasedRuleKeyBuilderFactory
    extends ReflectiveRuleKeyBuilderFactory<
        InputBasedRuleKeyBuilderFactory.Builder,
        Optional<RuleKey>> {

  private final FileHashLoader fileHashLoader;
  private final SourcePathResolver pathResolver;
  private final ArchiveHandling archiveHandling;
  private final InputHandling inputHandling;
  private final LoadingCache<RuleKeyAppendable, Result> cache;

  protected InputBasedRuleKeyBuilderFactory(
      int seed,
      FileHashLoader hashLoader,
      SourcePathResolver pathResolver,
      InputHandling inputHandling,
      ArchiveHandling archiveHandling) {
    super(seed);
    this.fileHashLoader = hashLoader;
    this.pathResolver = pathResolver;
    this.inputHandling = inputHandling;
    this.archiveHandling = archiveHandling;

    // Build the cache around the sub-rule-keys and their dep lists.
    cache = CacheBuilder.newBuilder().weakKeys().build(
        new CacheLoader<RuleKeyAppendable, Result>() {
          @Override
          public Result load(
              @Nonnull RuleKeyAppendable appendable) {
            Builder subKeyBuilder = new Builder();
            appendable.appendToRuleKey(subKeyBuilder);
            return subKeyBuilder.buildResult();
          }
        });
  }

  public InputBasedRuleKeyBuilderFactory(
      int seed,
      FileHashLoader hashLoader,
      SourcePathResolver pathResolver) {
    this(seed, hashLoader, pathResolver, InputHandling.HASH, ArchiveHandling.ARCHIVES);
  }

  @Override
  protected Builder newBuilder(final BuildRule rule) {
    final Iterable<DependencyAggregation> aggregatedRules =
        Iterables.filter(rule.getDeps(), DependencyAggregation.class);
    return new Builder() {
      private boolean hasEffectiveDirectDep(BuildRule dep) {
        for (BuildRule aggregationRule : aggregatedRules) {
          if (aggregationRule.getDeps().contains(dep)) {
            return true;
          }
        }
        return false;
      }

      // Construct the rule key, verifying that all the deps we saw when constructing it
      // are explicit dependencies of the rule.
      @Override
      public Optional<RuleKey> build() {
        Result result = buildResult();
        for (BuildRule usedDep : result.getDeps()) {
          Preconditions.checkState(
              rule.getDeps().contains(usedDep) || hasEffectiveDirectDep(usedDep),
              "%s: %s not in deps (%s)",
              rule.getBuildTarget(),
              usedDep.getBuildTarget(),
              rule.getDeps());
        }
        return Optional.of(result.getRuleKey());
      }

    };
  }

  public class Builder extends RuleKeyBuilder<Optional<RuleKey>> {

    private final ImmutableList.Builder<Iterable<BuildRule>> deps = ImmutableList.builder();
    private final ImmutableList.Builder<Iterable<SourcePath>> inputs = ImmutableList.builder();

    private Builder() {
      super(pathResolver, fileHashLoader);
    }

    @Override
    public Builder setAppendableRuleKey(String key, RuleKeyAppendable appendable) {
      Result result = cache.getUnchecked(appendable);
      deps.add(result.getDeps());
      inputs.add(result.getInputs());
      setAppendableRuleKey(key, result.getRuleKey());
      return this;
    }

    @Override
    public Builder setReflectively(String key, @Nullable Object val) {
      if (val instanceof ArchiveDependencySupplier &&
          archiveHandling == ArchiveHandling.MEMBERS) {
        super.setReflectively(
            key,
            ((ArchiveDependencySupplier) val).getArchiveMembers(pathResolver));
      } else {
        super.setReflectively(key, val);
      }

      return this;
    }

    // Input-based rule keys are evaluated after all dependencies for a rule are available on
    // disk, and so we can always resolve the `Path` packaged in a `SourcePath`.  We hash this,
    // rather than the rule key from it's `BuildRule`.
    @Override
    protected Builder setSourcePath(SourcePath sourcePath) {
      if (inputHandling == InputHandling.HASH) {
        deps.add(pathResolver.getRule(sourcePath).asSet());

        try {
          if (sourcePath instanceof ArchiveMemberSourcePath) {
            setArchiveMemberPath(
                pathResolver.getAbsoluteArchiveMemberPath(sourcePath),
                pathResolver.getRelativeArchiveMemberPath(sourcePath));
          } else {
            setPath(
                pathResolver.getAbsolutePath(sourcePath),
                pathResolver.getRelativePath(sourcePath));
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      inputs.add(Collections.singleton(sourcePath));
      return this;
    }

    // Rules supporting input-based rule keys should be described entirely by their `SourcePath`
    // inputs.  If we see a `BuildRule` when generating the rule key, this is likely a break in
    // that contract, so check for that.
    @Override
    protected Builder setBuildRule(BuildRule rule) {
      throw new IllegalStateException(
          String.format(
              "Input-based rule key builders cannot process build rules. " +
                  "Was given %s to add to rule key.",
              rule));
    }

    protected ImmutableSet<SourcePath> getInputsSoFar() {
      return ImmutableSet.copyOf(Iterables.concat(inputs.build()));
    }

    // Build the rule key and the list of deps found from this builder.
    protected Result buildResult() {
      return new Result(
          buildRuleKey(),
          Iterables.concat(deps.build()),
          Iterables.concat(inputs.build()));
    }

    @Override
    public Optional<RuleKey> build() {
      return Optional.of(buildRuleKey());
    }

  }

  /**
   * How to handle adding {@link SourcePath}s to the {@link RuleKey}.
   */
  protected enum InputHandling {

    /**
     * Hash the contents of {@link SourcePath}s.
     */
    HASH,

    /**
     * Ignore {@link SourcePath}s.  This is useful for implementing handling for dependency files,
     * where the list of inputs will be provided explicitly.
     */
    IGNORE,

  }

  /**
   * How to handle adding {@link ArchiveDependencySupplier}s to the {@link RuleKey}.
   */
  protected enum ArchiveHandling {

    /**
     * Add the archives (call {@link ArchiveDependencySupplier#get()}).
     */
    ARCHIVES,

    /**
     * Add all the members of the archives
     * (call {@link ArchiveDependencySupplier#getArchiveMembers(SourcePathResolver)}).
     */
    MEMBERS,
  }

  protected static class Result {

    private final RuleKey ruleKey;
    private final Iterable<BuildRule> deps;
    private final Iterable<SourcePath> inputs;

    public Result(
        RuleKey ruleKey,
        Iterable<BuildRule> deps,
        Iterable<SourcePath> inputs) {
      this.ruleKey = ruleKey;
      this.deps = deps;
      this.inputs = inputs;
    }

    public RuleKey getRuleKey() {
      return ruleKey;
    }

    public Iterable<BuildRule> getDeps() {
      return deps;
    }

    public Iterable<SourcePath> getInputs() {
      return inputs;
    }

  }

}
