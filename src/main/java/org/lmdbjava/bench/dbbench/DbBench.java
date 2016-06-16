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
import static java.nio.ByteBuffer.allocateDirect;
import java.security.SecureRandom;
import java.util.Random;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.IntHashSet;
import org.agrona.concurrent.UnsafeBuffer;
import org.lmdbjava.CursorB;
import static org.lmdbjava.CursorOp.MDB_SET_KEY;
import org.lmdbjava.Dbi;
import static org.lmdbjava.DbiFlags.MDB_CREATE;
import org.lmdbjava.Env;
import org.lmdbjava.MutableDirectBufferVal;
import static org.lmdbjava.MutableDirectBufferVal.forMdb;

import org.lmdbjava.Txn;
import static org.lmdbjava.TxnFlags.MDB_RDONLY;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import static org.openjdk.jmh.annotations.Level.Iteration;
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
@Warmup(iterations = 2)
@Measurement(iterations = 5)
@BenchmarkMode(SampleTime)
public class DbBench {

  // inspired by db_bench_mdb, but with simplifications to simplify JMH use
  private static final int POSIX_MODE = 0664;
  private static final Random RND = new SecureRandom();

  static {
   // setProperty(DISABLE_CHECKS_PROP, TRUE.toString());
  }
  
  @Benchmark
  public void readByEachKey(Reader r, Blackhole bh) throws Exception {
    try (final Txn tx = new Txn(r.env, MDB_RDONLY)) {
      final CursorB c = r.db.openCursorB(tx);
      for (final int key : r.keys) {
        r.wkb.putInt(0, key);
        bh.consume(c.get(r.wkv, r.rkv, MDB_SET_KEY));
        // no need to re-wrap, as we never change buffer (auto-reset:off)
      }
      c.close();
    }
  }

  @State(Benchmark)
  public static class Reader {

    File tmp;
    Dbi db;
    Env env;
    int[] keys;

    @Param({"1000000"})
    int num;

    @Param({"false", "true"})
    boolean random; // key order

    @Param({"100"})
    int size;

    // field names: (r[read]|w[rite])(k[ey]|v[al])(b[uffer]|v[val])
    MutableDirectBuffer rkb;
    MutableDirectBufferVal rkv;
    MutableDirectBuffer rvb;
    MutableDirectBufferVal rvv;
    MutableDirectBuffer wkb;
    MutableDirectBufferVal wkv;
    MutableDirectBuffer wvb;
    MutableDirectBufferVal wvv;

    @Setup(value = Iteration)
    public void setup() throws Exception {
      rkb = new UnsafeBuffer(allocateDirect(0)); // ok as read-only buff
      rvb = new UnsafeBuffer(allocateDirect(0)); // ok as read-only buff
      wkb = new UnsafeBuffer(allocateDirect(4));
      wvb = new UnsafeBuffer(allocateDirect(size));
      rkv = forMdb(rkb, false);
      rvv = forMdb(rvb, false);
      wkv = forMdb(wkb, false);
      wvv = forMdb(wvb, false);

      final IntHashSet set = new IntHashSet(num, Integer.MIN_VALUE);
      keys = new int[num];
      for (int i = 0; i < num; i++) {
        if (random) {
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
        } else {
          keys[i] = i;
        }
      }

      final String tmpParent =  System.getProperty("java.io.tmpdir");
      tmp = new File(tmpParent, "jmh-bench-" + RND.nextInt());
      tmp.mkdirs();
      env = new Env();
      env.setMapSize(num * size * 128L);
      env.setMaxDbs(1);
      env.setMaxReaders(1);
      env.open(tmp, POSIX_MODE);

      try (final Txn tx = new Txn(env)) {
        db = new Dbi(tx, "db", MDB_CREATE);
        tx.commit();
      }

      try (final Txn tx = new Txn(env)) {
        final CursorB c = db.openCursorB(tx);
        for (final int key : keys) {
          wkb.putInt(0, key);
          wvb.putInt(0, key); // db_bench_mdb slices from 1048576 bytes rnd buff
          c.put(wkv, wvv);
        }
        c.close();
        tx.commit();
      }
    }

    @TearDown(Level.Iteration)
    public void teardown() {
      env.close();
      for (final File f : tmp.listFiles()) {
        f.delete();
      }
      tmp.delete();
    }
  }
}
