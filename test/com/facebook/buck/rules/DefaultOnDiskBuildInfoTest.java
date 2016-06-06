/*
 * Copyright 2013-present Facebook, Inc.
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

package com.facebook.buck.rules;

import static org.junit.Assert.assertThat;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.util.ObjectMappers;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;

public class DefaultOnDiskBuildInfoTest {

  @Test
  public void whenMetadataEmptyStringThenGetValueReturnsEmptyString() throws IOException {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    projectFilesystem.writeContentsToPath("", Paths.get("buck-out/bin/foo/bar/.baz/metadata/KEY"));

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo/bar:baz");
    DefaultOnDiskBuildInfo onDiskBuildInfo =
        new DefaultOnDiskBuildInfo(
            buildTarget,
            projectFilesystem,
            ObjectMappers.newDefaultInstance());
    assertThat(
        onDiskBuildInfo.getValue("KEY"),
        Matchers.equalTo(Optional.of("")));
  }

  @Test
   public void whenMetaDataJsonListThenGetValuesReturnsList() throws IOException {
    FakeProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    projectFilesystem.writeContentsToPath(
        "[\"bar\",\"biz\",\"baz\"]",
        Paths.get("buck-out/bin/foo/bar/.baz/metadata/KEY"));

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo/bar:baz");
    DefaultOnDiskBuildInfo onDiskBuildInfo =
        new DefaultOnDiskBuildInfo(
            buildTarget,
            projectFilesystem,
            ObjectMappers.newDefaultInstance());
    assertThat(
        onDiskBuildInfo.getValues("KEY"),
        Matchers.equalTo(Optional.of(ImmutableList.of("bar", "biz", "baz"))));
  }

  @Test
  public void whenMetaDataEmptyJsonListThenGetValuesReturnsEmptyList() throws IOException {
    FakeProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    projectFilesystem.writeContentsToPath(
        "[]",
        Paths.get("buck-out/bin/foo/bar/.baz/metadata/KEY"));

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo/bar:baz");
    DefaultOnDiskBuildInfo onDiskBuildInfo =
        new DefaultOnDiskBuildInfo(
            buildTarget,
            projectFilesystem,
            ObjectMappers.newDefaultInstance());
    assertThat(
        onDiskBuildInfo.getValues("KEY"),
        Matchers.equalTo(Optional.of(ImmutableList.<String>of())));
  }

  @Test
  public void whenMetadataEmptyStringThenGetValuesReturnsAbsent() throws IOException {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    projectFilesystem.writeContentsToPath("", Paths.get("buck-out/bin/foo/bar/.baz/metadata/KEY"));

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo/bar:baz");
    DefaultOnDiskBuildInfo onDiskBuildInfo =
        new DefaultOnDiskBuildInfo(
            buildTarget,
            projectFilesystem,
            ObjectMappers.newDefaultInstance());
    assertThat(
        onDiskBuildInfo.getValues("KEY"),
        Matchers.equalTo(Optional.<ImmutableList<String>>absent()));
  }

  @Test
  public void whenMetadataInvalidJsonThenGetValuesReturnsAbsent() throws IOException {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    projectFilesystem.writeContentsToPath(
        "Some Invalid Json",
        Paths.get("buck-out/bin/foo/bar/.baz/metadata/KEY"));

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo/bar:baz");
    DefaultOnDiskBuildInfo onDiskBuildInfo =
        new DefaultOnDiskBuildInfo(
            buildTarget,
            projectFilesystem,
            ObjectMappers.newDefaultInstance());
    assertThat(
        onDiskBuildInfo.getValues("KEY"),
        Matchers.equalTo(Optional.<ImmutableList<String>>absent()));
  }

  @Test
  public void whenMetadataValidHashThenGetHashReturnsHash() throws IOException {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    String hash = "fac0fac1fac2fac3fac4fac5fac6fac7fac8fac9";
    projectFilesystem.writeContentsToPath(
        hash,
        Paths.get("buck-out/bin/foo/bar/.baz/metadata/KEY"));

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo/bar:baz");
    DefaultOnDiskBuildInfo onDiskBuildInfo =
        new DefaultOnDiskBuildInfo(
            buildTarget,
            projectFilesystem,
            ObjectMappers.newDefaultInstance());
    assertThat(
        onDiskBuildInfo.getHash("KEY"),
        Matchers.equalTo(Optional.of(Sha1HashCode.of(hash))));
  }

  @Test
  public void whenMetadataEmptyStringThenGetHashReturnsAbsent() throws IOException {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    projectFilesystem.writeContentsToPath("", Paths.get("buck-out/bin/foo/bar/.baz/metadata/KEY"));

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo/bar:baz");
    DefaultOnDiskBuildInfo onDiskBuildInfo =
        new DefaultOnDiskBuildInfo(
            buildTarget,
            projectFilesystem,
            ObjectMappers.newDefaultInstance());
    assertThat(
        onDiskBuildInfo.getHash("KEY"),
        Matchers.equalTo(Optional.<Sha1HashCode>absent()));
  }

  @Test
  public void whenMetadataInvalidHashThenGetHashReturnsAbsent() throws IOException {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    projectFilesystem.writeContentsToPath(
        "Not A Valid Hash",
        Paths.get("buck-out/bin/foo/bar/.baz/metadata/KEY"));

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo/bar:baz");
    DefaultOnDiskBuildInfo onDiskBuildInfo =
        new DefaultOnDiskBuildInfo(
            buildTarget,
            projectFilesystem,
            ObjectMappers.newDefaultInstance());
    assertThat(
        onDiskBuildInfo.getHash("KEY"),
        Matchers.equalTo(Optional.<Sha1HashCode>absent()));
  }

  @Test
  public void whenMetadataValidRuleKeyThenGetRuleKeyReturnsKey() throws IOException {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    String key = "fa";
    projectFilesystem.writeContentsToPath(
        key,
        Paths.get("buck-out/bin/foo/bar/.baz/metadata/" + BuildInfo.METADATA_KEY_FOR_RULE_KEY));

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo/bar:baz");
    DefaultOnDiskBuildInfo onDiskBuildInfo =
        new DefaultOnDiskBuildInfo(
            buildTarget,
            projectFilesystem,
            ObjectMappers.newDefaultInstance());
    assertThat(
        onDiskBuildInfo.getRuleKey(BuildInfo.METADATA_KEY_FOR_RULE_KEY),
        Matchers.equalTo(Optional.of(new RuleKey(key))));
  }

  @Test
  public void whenMetadataEmptyStringThenGetRuleKeyReturnsAbsent() throws IOException {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    projectFilesystem.writeContentsToPath(
        "",
        Paths.get("buck-out/bin/foo/bar/.baz/metadata/" + BuildInfo.METADATA_KEY_FOR_RULE_KEY));

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo/bar:baz");
    DefaultOnDiskBuildInfo onDiskBuildInfo =
        new DefaultOnDiskBuildInfo(
            buildTarget,
            projectFilesystem,
            ObjectMappers.newDefaultInstance());
    assertThat(
        onDiskBuildInfo.getRuleKey(BuildInfo.METADATA_KEY_FOR_RULE_KEY),
        Matchers.equalTo(Optional.<RuleKey>absent()));
  }

  @Test
  public void whenMetadataInvalidRuleKeyThenGetRuleKeyReturnsAbsent() throws IOException {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    projectFilesystem.writeContentsToPath(
        "Not A Valid Rule Key",
        Paths.get("buck-out/bin/foo/bar/.baz/metadata/" + BuildInfo.METADATA_KEY_FOR_RULE_KEY));

    BuildTarget buildTarget = BuildTargetFactory.newInstance("//foo/bar:baz");
    DefaultOnDiskBuildInfo onDiskBuildInfo =
        new DefaultOnDiskBuildInfo(
            buildTarget,
            projectFilesystem,
            ObjectMappers.newDefaultInstance());
    assertThat(
        onDiskBuildInfo.getRuleKey(BuildInfo.METADATA_KEY_FOR_RULE_KEY),
        Matchers.equalTo(Optional.<RuleKey>absent()));
  }

}
