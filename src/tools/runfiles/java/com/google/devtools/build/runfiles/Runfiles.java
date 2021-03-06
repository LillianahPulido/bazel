// Copyright 2018 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.runfiles;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Returns the runtime location of runfiles (data-dependencies of Bazel-built binaries and tests).
 *
 * <p>Usage:
 *
 * <pre>
 *   Runfiles runfiles = Runfiles.create();
 *   File p = new File(runfiles.rlocation("io_bazel/src/bazel"));
 * </pre>
 */
public abstract class Runfiles {

  // Package-private constructor, so only package-private classes may extend it.
  Runfiles() {}

  /**
   * Returns a new {@link Runfiles} instance.
   *
   * <p>This method passes the JVM's environment variable map to {@link #create(Map)}.
   */
  public static Runfiles create() throws IOException {
    return create(System.getenv());
  }

  /**
   * Returns a new {@link Runfiles} instance.
   *
   * <p>The returned object is either:
   *
   * <ul>
   *   <li>manifest-based, meaning it looks up runfile paths from a manifest file, or
   *   <li>directory-based, meaning it looks up runfile paths under a given directory path
   * </ul>
   *
   * <p>If {@code env} contains "RUNFILES_MANIFEST_ONLY" with value "1", this method returns a
   * manifest-based implementation. The manifest's path is defined by the "RUNFILES_MANIFEST_FILE"
   * key's value in {@code env}.
   *
   * <p>Otherwise this method returns a directory-based implementation. The directory's path is
   * defined by the "RUNFILES_DIR" or "TEST_SRCDIR" key's value in {@code env}, in this priority
   * order.
   *
   * <p>Note about performance: the manifest-based implementation eagerly reads and caches the whole
   * manifest file upon instantiation.
   *
   * @throws IOException if RUNFILES_MANIFEST_ONLY=1 is in {@code env} but there's no
   *     "RUNFILES_MANIFEST_FILE", or if neither "RUNFILES_DIR" nor "TEST_SRCDIR" is in {@code env},
   *     or if some IO error occurs
   */
  public static Runfiles create(Map<String, String> env) throws IOException {
    if (isManifestOnly(env)) {
      // On Windows, Bazel sets RUNFILES_MANIFEST_ONLY=1.
      // On every platform, Bazel also sets RUNFILES_MANIFEST_FILE, but on Linux and macOS it's
      // faster to use RUNFILES_DIR.
      return new ManifestBased(getManifestPath(env));
    } else {
      return new DirectoryBased(getRunfilesDir(env));
    }
  }

  /**
   * Returns the runtime path of a runfile (a Bazel-built binary's/test's data-dependency).
   *
   * <p>The caller should check that the returned path exists. A null return value means the rule
   * definitely doesn't know about this data-dependency. A non-null return value means no guarantee
   * though that the file would exist.
   *
   * @param path runfiles-root-relative path of the runfile
   */
  @Nullable
  public final String rlocation(String path) {
    Preconditions.checkArgument(path != null);
    Preconditions.checkArgument(!path.isEmpty());
    Preconditions.checkArgument(
        !path.contains(".."), "path contains uplevel references: \"%s\"", path);
    Preconditions.checkArgument(
        !new File(path).isAbsolute() && path.charAt(0) != File.separatorChar,
        "path is absolute: \"%s\"",
        path);
    return rlocationUnchecked(path);
  }

  /** Returns true if the platform supports runfiles only via manifests. */
  private static boolean isManifestOnly(Map<String, String> env) {
    return "1".equals(env.get("RUNFILES_MANIFEST_ONLY"));
  }

  @Nullable
  private static String getManifestPath(Map<String, String> env) throws IOException {
    String value = env.get("RUNFILES_MANIFEST_FILE");
    if (Strings.isNullOrEmpty(value)) {
      throw new IOException(
          "Cannot load runfiles manifest: $RUNFILES_MANIFEST_ONLY is 1 but"
              + " $RUNFILES_MANIFEST_FILE is empty or undefined");
    }
    return value;
  }

  @Nullable
  private static String getRunfilesDir(Map<String, String> env) throws IOException {
    // On Linux and macOS, Bazel sets RUNFILES_DIR and TEST_SRCDIR.
    // Google-internal Blaze sets only TEST_SRCDIR.
    String value = env.get("RUNFILES_DIR");
    if (Strings.isNullOrEmpty(value)) {
      value = env.get("TEST_SRCDIR");
    }
    if (Strings.isNullOrEmpty(value)) {
      throw new IOException(
          "Cannot find runfiles: $RUNFILES_DIR and $TEST_SRCDIR are both unset or empty");
    }
    return value;
  }

  @Nullable
  abstract String rlocationUnchecked(String path);
}
