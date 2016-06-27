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
import static java.nio.ByteBuffer.allocateDirect;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static net.openhft.hashing.LongHashFunction.xx_r39;
import org.fusesource.lmdbjni.BufferCursor;
import org.fusesource.lmdbjni.Database;
import org.fusesource.lmdbjni.DirectBuffer;
import static org.fusesource.lmdbjni.DirectBuffer.DISABLE_BOUNDS_CHECKS_PROP_NAME;
import org.fusesource.lmdbjni.Env;
import org.fusesource.lmdbjni.Transaction;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.EnvFlags;
import static org.lmdbjava.MaskedFlag.mask;
import static org.lmdbjava.bench.CommonLmdbJava.POSIX_MODE;
import static org.lmdbjava.bench.CommonLmdbJava.dbiFlags;
import static org.lmdbjava.bench.CommonLmdbJava.envFlags;
import static org.lmdbjava.bench.CommonLmdbJava.mapSize;
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
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;

@OutputTimeUnit(MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@BenchmarkMode(SampleTime)
public class LmdbJni {

  @Benchmark
  public void readCrc(final Reader r, final Blackhole bh) throws Exception {
    r.crc.reset();
    bh.consume(r.c.first());
    do {
      r.c.keyBuffer().getBytes(0, r.keyBytes, 0, r.keySize);
      r.c.valBuffer().getBytes(0, r.valBytes, 0, r.valSize);
      r.crc.update(r.keyBytes);
      r.crc.update(r.valBytes);
    } while (r.c.next());
    bh.consume(r.crc.getValue());
  }

  @Benchmark
  public void readKey(final Reader r, final Blackhole bh) throws Exception {
    for (final int key : r.keys) {
      if (r.intKey) {
        r.wkb.putInt(0, key);
      } else {
        r.wkb.putStringWithoutLengthUtf8(0, r.padKey(key));
      }
      r.c.keyWrite(r.wkb);
      bh.consume(r.c.seekKey());
      bh.consume(r.c.valBuffer());
    }
  }

  @Benchmark
  public void readRev(final Reader r, final Blackhole bh) throws Exception {
    bh.consume(r.c.last());
    do {
      bh.consume(r.c.valBuffer());
    } while (r.c.prev());
  }

  @Benchmark
  public void readSeq(final Reader r, final Blackhole bh) throws Exception {
    bh.consume(r.c.first());
    do {
      bh.consume(r.c.valBuffer());
    } while (r.c.next());
  }

  @Benchmark
  public void readXxh64(final Reader r, final Blackhole bh) throws Exception {
    long result = 0;
    bh.consume(r.c.first());
    do {
      result += xx_r39().hashMemory(r.c.keyBuffer().addressOffset(), r.keySize);
      result += xx_r39().hashMemory(r.c.valBuffer().addressOffset(), r.valSize);
    } while (r.c.next());
    bh.consume(result);
  }

  @Benchmark
  public void write(final Writer w, final Blackhole bh) throws Exception {
    w.write();
  }

  @State(value = Benchmark)
  public static class CommonLmdbJni extends Common {

    static {
      setProperty(DISABLE_BOUNDS_CHECKS_PROP_NAME, TRUE.toString());
    }

    Database db;
    Env env;

    /**
     * CRC scratch (memory-mapped MDB can't return a byte[] or ByteBuffer).
     */
    byte[] keyBytes;

    /**
     * CRC scratch (memory-mapped MDB can't return a byte[] or ByteBuffer).
     */
    byte[] valBytes;

    /**
     * CRC scratch (memory-mapped MDB can't return a byte[] or ByteBuffer).
     */
    DirectBuffer wkb;

    /**
     * Whether {@link EnvFlags#MDB_WRITEMAP} is used.
     */
    @Param({"true"})
    boolean writeMap;

    /**
     * Writable value buffer.
     */
    DirectBuffer wvb;

    public void setup(BenchmarkParams b, final boolean metaSync,
                      final boolean sync) throws
        Exception {
      super.setup(b);
      wkb = new DirectBuffer(allocateDirect(keySize));
      wvb = new DirectBuffer(allocateDirect(valSize));
      keyBytes = new byte[keySize];
      valBytes = new byte[valSize];

      final EnvFlags[] envFlags = envFlags(writeMap, metaSync, sync);

      env = new Env();
      env.setMapSize(mapSize(num, valSize));
      env.setMaxDbs(1);
      env.setMaxReaders(2);
      env.open(tmp.getAbsolutePath(), mask(envFlags), POSIX_MODE);

      try (final Transaction tx = env.createWriteTransaction()) {
        final DbiFlags[] flags = dbiFlags(intKey);
        db = env.openDatabase(tx, "db", mask(flags));
        tx.commit();
      }
    }

    @Override
    public void teardown() throws Exception {
      env.close();
      super.teardown();
    }

    void write() throws Exception {
      try (final Transaction tx = env.createWriteTransaction()) {
        try (final BufferCursor c = db.bufferCursor(tx);) {
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
              rndByteOffset += valSize;
              if (rndByteOffset >= rndByteMax) {
                rndByteOffset = 0;
              }
            } else {
              wvb.putInt(0, key);
            }
            c.keyWrite(wkb);
            c.valWrite(wvb);
            if (sequential) {
              c.append();
            } else {
              c.overwrite();
            }
          }
        }
        tx.commit();
      }
    }
  }

  @State(Benchmark)
  public static class Reader extends CommonLmdbJni {

    BufferCursor c;
    Transaction tx;

    @Setup(Trial)
    @Override
    public void setup(BenchmarkParams b) throws Exception {
      super.setup(b, false, false);
      super.write();
      tx = env.createReadTransaction();
      c = db.bufferCursor(tx);
    }

    @TearDown(Trial)
    @Override
    public void teardown() throws Exception {
      c.close();
      tx.abort();
      super.teardown();
    }
  }

  @State(Benchmark)
  public static class Writer extends CommonLmdbJni {

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
    public void setup(BenchmarkParams b) throws Exception {
      super.setup(b, metaSync, sync);
    }

    @TearDown(Invocation)
    @Override
    public void teardown() throws Exception {
      super.teardown();
    }
  }

}
