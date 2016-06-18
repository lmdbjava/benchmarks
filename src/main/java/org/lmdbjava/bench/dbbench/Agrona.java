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

import static java.lang.Integer.BYTES;
import static java.nio.ByteBuffer.allocateDirect;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.lmdbjava.CursorB;
import static org.lmdbjava.CursorOp.MDB_FIRST;
import static org.lmdbjava.CursorOp.MDB_LAST;
import static org.lmdbjava.CursorOp.MDB_NEXT;
import static org.lmdbjava.CursorOp.MDB_PREV;
import static org.lmdbjava.CursorOp.MDB_SET_KEY;
import org.lmdbjava.EnvFlags;
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
public class Agrona {

  @Benchmark
  public void readKey(final ReaderAgrona r, final Blackhole bh) throws
      Exception {
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
  public void readRev(final ReaderAgrona r, final Blackhole bh) throws
      Exception {
    try (final Txn tx = new Txn(r.env, MDB_RDONLY);
         final CursorB c = r.db.openCursorB(tx)) {
      bh.consume(c.get(r.rkv, r.rkv, MDB_LAST));
      while (c.get(r.rkv, r.rkv, MDB_PREV)) {
        bh.consume(r.rkv.size()); // force native memory lookup
      }
    }
  }

  @Benchmark
  public void readSeq(final ReaderAgrona r, final Blackhole bh) throws
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
  public void write(final WriterAgrona w, final Blackhole bh) throws Exception {
    w.write();
  }

  @State(Benchmark)
  public static class LmdbJavaAgrona extends Common {

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
     * Writable key buffer. This is the buffer used by {@link #wkv}.
     */
    MutableDirectBuffer wkb;

    /**
     * Writable key value. This is the value that wraps {@link #wkb}.
     */
    MutableDirectBufferVal wkv;

    /**
     * Writable value buffer. This is the buffer used by {@link #wvv}.
     */
    MutableDirectBuffer wvb;

    /**
     * Writable value value. This is the value that wraps {@link #wvb}.
     */
    MutableDirectBufferVal wvv;

    @Override
    public void setup(final boolean metaSync, final boolean sync) throws
        Exception {
      super.setup(metaSync, sync);
      rkb = new UnsafeBuffer(allocateDirect(0));
      rvb = new UnsafeBuffer(allocateDirect(0));
      final int keySize = intKey ? BYTES : STRING_KEY_LENGTH;
      wkb = new UnsafeBuffer(allocateDirect(keySize));
      wvb = new UnsafeBuffer(allocateDirect(valSize));
      rkv = forMdb(rkb, false);
      rvv = forMdb(rvb, false);
      wkv = forMdb(wkb, false);
      wvv = forMdb(wvb, false);
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
  public static class ReaderAgrona extends LmdbJavaAgrona {

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
  public static class WriterAgrona extends LmdbJavaAgrona {

    /**
     * Whether {@link EnvFlags#MDB_NOMETASYNC} is used.
     */
    @Param({"false"})
    boolean metaSync;

    /**
     * Whether {@link EnvFlags#MDB_NOSYNC} is used.
     */
    @Param({"false"})
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
