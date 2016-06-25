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
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static net.openhft.hashing.LongHashFunction.xx_r39;
import org.agrona.MutableDirectBuffer;
import static org.agrona.concurrent.UnsafeBuffer.DISABLE_BOUNDS_CHECKS_PROP_NAME;
import org.lmdbjava.Cursor;
import static org.lmdbjava.CursorOp.MDB_FIRST;
import static org.lmdbjava.CursorOp.MDB_LAST;
import static org.lmdbjava.CursorOp.MDB_NEXT;
import static org.lmdbjava.CursorOp.MDB_PREV;
import static org.lmdbjava.CursorOp.MDB_SET_KEY;
import static org.lmdbjava.Env.create;
import org.lmdbjava.EnvFlags;
import static org.lmdbjava.MutableDirectBufferProxy.PROXY_MDB;
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
public class LmdbJavaAgrona {

  @Benchmark
  public void readCrc(final Reader r, final Blackhole bh) throws Exception {
    r.crc.reset();
    bh.consume(r.c.get(null, MDB_FIRST));
    do {
      r.txn.key().getBytes(0, r.keyBytes, 0, r.keySize);
      r.txn.val().getBytes(0, r.valBytes, 0, r.valSize);
      r.crc.update(r.keyBytes);
      r.crc.update(r.valBytes);
    } while (r.c.get(null, MDB_NEXT));
    bh.consume(r.crc.getValue());
  }

  @Benchmark
  public void readKey(final Reader r, final Blackhole bh) throws Exception {
    for (final int key : r.keys) {
      if (r.intKey) {
        r.rwKey.putInt(0, key);
      } else {
        r.rwKey.putStringWithoutLengthUtf8(0, r.padKey(key));
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
      result += xx_r39().hashMemory(r.txn.key().addressOffset(), r.keySize);
      result += xx_r39().hashMemory(r.txn.val().addressOffset(), r.valSize);
    } while (r.c.get(null, MDB_NEXT));
    bh.consume(result);
  }

  @Benchmark
  public void write(final Writer w, final Blackhole bh) throws Exception {
    w.write();
  }

  @State(Benchmark)
  public static class LmdbJava extends CommonLmdbJava<MutableDirectBuffer> {

    static {
      setProperty(DISABLE_BOUNDS_CHECKS_PROP_NAME, TRUE.toString());
    }

    /**
     * CRC scratch (memory-mapped MDB can't return a byte[] or ByteBuffer).
     */
    byte[] keyBytes;

    /**
     * CRC scratch (memory-mapped MDB can't return a byte[] or ByteBuffer).
     */
    byte[] valBytes;

    @Override
    public void setup(final boolean metaSync, final boolean sync) throws
        Exception {
      super.setup(metaSync, sync);
      keyBytes = new byte[keySize];
      valBytes = new byte[valSize];
    }

    void write() throws Exception {
      try (final Txn<MutableDirectBuffer> tx = env.txnWrite()) {
        try (final Cursor<MutableDirectBuffer> c = db.openCursor(tx);) {
          final PutFlags flags = sequential ? MDB_APPEND : null;
          final int rndByteMax = RND_MB.length - valSize;
          int rndByteOffset = 0;
          for (final int key : keys) {
            if (intKey) {
              rwKey.putInt(0, key);
            } else {
              rwKey.putStringWithoutLengthUtf8(0, padKey(key));
            }
            if (valRandom) {
              rwVal.putBytes(0, RND_MB, rndByteOffset, valSize);
              rndByteOffset += valSize;
              if (rndByteOffset >= rndByteMax) {
                rndByteOffset = 0;
              }
            } else {
              rwVal.putInt(0, key);
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

    @Setup(Trial)
    @Override
    public void setup() throws Exception {
      env = create(PROXY_MDB);
      super.setup(false, false);
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
      env = create(PROXY_MDB);
      super.setup(metaSync, sync);
    }

    @TearDown(Invocation)
    @Override
    public void teardown() throws Exception {
      super.teardown();
    }
  }

}
