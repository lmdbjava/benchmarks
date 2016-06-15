package org.lmdbjava.bench;

import static java.lang.Long.BYTES;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;


import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.lmdbjava.bench.LmdbJava.LMDBJAVA;
import static org.lmdbjava.bench.LmdbJavaB.LMDBJAVAB;
import static org.lmdbjava.bench.LmdbJni.LMDBJNI;
import static org.openjdk.jmh.annotations.Level.Iteration;
import static org.openjdk.jmh.annotations.Mode.AverageTime;
import static org.openjdk.jmh.annotations.Scope.Thread;

@State(Thread)
@OutputTimeUnit(NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = SECONDS)
@BenchmarkMode(AverageTime)
public class BasicOpsBenchmark {

  @Param(value = {LMDBJAVA, LMDBJAVAB, LMDBJNI})
  private String store;

  private AbstractStore target;

  private byte[] valByteRnd;

  static final long KEY = 12345;
  static final long VAL = 67890;

  @Setup(value = Iteration)
  public void setup() throws Exception {
    this.target = AbstractStore.create(store, BYTES, BYTES);
    this.target.key.putLong(KEY).flip();
    this.target.val.putLong(VAL).flip();
    this.target.startWritePhase();
    this.target.put();
    this.target.startReadPhase();
  }

  @Benchmark
  public void get(Blackhole bh) throws Exception {
    this.target.get();
    // key not checked as get does an exact match (so we know the key already)
    final long v = this.target.roVal.getLong();
    if (v != VAL) {
      throw new IllegalStateException("v:" + v);
    }
    bh.consume(v);
  }

  @Benchmark
  public void cursorGetFirst(Blackhole bh) throws Exception {
    this.target.cursorGetFirst();
  }
}
