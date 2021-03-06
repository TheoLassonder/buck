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

package com.facebook.buck.android;

import com.facebook.buck.cxx.ClangCompiler;
import com.facebook.buck.cxx.ClangPreprocessor;
import com.facebook.buck.cxx.DefaultCompiler;
import com.facebook.buck.cxx.DefaultPreprocessor;
import com.facebook.buck.cxx.Preprocessor;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.util.immutables.BuckStyleImmutable;

import org.immutables.value.Value;

@Value.Immutable
@BuckStyleImmutable
interface AbstractNdkCxxPlatformCompiler {

  Type getType();

  /**
   * @return the compiler version, corresponding to either `gcc_version` or `clang_version`
   * from the .buckconfig settings, depending on which compiler family was selected.
   */
  String getVersion();

  /**
   * @return the GCC compiler version.  Since even runtimes which are not GCC-based need to use
   * GCC tools (e.g. ar, as,, ld.gold), we need to *always* have a version of GCC.
   */
  String getGccVersion();

  enum Type {

    GCC("gcc", "gcc", "g++"),
    CLANG("clang", "clang", "clang++"),
    ;

    private final String name;
    private final String cc;
    private final String cxx;

    Type(String name, String cc, String cxx) {
      this.name = name;
      this.cc = cc;
      this.cxx = cxx;
    }

    public String getName() {
      return name;
    }

    public String getCc() {
      return cc;
    }

    public String getCxx() {
      return cxx;
    }

    public com.facebook.buck.cxx.Compiler compilerFromTool(Tool tool) {
      switch (this) {
        case GCC:
          return new DefaultCompiler(tool);
        case CLANG:
          return new ClangCompiler(tool);
      }
      throw new RuntimeException("Invalid compiler type");
    }

    public Preprocessor preprocessorFromTool(Tool tool) {
      switch (this) {
        case GCC:
          return new DefaultPreprocessor(tool);
        case CLANG:
          return new ClangPreprocessor(tool);
      }
      throw new RuntimeException("Invalid compiler type");
    }

  }

}
