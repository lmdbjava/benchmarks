/*
 * Copyright 2016 The LmdbJava Project, http://lmdbjava.org/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lmdbjava.bench;

import static java.lang.Boolean.TRUE;
import static java.lang.System.setProperty;
import java.util.HashSet;
import java.util.Set;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import static org.lmdbjava.DbiFlags.MDB_CREATE;
import static org.lmdbjava.DbiFlags.MDB_INTEGERKEY;
import org.lmdbjava.Env;
import static org.lmdbjava.Env.DISABLE_CHECKS_PROP;
import org.lmdbjava.EnvFlags;
import static org.lmdbjava.EnvFlags.MDB_NOMETASYNC;
import static org.lmdbjava.EnvFlags.MDB_NOSYNC;
import static org.lmdbjava.EnvFlags.MDB_WRITEMAP;
import org.lmdbjava.Txn;
import org.openjdk.jmh.annotations.Param;
import static org.openjdk.jmh.annotations.Scope.Benchmark;
import org.openjdk.jmh.annotations.State;

/**
 * Additional {@link State} members used by LmdbJava benchmarks.
 */
@State(Benchmark)
public class CommonLmdbJava extends Common {

  static final int POSIX_MODE = 0664;

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

  static final EnvFlags[] envFlags(final boolean writeMap,
                                   final boolean metaSync, final boolean sync) {
    final Set<EnvFlags> envFlagSet = new HashSet<>();
    if (writeMap) {
      envFlagSet.add(MDB_WRITEMAP);
    }
    if (!sync) {
      envFlagSet.add(MDB_NOSYNC);
    }
    if (!metaSync) {
      envFlagSet.add(MDB_NOMETASYNC);
    }
    final EnvFlags[] envFlags = new EnvFlags[envFlagSet.size()];
    envFlagSet.toArray(envFlags);
    return envFlags;
  }

  static final long mapSize(final int num, final int valSize) {
    return num * valSize * 128L;
  }

  Dbi db;
  Env env;

  /**
   * Whether {@link EnvFlags#MDB_WRITEMAP} is used.
   */
  @Param({"true"})
  boolean writeMap;

  public void setup(final boolean metaSync, final boolean sync) throws Exception {
    super.setup();
    final EnvFlags[] envFlags = envFlags(writeMap, metaSync, sync);

    env = new Env();
    env.setMapSize(mapSize(num, valSize));
    env.setMaxDbs(1);
    env.setMaxReaders(1);
    env.open(tmp, POSIX_MODE, envFlags);

    try (final Txn tx = new Txn(env)) {
      final DbiFlags[] flags = dbiFlags(intKey);
      db = new Dbi(tx, "db", flags);
      tx.commit();
    }
  }

  @Override
  public void teardown() {
    env.close();
    super.teardown();
  }
}
