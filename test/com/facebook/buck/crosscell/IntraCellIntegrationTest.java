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

package com.facebook.buck.crosscell;


import static org.junit.Assert.fail;

import com.facebook.buck.event.BuckEventBusFactory;
import com.facebook.buck.json.BuildFileParseException;
import com.facebook.buck.model.BuildTargetException;
import com.facebook.buck.model.BuildTargetFactory;
import com.facebook.buck.parser.Parser;
import com.facebook.buck.parser.ParserConfig;
import com.facebook.buck.rules.Cell;
import com.facebook.buck.rules.ConstructorArgMarshaller;
import com.facebook.buck.rules.coercer.DefaultTypeCoercerFactory;
import com.facebook.buck.rules.coercer.TypeCoercerFactory;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TemporaryPaths;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.ObjectMappers;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.Executors;

public class IntraCellIntegrationTest {

  @Rule
  public TemporaryPaths tmp = new TemporaryPaths();

  @Test
  @Ignore
  public void shouldTreatACellBoundaryAsAHardBuckPackageBoundary() {

  }

  @SuppressWarnings("PMD.EmptyCatchBlock")
  @Test
  public void shouldTreatCellBoundariesAsVisibilityBoundariesToo()
      throws IOException, InterruptedException, BuildFileParseException, BuildTargetException {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this,
        "intracell/visibility",
        tmp);
    workspace.setUp();

    // We don't need to do a build. It's enough to just parse these things.
    Cell cell = workspace.asCell();

    TypeCoercerFactory coercerFactory = new DefaultTypeCoercerFactory(
        ObjectMappers.newDefaultInstance());
    Parser parser = new Parser(
        new ParserConfig(cell.getBuckConfig()),
        coercerFactory,
        new ConstructorArgMarshaller(coercerFactory));

    // This parses cleanly
    parser.buildTargetGraph(
        BuckEventBusFactory.newInstance(),
        cell,
        false,
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
        ImmutableSet.of(BuildTargetFactory.newInstance(
            cell.getFilesystem(),
            "//just-a-directory:rule")));

    Cell childCell = cell.getCell(BuildTargetFactory.newInstance(
        workspace.getDestPath().resolve("child-repo"),
        "//:child-target"));

    try {
      // Whereas, because visibility is limited to the same cell, this won't.
      parser.buildTargetGraph(
          BuckEventBusFactory.newInstance(),
          childCell,
          false,
          MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
          ImmutableSet.of(BuildTargetFactory.newInstance(
              childCell.getFilesystem(),
              "//:child-target")));
      fail("Didn't expect parsing to work because of visibility");
    } catch (HumanReadableException e) {
      // This is expected
    }
  }

  @Test
  @Ignore
  public void allOutputsShouldBePlacedInTheSameRootOutputDirectory() {

  }

}