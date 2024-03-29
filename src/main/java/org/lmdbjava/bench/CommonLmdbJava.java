/*-
 * #%L
 * LmdbJava Benchmarks
 * %%
 * Copyright (C) 2016 - 2022 The LmdbJava Open Source Project
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package org.lmdbjava.bench;

import static java.lang.Boolean.TRUE;
import static java.lang.System.setProperty;
import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.DbiFlags.MDB_INTEGERKEY;
import static org.lmdbjava.Env.DISABLE_CHECKS_PROP;
import static org.lmdbjava.Env.create;
import static org.lmdbjava.EnvFlags.MDB_NOSYNC;
import static org.lmdbjava.EnvFlags.MDB_WRITEMAP;
import static org.openjdk.jmh.annotations.Scope.Benchmark;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.lmdbjava.BufferProxy;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.BenchmarkParams;

/**
 * Additional {@link State} members used by LmdbJava benchmarks.
 *
 * @param <T> buffer type
 */
@State(Benchmark)
@SuppressWarnings({"checkstyle:javadoctype", "checkstyle:designforextension",
                   "checkstyle:visibilitymodifier"})
public class CommonLmdbJava<T> extends Common {

  static final int POSIX_MODE = 664;

  BufferProxy<T> bufferProxy;
  Dbi<T> db;
  Env<T> env;

  /**
   * Whether {@link EnvFlags#MDB_WRITEMAP} is used.
   */
  @Param("true")
  boolean writeMap;

  static {
    setProperty(DISABLE_CHECKS_PROP, TRUE.toString());
  }

  static final DbiFlags[] dbiFlags(final boolean intKey) {
    final DbiFlags[] flags;
    if (intKey) {
      flags = new DbiFlags[]{MDB_CREATE, MDB_INTEGERKEY};
    } else {
      flags = new DbiFlags[]{MDB_CREATE};
    }
    return flags;
  }

  static final EnvFlags[] envFlags(final boolean writeMap, final boolean sync) {
    final Set<EnvFlags> envFlagSet = new HashSet<>();
    if (writeMap) {
      envFlagSet.add(MDB_WRITEMAP);
    }
    if (!sync) {
      envFlagSet.add(MDB_NOSYNC);
    }
    final EnvFlags[] envFlags = new EnvFlags[envFlagSet.size()];
    envFlagSet.toArray(envFlags);
    return envFlags;
  }

  static final long mapSize(final int num, final int valSize) {
    return num * ((long) valSize) * 32L / 10L;
  }

  public void setup(final BenchmarkParams b, final boolean sync) throws
      IOException {
    super.setup(b);
    final EnvFlags[] envFlags = envFlags(writeMap, sync);
    env = create(bufferProxy)
        .setMapSize(mapSize(num, valSize))
        .setMaxDbs(1)
        .setMaxReaders(2)
        .open(tmp, POSIX_MODE, envFlags);

    final DbiFlags[] flags = dbiFlags(intKey);
    db = env.openDbi("db", flags);
  }

  @Override
  public void teardown() throws IOException {
    reportSpaceBeforeClose();
    env.close();
    super.teardown();
  }
}
