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

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Random;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.zip.CRC32;

import static org.lmdbjava.bench.LmdbJava.LMDBJAVA;
import static org.lmdbjava.bench.LmdbJni.LMDBJNI;

import org.lmdbjava.LmdbException;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import static org.openjdk.jmh.annotations.Level.Iteration;
import org.openjdk.jmh.annotations.Measurement;
import static org.openjdk.jmh.annotations.Mode.AverageTime;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import static org.openjdk.jmh.annotations.Scope.Thread;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Thread)
@OutputTimeUnit(MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = SECONDS)
@BenchmarkMode(AverageTime)
public class StoreBenchmark {

  private static final CRC32 CRC = new CRC32();

  private static final Random RND = new SecureRandom();

  @Param({"false"})
  private boolean random;

  @Param({"15000", "30000"})
  private long size;

  @Param(value = {LMDBJAVA, LMDBJNI})
  private String store;

  private AbstractStore target;

  private byte[] valByteRnd;

  @Param({"512"})
  private int valBytes;

  @Benchmark
  public void quickTest(Blackhole bh) throws Exception {
    CRC.reset();
    target.startWritePhase();
    for (long i = 0; i < size; i++) {
      target.key.clear();
      target.key.putLong(i);
      target.key.flip();
      CRC.update(target.key);
      target.key.flip();

      if (random) {
        RND.nextBytes(valByteRnd);
      }
      target.val.clear();
      target.val.put(valByteRnd);
      target.val.flip();
      CRC.update(target.val);
      target.val.flip();

      target.put();
    }
    final long crcWrites = CRC.getValue();
    bh.consume(crcWrites);

    CRC.reset();
    target.startReadPhase();
    for (int i = 0; i < size; i++) {
      target.key.clear();
      target.key.putLong(i);
      target.key.flip();

      target.val.clear();

      target.get();
      CRC.update(target.key);
      CRC.update(target.val);
    }
    final long crcReads = CRC.getValue();
    bh.consume(crcReads);
    if (crcReads != crcWrites) {
      throw new IllegalStateException();
    }

    target.CRC.reset();
    target.crc32();
    final long crcSequential = target.CRC.getValue();
    if (crcSequential != crcWrites) {
      throw new IllegalStateException();
    }
    bh.consume(crcSequential);
    target.finishCrcPhase();
  }

  @Setup(value = Iteration)
  public void setup() throws IOException, LmdbException {
    this.target = AbstractStore.create(store, Long.BYTES, valBytes);
    this.valByteRnd = new byte[valBytes];
  }
}
