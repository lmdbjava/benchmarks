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

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.lmdb.LMDB;
import org.lwjgl.util.lmdb.MDBVal;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static java.util.concurrent.TimeUnit.*;
import static net.openhft.hashing.LongHashFunction.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.util.lmdb.LMDB.*;
import static org.openjdk.jmh.annotations.Level.*;
import static org.openjdk.jmh.annotations.Mode.*;
import static org.openjdk.jmh.annotations.Scope.*;

@OutputTimeUnit(MILLISECONDS)
@Fork(value = 1/*, jvmArgsAppend = "-Dorg.lwjgl.util.NoChecks=true"*/)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@BenchmarkMode(SampleTime)
public class LmdbLWJGL {

  @Benchmark
  public void readCrc(final Reader r, final Blackhole bh) throws Exception {
    try (MemoryStack stack = stackPush()) {
      MDBVal rwKey = MDBVal.mallocStack(stack);
      MDBVal rwVal = MDBVal.mallocStack(stack);

      r.crc.reset();
      int status = mdb_cursor_get(r.c, rwKey, rwVal, MDB_FIRST);
      while ( status != MDB_NOTFOUND ) {
        r.crc.update(rwKey.mv_data());
        r.crc.update(rwVal.mv_data());
        status = mdb_cursor_get(r.c, rwKey, rwVal, MDB_NEXT);
      }
      bh.consume(r.crc.getValue());
    }
  }

  @Benchmark
  public void readKey(final Reader r, final Blackhole bh) throws Exception {
    try (MemoryStack stack = stackPush()) {
      MDBVal rwKey = MDBVal.mallocStack(stack);
      MDBVal rwVal = MDBVal.mallocStack(stack);

      for ( final int key : r.keys ) {
        stack.push();
        if ( r.intKey ) {
          rwKey.mv_data(stack.malloc(4).putInt(0, key));
        } else {
          rwKey.mv_data(stack.ASCII(r.padKey(key), false));
        }
        bh.consume(mdb_cursor_get(r.c, rwKey, rwVal, MDB_SET_KEY));
        bh.consume(rwVal.mv_data());
        stack.pop();
      }
    }
  }

  @Benchmark
  public void readRev(final Reader r, final Blackhole bh) throws Exception {
    try (MemoryStack stack = stackPush()) {
      MDBVal key = MDBVal.mallocStack(stack);
      MDBVal val = MDBVal.mallocStack(stack);

      int status = mdb_cursor_get(r.c, key, val, MDB_LAST);
      while ( status != MDB_NOTFOUND ) {
        bh.consume(val.mv_data());
        status = mdb_cursor_get(r.c, key, val, MDB_PREV);
      }
    }

  }

  @Benchmark
  public void readSeq(final Reader r, final Blackhole bh) throws Exception {
    try (MemoryStack stack = stackPush()) {
      MDBVal key = MDBVal.mallocStack(stack);
      MDBVal val = MDBVal.mallocStack(stack);

      int status = mdb_cursor_get(r.c, key, val, MDB_FIRST);
      while ( status != MDB_NOTFOUND ) {
        bh.consume(val.mv_data());
        status = mdb_cursor_get(r.c, key, val, MDB_NEXT);
      }
    }
  }

  @Benchmark
  public void readXxh64(final Reader r, final Blackhole bh) throws Exception {
    try (MemoryStack stack = stackPush()) {
      MDBVal key = MDBVal.mallocStack(stack);
      MDBVal val = MDBVal.mallocStack(stack);

      long result = 0;

      int status = mdb_cursor_get(r.c, key, val, MDB_FIRST);
      while ( status != MDB_NOTFOUND ) {
        result += xx_r39().hashBytes(key.mv_data());
        result += xx_r39().hashBytes(val.mv_data());

        status = mdb_cursor_get(r.c, key, val, MDB_NEXT);
      }
      bh.consume(result);
    }
  }

  @Benchmark
  public void write(final Writer w, final Blackhole bh) throws Exception {
    w.write();
  }

  @State(Benchmark)
  public static class CommonLmdbLWJGL extends Common {

    private static final int POSIX_MODE = 0664;

    static void E(int rc) {
      if ( rc != MDB_SUCCESS )
        throw new IllegalStateException(mdb_strerror(rc));
    }

    private static int dbiFlags(final boolean intKey) {
      int flags;
      if ( intKey ) {
        flags = MDB_CREATE | MDB_INTEGERKEY;
      } else {
        flags = MDB_CREATE;
      }
      return flags;
    }

