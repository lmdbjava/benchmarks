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

import java.nio.ByteBuffer;
import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import org.lmdbjava.Cursor;
import static org.lmdbjava.CursorOp.MDB_FIRST;
import static org.lmdbjava.CursorOp.MDB_LAST;
import static org.lmdbjava.CursorOp.MDB_NEXT;
import static org.lmdbjava.CursorOp.MDB_PREV;
import static org.lmdbjava.CursorOp.MDB_SET_KEY;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.PutFlags;
import static org.lmdbjava.PutFlags.MDB_APPEND;
import org.lmdbjava.Txn;
import static org.lmdbjava.TxnFlags.MDB_RDONLY;
import org.lmdbjava.Val;
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
public class ByteBuffCursor {

  @Benchmark
  public void readCrc(final Reader r, final Blackhole bh) throws Exception {
    r.crc.reset();
    try (final Txn tx = new Txn(r.env, MDB_RDONLY);
         final Cursor c = r.db.openCursor(tx)) {
      bh.consume(c.get(r.rkv, r.rvv, MDB_FIRST));
      do {
        r.crc.update(r.rkv.getByteBuffer());
        r.crc.update(r.rvv.getByteBuffer());
      } while (c.get(r.rkv, r.rvv, MDB_NEXT));
    }
    bh.consume(r.crc.getValue());
  }

  @Benchmark
  public void readKey(final Reader r, final Blackhole bh) throws Exception {
    try (final Txn tx = new Txn(r.env, MDB_RDONLY);
         final Cursor c = r.db.openCursor(tx)) {
      for (final int key : r.keys) {
        r.wkb.clear();
        if (r.intKey) {
          r.wkb.putInt(0, key).flip();
        } else {
          final byte[] str = r.padKey(key).getBytes();
          r.wkb.put(str, 0, str.length).flip();
        }
        bh.consume(c.get(r.wkv, r.rkv, MDB_SET_KEY));
        bh.consume(r.rkv.getSize()); // force native memory lookup
        // no need to re-wrap, as we never change buffer (auto-reset:off)
      }
    }
  }

  @Benchmark
  public void readRev(final Reader r, final Blackhole bh) throws Exception {
    try (final Txn tx = new Txn(r.env, MDB_RDONLY);
         final Cursor c = r.db.openCursor(tx)) {
      bh.consume(c.get(r.rkv, r.rvv, MDB_LAST));
      do {
        bh.consume(r.rkv.getSize()); // force native memory lookup
      } while (c.get(r.rkv, r.rvv, MDB_PREV));
    }
  }

  @Benchmark
  public void readSeq(final Reader r, final Blackhole bh) throws Exception {
    try (final Txn tx = new Txn(r.env, MDB_RDONLY);
         final Cursor c = r.db.openCursor(tx)) {
      bh.consume(c.get(r.rkv, r.rvv, MDB_FIRST));
      do {
        bh.consume(r.rkv.getSize()); // force native memory lookup
      } while (c.get(r.rkv, r.rvv, MDB_NEXT));
    }
  }

  @Benchmark
  public void write(final Writer w, final Blackhole bh) throws Exception {
    w.write();
  }

  @State(Benchmark)
  public static class LmdbJava extends CommonLmdbJava {

    /**
     * Read-only key buffer. This is the buffer used by {@link #rkv}.
     */
    ByteBuffer rkb;

    /**
     * Read-only key value. This is the value that wraps {@link #rkb}.
     */
    Val rkv;

    /**
     * Read-only value buffer. This is the buffer used by {@link #rvv}.
     */
    ByteBuffer rvb;

    /**
     * Read-only value value. This is the value that wraps {@link #rvb}.
     */
    Val rvv;

    /**
     * Writable key buffer. This is the buffer used by {@link #wkv}.
     */
    ByteBuffer wkb;

    /**
     * Writable key value. This is the value that wraps {@link #wkb}.
     */
    Val wkv;

    /**
     * Writable value buffer. This is the buffer used by {@link #wvv}.
     */
    ByteBuffer wvb;

    /**
     * Writable value value. This is the value that wraps {@link #wvb}.
     */
    Val wvv;

    @Override
    public void setup(final boolean metaSync, final boolean sync) throws
        Exception {
      super.setup(metaSync, sync);
      rkb = allocateDirect(0).order(LITTLE_ENDIAN);
      rvb = allocateDirect(0).order(LITTLE_ENDIAN);
      wkb = allocateDirect(keySize).order(LITTLE_ENDIAN);
      wvb = allocateDirect(valSize).order(LITTLE_ENDIAN);
      rkv = new Val(rkb);
      rvv = new Val(rvb);
      wkv = new Val(wkb);
      wvv = new Val(wvb);
    }

    void write() throws Exception {
      try (final Txn tx = new Txn(env);) {
        try (final Cursor c = db.openCursor(tx);) {
          final PutFlags flags = sequential ? MDB_APPEND : null;
          final int rndByteMax = RND_MB.length - valSize;
          int rndByteOffset = 0;
          for (final int key : keys) {
            wkb.clear();
            wvb.clear();
            if (intKey) {
              wkb.putInt(0, key).flip();
            } else {
              final byte[] str = padKey(key).getBytes();
              wkb.put(str, 0, str.length).flip();
            }
            if (valRandom) {
              wvb.put(RND_MB, rndByteOffset, valSize).flip();
            } else {
              wvb.putInt(0, key).flip();
            }
            rndByteOffset += valSize;
            if (rndByteOffset >= rndByteMax) {
              rndByteOffset = 0;
            }
            c.put(wkb, wvb, flags);
          }
        }
        tx.commit();
      }
    }

  }

  @State(Benchmark)
  public static class Reader extends LmdbJava {

    @Setup(Trial)
    @Override
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
  public static class Writer extends LmdbJava {

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
    @Override
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
