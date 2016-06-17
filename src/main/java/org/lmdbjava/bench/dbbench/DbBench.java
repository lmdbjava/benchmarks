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
import static java.lang.Integer.BYTES;
import static java.lang.Integer.MIN_VALUE;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.lang.System.setProperty;
import static java.nio.ByteBuffer.allocateDirect;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.IntHashSet;
import org.agrona.concurrent.UnsafeBuffer;
import org.lmdbjava.CursorB;
import static org.lmdbjava.CursorOp.MDB_FIRST;
import static org.lmdbjava.CursorOp.MDB_LAST;
import static org.lmdbjava.CursorOp.MDB_NEXT;
import static org.lmdbjava.CursorOp.MDB_PREV;
import static org.lmdbjava.CursorOp.MDB_SET_KEY;
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
import org.lmdbjava.MutableDirectBufferVal;
import static org.lmdbjava.MutableDirectBufferVal.forMdb;
import org.lmdbjava.PutFlags;
import static org.lmdbjava.PutFlags.MDB_APPEND;
import org.lmdbjava.Txn;
import static org.lmdbjava.TxnFlags.MDB_RDONLY;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import static org.openjdk.jmh.annotations.Level.Invocation;
import static org.openjdk.jmh.annotations.Level.Trial;
import org.openjdk.jmh.annotations.Measurement;
import static org.openjdk.jmh.annotations.Mode.SampleTime;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import static org.openjdk.jmh.annotations.Scope.Benchmark;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@OutputTimeUnit(MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@BenchmarkMode(SampleTime)
public class DbBench {

  private static final int POSIX_MODE = 0664;
  private static final Random RND = new SecureRandom();
  private static final byte[] RND_MB = new byte[1_048_576];
  private static final int STRING_KEY_LENGTH = 16;

  static {
    setProperty(DISABLE_CHECKS_PROP, TRUE.toString());
    RND.nextBytes(RND_MB);
  }

  @Benchmark
  public void readKeys(final Reader r, final Blackhole bh) throws Exception {
    try (final Txn tx = new Txn(r.env, MDB_RDONLY);
         final CursorB c = r.db.openCursorB(tx)) {
      for (final int key : r.keys) {
        if (r.intKey) {
          r.wkb.putInt(0, key);
        } else {
          r.wkb.putStringWithoutLengthUtf8(0, r.padKey(key));
        }
        bh.consume(c.get(r.wkv, r.rkv, MDB_SET_KEY));
        bh.consume(r.rkv.size()); // force native memory lookup
        // no need to re-wrap, as we never change buffer (auto-reset:off)
      }
    }
  }

  @Benchmark
  public void readReverse(final Reader r, final Blackhole bh) throws Exception {
    try (final Txn tx = new Txn(r.env, MDB_RDONLY);
         final CursorB c = r.db.openCursorB(tx)) {
      bh.consume(c.get(r.rkv, r.rkv, MDB_LAST));
      while (c.get(r.rkv, r.rkv, MDB_PREV)) {
        bh.consume(r.rkv.size()); // force native memory lookup
      }
    }
  }

  @Benchmark
  public void readSequential(final Reader r, final Blackhole bh) throws
      Exception {
    try (final Txn tx = new Txn(r.env, MDB_RDONLY);
         final CursorB c = r.db.openCursorB(tx)) {
      bh.consume(c.get(r.rkv, r.rkv, MDB_FIRST));
      while (c.get(r.rkv, r.rkv, MDB_NEXT)) {
        bh.consume(r.rkv.size()); // force native memory lookup
      }
    }
  }

  @Benchmark
  public void write(final Writer w, final Blackhole bh) throws Exception {
    w.write();
  }

  @State(Benchmark)
  public static class Common {

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
     * Read-only key buffer. This is the buffer used by {@link #rkv}.
     */
    MutableDirectBuffer rkb;

    /**
     * Read-only key value. This is the value that wraps {@link #rkb}.
     */
    MutableDirectBufferVal rkv;

    /**
     * Read-only value buffer. This is the buffer used by {@link #rvv}.
     */
    MutableDirectBuffer rvb;

    /**
     * Read-only value value. This is the value that wraps {@link #rvb}.
     */
    MutableDirectBufferVal rvv;

    /**
     * Whether the keys are to be inserted into the database in sequential order
     * (and in the "readKeys" case, read back in that order). Sequential inserts
     * use {@link PutFlags#MDB_APPEND} and offer a major performance gain. If
     * this field is false, the append flag will not be used and the keys will
     * instead be inserted (and read back via "readKeys") in a random order.
     */
    @Param({"true", "false"})
    boolean sequential;

    File tmp;

    /**
     * Whether the values contain random bytes or are simply the same as the
     * key. If true, the random bytes are obtained sequentially from a 1 MB
     * random byte buffer.
     */
    @Param({"true"})
    boolean valRandom;

    /**
     * Number of bytes in each value.
     */
    @Param({"100"})
    int valSize;

    /**
     * Writable key buffer. This is the buffer used by {@link #wkv}.
     */
    MutableDirectBuffer wkb;

    /**
     * Writable key value. This is the value that wraps {@link #wkb}.
     */
    MutableDirectBufferVal wkv;

    /**
     * Whether {@link EnvFlags#MDB_WRITEMAP} is used.
     */
    @Param({"true"})
    boolean writeMap;

    /**
     * Writable value buffer. This is the buffer used by {@link #wvv}.
     */
    MutableDirectBuffer wvb;

    /**
     * Writable value value. This is the value that wraps {@link #wvb}.
     */
    MutableDirectBufferVal wvv;

    public void setup(final boolean metaSync, final boolean sync)
        throws Exception {
      rkb = new UnsafeBuffer(allocateDirect(0));
      rvb = new UnsafeBuffer(allocateDirect(0));
      final int keySize = intKey ? BYTES : STRING_KEY_LENGTH;
      wkb = new UnsafeBuffer(allocateDirect(keySize));
      wvb = new UnsafeBuffer(allocateDirect(valSize));
      rkv = forMdb(rkb, false);
      rvv = forMdb(rvb, false);
      wkv = forMdb(wkb, false);
      wvv = forMdb(wvb, false);

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

    String padKey(int key) {
      return format("%0" + STRING_KEY_LENGTH + "d", key);
    }

    void write() throws Exception {
      try (final Txn tx = new Txn(env);) {
        try (final CursorB c = db.openCursorB(tx);) {
          final PutFlags flags = sequential ? MDB_APPEND : null;
          final int rndByteMax = RND_MB.length - valSize;
          int rndByteOffset = 0;
          for (final int key : keys) {
            if (intKey) {
              wkb.putInt(0, key);
            } else {
              wkb.putStringWithoutLengthUtf8(0, padKey(key));
            }
            if (valRandom) {
              wvb.putBytes(0, RND_MB, rndByteOffset, valSize);
            } else {
              wvb.putInt(0, key);
            }
            rndByteOffset += valSize;
            if (rndByteOffset >= rndByteMax) {
              rndByteOffset = 0;
            }
            c.put(wkv, wvv, flags);
          }
        }
        tx.commit();
      }
    }
  }

  @State(Benchmark)
  public static class Reader extends Common {

    @Setup(Trial)
    public void setup() throws Exception {
      super.setup(false, false);
      super.write();
    }

    @TearDown(Trial)
    @Override
    public void teardown() {
      super.teardown();
    }
  }

  @State(Benchmark)
  public static class Writer extends Common {

    /**
     * Whether {@link EnvFlags#MDB_NOMETASYNC} is used.
     */
    @Param({"false"})
    boolean metaSync;

    /**
     * Whether {@link EnvFlags#MDB_NOSYNC} is used.
     */
    @Param({"false", "true"})
    boolean sync;

    @Setup(Invocation)
    public void setup() throws Exception {
      super.setup(metaSync, sync);
    }

    @TearDown(Invocation)
    @Override
    public void teardown() {
      super.teardown();
    }
  }

}
