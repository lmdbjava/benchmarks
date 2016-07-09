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

import static java.util.Arrays.copyOfRange;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import jetbrains.exodus.ArrayByteIterable;
import jetbrains.exodus.ByteIterable;
import static jetbrains.exodus.bindings.IntegerBinding.intToEntry;
import static jetbrains.exodus.bindings.StringBinding.stringToEntry;
import jetbrains.exodus.env.Cursor;
import jetbrains.exodus.env.Environment;
import jetbrains.exodus.env.EnvironmentConfig;
import static jetbrains.exodus.env.Environments.newInstance;
import jetbrains.exodus.env.Store;
import static jetbrains.exodus.env.StoreConfig.WITHOUT_DUPLICATES;
import jetbrains.exodus.env.Transaction;
import static net.openhft.hashing.LongHashFunction.xx_r39;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import static org.openjdk.jmh.annotations.Level.Invocation;
import static org.openjdk.jmh.annotations.Level.Trial;
import org.openjdk.jmh.annotations.Measurement;
import static org.openjdk.jmh.annotations.Mode.SampleTime;
import org.openjdk.jmh.annotations.OutputTimeUnit;
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
public class Xodus {

  @Benchmark public void readCrc(final Reader r, final Blackhole bh) throws
      Exception {
    r.crc.reset();
    try (final Cursor c = r.store.openCursor(r.tx)) {
      while (c.getNext()) {
        r.crc.update(c.getKey().getBytesUnsafe(), 0, r.keySize);
        r.crc.update(c.getValue().getBytesUnsafe(), 0, r.valSize);
      }
    }
    bh.consume(r.crc.getValue());
  }

  @Benchmark public void readKey(final Reader r, final Blackhole bh) throws
      Exception {
    for (final int key : r.keys) {
      if (r.intKey) {
        bh.consume(r.store.get(r.tx, intToEntry(key)).getBytesUnsafe());
      } else {
        bh.consume(r.store.get(r.tx, stringToEntry(r.padKey(key))).getBytesUnsafe());
      }
    }
  }

  @Benchmark
  public void readRev(final Reader r, final Blackhole bh) throws Exception {
    try (final Cursor c = r.store.openCursor(r.tx)) {
      c.getLast();
      do {
        bh.consume(c.getValue().getBytesUnsafe());
      } while (c.getPrev());
    }
  }

  @Benchmark
  public void readSeq(final Reader r, final Blackhole bh) throws Exception {
    try (final Cursor c = r.store.openCursor(r.tx)) {
      while (c.getNext()) {
        bh.consume(c.getValue().getBytesUnsafe());
      }
    }
  }

  @Benchmark public void readXxh64(final Reader r, final Blackhole bh) throws
      Exception {
    long result = 0;
    try (final Cursor c = r.store.openCursor(r.tx)) {
      while (c.getNext()) {
        result += xx_r39().hashBytes(c.getKey().getBytesUnsafe(), 0, r.keySize);
        result += xx_r39().
            hashBytes(c.getValue().getBytesUnsafe(), 0, r.valSize);
      }
    }
    bh.consume(result);
  }

  @Benchmark public void write(final Writer w, final Blackhole bh) throws
      Exception {
    w.write();
  }

  @State(value = Benchmark)
  public static class CommonXodus extends Common {

    Environment env;
    Store store;

    @Override
    public void setup(BenchmarkParams b) throws Exception {
      super.setup(b);

      final EnvironmentConfig cfg = new EnvironmentConfig();
      cfg.setLogDurableWrite(false); // non-durable writes is xodus default
      env = newInstance(tmp, cfg);

      env.executeInTransaction((final Transaction txn) -> {
        store = env.openStore("without_dups", WITHOUT_DUPLICATES, txn);
        txn.commit();
      });
    }

    @Override
    public void teardown() throws Exception {
      reportSpaceBeforeClose();
      env.close();
      super.teardown();
    }

    void write() throws Exception {
      env.executeInTransaction((final Transaction tx) -> {
        final int rndByteMax = RND_MB.length - valSize;
        int rndByteOffset = 0;
        for (final int key : keys) {
          ByteIterable keyBi;
          ByteIterable valBi;
          if (intKey) {
            keyBi = intToEntry(key);
          } else {
            keyBi = stringToEntry(padKey(key));
          }
          if (valRandom) {
            byte[] bytes = copyOfRange(RND_MB, rndByteOffset, valSize);
            valBi = new ArrayByteIterable(bytes);
            rndByteOffset += valSize;
            if (rndByteOffset >= rndByteMax) {
              rndByteOffset = 0;
            }
          } else {
            byte[] bytes = new byte[valSize];
            bytes[0] = (byte) (key >>> 24);
            bytes[1] = (byte) (key >>> 16);
            bytes[2] = (byte) (key >>> 8);
            bytes[3] = (byte) (key);
            valBi = new ArrayByteIterable(bytes, valSize);
          }
          if (sequential) {
            store.putRight(tx, keyBi, valBi);
          } else {
            store.put(tx, keyBi, valBi);
          }
        }
        tx.commit();
      });
    }
  }

  @State(Benchmark)
  public static class Reader extends CommonXodus {

    Transaction tx;

    @Setup(Trial)
    @Override
    public void setup(BenchmarkParams b) throws Exception {
      super.setup(b);
      super.write();
      tx = env.beginReadonlyTransaction();
      // cannot share Cursor, as there's no Cursor.getFirst() to reset methods
    }

    @TearDown(Trial)
    @Override
    public void teardown() throws Exception {
      tx.abort();
      super.teardown();
    }
  }

  @State(Benchmark)
  public static class Writer extends CommonXodus {

    @Setup(Invocation)
    @Override
    public void setup(BenchmarkParams b) throws Exception {
      super.setup(b);
    }

    @TearDown(Invocation)
    @Override
    public void teardown() throws Exception {
      super.teardown();
    }
  }

}
