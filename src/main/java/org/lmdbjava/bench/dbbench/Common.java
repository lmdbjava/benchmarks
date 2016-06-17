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
package org.lmdbjava.bench.dbbench;

import java.io.File;
import static java.lang.Boolean.TRUE;
import static java.lang.Integer.MIN_VALUE;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.lang.System.setProperty;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.agrona.collections.IntHashSet;
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
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;
import org.openjdk.jmh.annotations.Param;
import static org.openjdk.jmh.annotations.Scope.Benchmark;
import org.openjdk.jmh.annotations.State;

@State(Benchmark)
public class Common {

  private static final int POSIX_MODE = 0664;
  private static final Random RND = new SecureRandom();
  static final byte[] RND_MB = new byte[1_048_576];
  static final int STRING_KEY_LENGTH = 16;

  static {
    setProperty(DISABLE_CHECKS_PROP, TRUE.toString());
    RND.nextBytes(RND_MB);
  }

  Dbi db;
  Env env;

  /**
   * Keys are always an integer, however they are actually stored as integers
   * (taking 4 bytes) or as zero-padded 16 byte strings. Storing keys as
   * integers offers a major performance gain.
   */
  @Param({"false", "true"})
  boolean intKey;

  /**
   * Keys in designated (random/sequential) order.
   */
  int[] keys;

  /**
   * Number of entries to read/write to the database.
   */
  @Param({"1000000"})
  int num;

  /**
   * Whether the keys are to be inserted into the database in sequential order
   * (and in the "readKeys" case, read back in that order). Sequential inserts
   * use {@link PutFlags#MDB_APPEND} and offer a major performance gain. If this
   * field is false, the append flag will not be used and the keys will instead
   * be inserted (and read back via "readKeys") in a random order.
   */
  @Param({"true", "false"})
  boolean sequential;

  File tmp;

  /**
   * Whether the values contain random bytes or are simply the same as the key.
   * If true, the random bytes are obtained sequentially from a 1 MB random byte
   * buffer.
   */
  @Param({"true"})
  boolean valRandom;

  /**
   * Number of bytes in each value.
   */
  @Param({"100"})
  int valSize;

  /**
   * Whether {@link EnvFlags#MDB_WRITEMAP} is used.
   */
  @Param({"true"})
  boolean writeMap;

  public void setup(final boolean metaSync, final boolean sync)
      throws Exception {
    final IntHashSet set = new IntHashSet(num, MIN_VALUE);
    keys = new int[num];
    for (int i = 0; i < num; i++) {
      if (sequential) {
        keys[i] = i;
      } else {
        while (true) {
          int candidateKey = RND.nextInt();
          if (candidateKey < 0) {
            candidateKey *= -1;
          }
          if (!set.contains(candidateKey)) {
            set.add(candidateKey);
            keys[i] = candidateKey;
            break;
          }
        }
      }
    }

    final String tmpParent = getProperty("java.io.tmpdir");
    tmp = new File(tmpParent, "jmh-bench-" + RND.nextInt());
    tmp.mkdirs();

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

    env = new Env();
    env.setMapSize(num * valSize * 128L);
    env.setMaxDbs(1);
    env.setMaxReaders(1);
    env.open(tmp, POSIX_MODE, envFlags);

    try (final Txn tx = new Txn(env)) {
      final DbiFlags[] flags;
      if (intKey) {
        flags = new DbiFlags[]{MDB_CREATE, MDB_INTEGERKEY};
      } else {
        flags = new DbiFlags[]{MDB_CREATE};
      }
      db = new Dbi(tx, "db", flags);
      tx.commit();
    }
  }

  public void teardown() {
    env.close();
    for (final File f : tmp.listFiles()) {
      f.delete();
    }
    tmp.delete();
  }

  final String padKey(int key) {
    return format("%0" + STRING_KEY_LENGTH + "d", key);
  }
}