    private static int envFlags(final boolean writeMap, final boolean metaSync, final boolean sync) {
      int envFlags = 0;
      if ( writeMap ) {
        envFlags |= MDB_WRITEMAP;
      }
      if ( !sync ) {
        envFlags |= MDB_NOSYNC;
      }
      if ( !metaSync ) {
        envFlags |= MDB_NOMETASYNC;
      }
      return envFlags;
    }

    private static long mapSize(final int num, final int valSize) {
      return num * ((long)valSize) * 32L / 10L;
    }

    int db;
    long env;

    /**
     * Whether {@link LMDB#MDB_WRITEMAP} is used.
     */
    @Param({"true"})
    boolean writeMap;

    public void setup(final BenchmarkParams b, final boolean metaSync,
                      final boolean sync) throws Exception {
      super.setup(b);

      try (MemoryStack stack = stackPush()) {
        PointerBuffer pp = stack.mallocPointer(1);

        E(mdb_env_create(pp));
        env = pp.get(0);

        E(mdb_env_set_maxdbs(env, 1));
        E(mdb_env_set_maxreaders(env, 2));
        E(mdb_env_set_mapsize(env, mapSize(num, valSize)));

        // Open environment
        E(mdb_env_open(env, tmp.getPath(), envFlags(writeMap, metaSync, sync), POSIX_MODE));

        // Open database
        E(mdb_txn_begin(env, NULL, 0, pp));
        long txn = pp.get(0);

        IntBuffer ip = stack.mallocInt(1);
        E(mdb_dbi_open(txn, "db", dbiFlags(intKey), ip));
        db = ip.get(0);

        mdb_txn_commit(txn);
      }
    }

    @Override
    public void teardown() throws Exception {
      mdb_env_close(env);
      super.teardown();
    }

    void write() throws Exception {
      try (MemoryStack stack = stackPush()) {
        PointerBuffer pp = stack.mallocPointer(1);

        MDBVal rwKey = MDBVal.mallocStack(stack);
        MDBVal rwVal = MDBVal.mallocStack(stack);

        E(mdb_txn_begin(env, NULL, 0, pp));
        long tx = pp.get(0);

        E(mdb_cursor_open(tx, db, pp));
        long c = pp.get(0);

        final int flags = sequential ? MDB_APPEND : 0;
        final int rndByteMax = RND_MB.length - valSize;
        int rndByteOffset = 0;
        for ( final int key : keys ) {
          stack.push();
          if ( intKey ) {
            rwKey.mv_data(stack.malloc(4).putInt(0, key));
          } else {
            rwKey.mv_data(stack.ASCII(padKey(key), false));
          }
          if ( valRandom ) {
            ByteBuffer rnd = stack.malloc(valSize).put(RND_MB, rndByteOffset, valSize);
            rnd.flip();
            rwVal.mv_data(rnd);
            rndByteOffset += valSize;
            if ( rndByteOffset >= rndByteMax ) {
              rndByteOffset = 0;
            }
          } else {
            rwVal.mv_data(stack.malloc(valSize).putInt(0, key));
          }

          E(mdb_cursor_put(c, rwKey, rwVal, flags));
          stack.pop();
        }

        mdb_cursor_close(c);
        mdb_txn_commit(tx);
      }
    }

  }

  @State(Benchmark)
  public static class Reader extends CommonLmdbLWJGL {

    long txn;
    long c;

    @Setup(Trial)
    @Override
    public void setup(BenchmarkParams b) throws Exception {
      super.setup(b, false, false);
      super.write();

      try (MemoryStack stack = stackPush()) {
        PointerBuffer pp = stack.mallocPointer(1);

        E(mdb_txn_begin(env, NULL, MDB_RDONLY, pp));
        txn = pp.get(0);

        E(mdb_cursor_open(txn, db, pp));
        c = pp.get(0);
      }
    }

    @TearDown(Trial)
    @Override
    public void teardown() throws Exception {
      mdb_cursor_close(c);
      mdb_txn_abort(txn);
      super.teardown();
    }
  }

  @State(Benchmark)
  public static class Writer extends CommonLmdbLWJGL {

    /**
     * Whether {@link LMDB#MDB_NOMETASYNC} is used.
     */
    @Param({"false"})
    boolean metaSync;

    /**
     * Whether {@link LMDB#MDB_NOSYNC} is used.
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