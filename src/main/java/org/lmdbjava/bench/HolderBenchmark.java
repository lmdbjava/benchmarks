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
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import static org.openjdk.jmh.annotations.Mode.Throughput;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import static org.openjdk.jmh.annotations.Scope.Benchmark;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@OutputTimeUnit(MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 3, time = 5)
@State(Benchmark)
@BenchmarkMode(Throughput)
public class HolderBenchmark {

  private Holder<ByteBuffer> h1;
  private HolderByteBuffer h2;
  private final ByteBuffer input = allocateDirect(100);

  @Benchmark
  public void nonParameterized(final Blackhole bh) throws Exception {
    bh.consume(h2.get());
  }

  @Benchmark
  public void parameterized(final Blackhole bh) throws Exception {
    bh.consume(h1.get());
  }

  @Setup
  public void setup() {
    h1 = new Holder(input);
    h2 = new HolderByteBuffer(input);
  }

}
