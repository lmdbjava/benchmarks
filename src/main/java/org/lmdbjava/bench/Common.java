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

import java.io.File;
import static java.lang.Integer.BYTES;
import static java.lang.Integer.MIN_VALUE;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.lang.System.out;
import java.security.SecureRandom;
import java.util.Random;
import java.util.zip.CRC32;
import jnr.posix.FileStat;
import jnr.posix.POSIX;
import static jnr.posix.POSIXFactory.getPOSIX;
import org.agrona.collections.IntHashSet;
import org.openjdk.jmh.annotations.Param;
import static org.openjdk.jmh.annotations.Scope.Benchmark;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.BenchmarkParams;

/**
 * Common JMH {@link State} superclass for all DB benchmark states.
 * <p>
 * Members do not reflect the typical code standards of the LmdbJava project due
 * to compliance requirements with JMH {@link Param} and {@link State}.
 */
@State(Benchmark)
public class Common {

  private static final POSIX POSIX = getPOSIX();

  private static final Random RND = new SecureRandom();
  static final byte[] RND_MB = new byte[1_048_576];
  static final int STRING_KEY_LENGTH = 16;
  static final int S_BLKSIZE = 512; // from sys/stat.h

  static {
    RND.nextBytes(RND_MB);
  }

  CRC32 crc;

  /**
   * Keys are always an integer, however they are actually stored as integers
   * (taking 4 bytes) or as zero-padded 16 byte strings. Storing keys as
   * integers offers a major performance gain.
   */
  @Param({"true"})
  boolean intKey;

  /**
   * Determined during {@link #setup()} based on {@link #intKey} value.
   */
  int keySize;
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
   * (and in the "readKeys" case, read back in that order). For LMDB, sequential
   * inserts use {@link org.lmdbjava.PutFlags#MDB_APPEND} and offer a major
   * performance gain. If this field is false, the append flag will not be used
   * and the keys will instead be inserted (and read back via "readKeys") in a
   * random order.
   */
  @Param({"true"})
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

  public void setup(BenchmarkParams b) throws Exception {
    keySize = intKey ? BYTES : STRING_KEY_LENGTH;
    crc = new CRC32();
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
    tmp = new File(tmpParent, b.id());
    if (tmp.exists()) {
      for (final File f : tmp.listFiles()) {
        f.delete();
      }
      tmp.delete();
    }
    tmp.mkdirs();
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public void teardown() throws Exception {
    if (tmp.getName().contains(".readKey-")) {
      // we only output for key, as all impls offer it and it should be fixed
      long bytes = 0;
      for (final File f : tmp.listFiles()) {
        if (f.isDirectory()) {
          throw new UnsupportedOperationException("impl created directory");
        }
        final FileStat stat = POSIX.stat(f.getAbsolutePath());
        bytes += (stat.blocks() * S_BLKSIZE);
      }
      out.println("\nBytes\t" + bytes + "\t" + tmp.getName());
    }
    for (final File f : tmp.listFiles()) {
      f.delete();
    }
    tmp.delete();
  }

  final String padKey(int key) {
    return format("%0" + STRING_KEY_LENGTH + "d", key);
  }
}
