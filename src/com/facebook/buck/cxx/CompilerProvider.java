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

package com.facebook.buck.cxx;

import com.facebook.buck.rules.Tool;
import com.facebook.buck.rules.ToolProvider;
import com.google.common.base.Optional;

import java.nio.file.Path;

public class CompilerProvider extends CxxToolProvider<Compiler> {

  public CompilerProvider(ToolProvider toolProvider, Type type) {
    super(toolProvider, type);
  }

  public CompilerProvider(
      Path path,
      Optional<Type> type) {
    super(path, type);
  }

  @Override
  protected Compiler build(CxxToolProvider.Type type, Tool tool) {
    switch (type) {
      case CLANG:
        return new ClangCompiler(tool);
      case DEFAULT:
        return new DefaultCompiler(tool);
    }
    throw new IllegalStateException();
  }

}
