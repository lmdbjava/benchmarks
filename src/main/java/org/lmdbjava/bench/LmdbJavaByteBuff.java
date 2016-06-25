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
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static net.openhft.hashing.LongHashFunction.xx_r39;
import static org.lmdbjava.ByteBufferProxy.PROXY_OPTIMAL;
import static org.lmdbjava.ByteBufferProxy.PROXY_SAFE;
import org.lmdbjava.Cursor;
import static org.lmdbjava.CursorOp.MDB_FIRST;
import static org.lmdbjava.CursorOp.MDB_LAST;
import static org.lmdbjava.CursorOp.MDB_NEXT;
import static org.lmdbjava.CursorOp.MDB_PREV;
import static org.lmdbjava.CursorOp.MDB_SET_KEY;
import static org.lmdbjava.Env.create;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.PutFlags;
import static org.lmdbjava.PutFlags.MDB_APPEND;
import org.lmdbjava.Txn;
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
public class LmdbJavaByteBuff {

  @Benchmark
  public void readCrc(final Reader r, final Blackhole bh) throws Exception {
    r.crc.reset();
    bh.consume(r.c.get(null, MDB_FIRST));
    do {
      r.crc.update(r.txn.key());
      r.crc.update(r.txn.val());
    } while (r.c.get(null, MDB_NEXT));
    bh.consume(r.crc.getValue());
  }

  @Benchmark
  public void readKey(final Reader r, final Blackhole bh) throws Exception {
    for (final int key : r.keys) {
      r.rwKey.clear();
      if (r.intKey) {
        r.rwKey.putInt(0, key).flip();
      } else {
        final byte[] str = r.padKey(key).getBytes();
        r.rwKey.put(str, 0, str.length).flip();
      }
      bh.consume(r.c.get(r.rwKey, MDB_SET_KEY));
      bh.consume(r.txn.val());
    }
  }

  @Benchmark
  public void readRev(final Reader r, final Blackhole bh) throws Exception {
    bh.consume(r.c.get(null, MDB_LAST));
    do {
      bh.consume(r.txn.val());
    } while (r.c.get(null, MDB_PREV));
  }

  @Benchmark
  public void readSeq(final Reader r, final Blackhole bh) throws Exception {
    bh.consume(r.c.get(null, MDB_FIRST));
    do {
      bh.consume(r.txn.val());
    } while (r.c.get(null, MDB_NEXT));
  }

  @Benchmark
  public void readXxh64(final Reader r, final Blackhole bh) throws Exception {
    long result = 0;
    bh.consume(r.c.get(null, MDB_FIRST));
    do {
      result += xx_r39().hashBytes(r.txn.key());
      result += xx_r39().hashBytes(r.txn.val());
    } while (r.c.get(null, MDB_NEXT));
    bh.consume(result);
  }

  @Benchmark
  public void write(final Writer w, final Blackhole bh) throws Exception {
    w.write();
  }

  @State(Benchmark)
  public static class LmdbJava extends CommonLmdbJava<ByteBuffer> {

    void write() throws Exception {
      try (final Txn<ByteBuffer> tx = env.txnWrite();) {
        try (final Cursor<ByteBuffer> c = db.openCursor(tx);) {
          final PutFlags flags = sequential ? MDB_APPEND : null;
          final int rndByteMax = RND_MB.length - valSize;
          int rndByteOffset = 0;
          for (final int key : keys) {
            rwKey.clear();
            rwVal.clear();
            if (intKey) {
              rwKey.putInt(0, key).flip();
            } else {
              final byte[] str = padKey(key).getBytes();
              rwKey.put(str, 0, str.length).flip();
            }
            if (valRandom) {
              rwVal.put(RND_MB, rndByteOffset, valSize).flip();
              rndByteOffset += valSize;
              if (rndByteOffset >= rndByteMax) {
                rndByteOffset = 0;
              }
            } else {
              rwVal.putInt(0, key).flip();
            }
            c.put(rwKey, rwVal, flags);
          }
        }
        tx.commit();
      }
    }

  }

  @State(Benchmark)
  public static class Reader extends LmdbJava {

    /**
     * Whether the byte buffer accessor is safe or not
     */
    @Param({"false", "true"})
    boolean forceSafe;

    @Setup(Trial)
    @Override
    public void setup() throws Exception {
      env = create(forceSafe ? PROXY_SAFE : PROXY_OPTIMAL);
      super.setup(false, false);
      txn.key().order(LITTLE_ENDIAN);
      rwKey.order(LITTLE_ENDIAN);
      super.write();
      txn.reset(); // freshen TX + cursor to see new data
      txn.renew();
      c.renew(txn);
    }

    @TearDown(Trial)
    @Override
    public void teardown() throws Exception {
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
      env = create(PROXY_OPTIMAL);
      super.setup(metaSync, sync);
      txn.key().order(LITTLE_ENDIAN);
      rwKey.order(LITTLE_ENDIAN);
    }

    @TearDown(Invocation)
    @Override
    public void teardown() throws Exception {
      super.teardown();
    }
  }

}
