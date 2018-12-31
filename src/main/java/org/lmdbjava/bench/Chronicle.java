/*-
 * #%L
 * LmdbJava Benchmarks
 * %%
 * Copyright (C) 2016 - 2019 The LmdbJava Open Source Project
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package org.lmdbjava.bench;

import java.io.File;
import java.io.IOException;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import net.openhft.chronicle.map.ChronicleMap;
import static net.openhft.chronicle.map.ChronicleMap.of;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
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
@SuppressWarnings({"checkstyle:javadoctype", "checkstyle:designforextension"})
public class Chronicle {

  // Chroncile Map does not provide ordered keys, so no CRC/XXH64/rev/prev test
  @Benchmark
  public void readKey(final Reader r, final Blackhole bh) {
    for (final int key : r.keys) {
      if (r.intKey) {
        r.wkb.putInt(0, key);
      } else {
        r.wkb.putStringWithoutLengthUtf8(0, r.padKey(key));
      }
      bh.consume(r.map.getUsing(r.wkb.byteArray(), r.wvb.byteArray()));
    }
  }

  @Benchmark
  public void write(final Writer w, final Blackhole bh) {
    w.write();
  }

  @State(value = Benchmark)
  @SuppressWarnings("checkstyle:visibilitymodifier")
  public static class CommonChroncileMap extends Common {

    ChronicleMap<byte[], byte[]> map;

    /**
     * Writable key buffer. Backed by a plain byte[] for Chroncile API ease.
     */
    MutableDirectBuffer wkb;

    /**
     * Writable value buffer. Backed by a plain byte[] for Chroncile API ease.
     */
    MutableDirectBuffer wvb;

    @Override
    public void setup(final BenchmarkParams b) throws IOException {
      super.setup(b);
      wkb = new UnsafeBuffer(new byte[keySize]);
      wvb = new UnsafeBuffer(new byte[valSize]);

      try {
        map = of(byte[].class, byte[].class)
            .constantKeySizeBySample(new byte[keySize])
            .constantValueSizeBySample(new byte[valSize])
            .entries(num)
            .createPersistedTo(new File(tmp, "chroncile.map"));
      } catch (final IOException ex) {
        throw new IllegalStateException(ex);
      }
    }

    @Override
    public void teardown() throws IOException {
      reportSpaceBeforeClose();
      map.close();
      super.teardown();
    }

    void write() {
      final int rndByteMax = RND_MB.length - valSize;
      int rndByteOffset = 0;
      for (final int key : keys) {
        if (intKey) {
          wkb.putInt(0, key, LITTLE_ENDIAN);
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
        map.put(wkb.byteArray(), wvb.byteArray());
      }
    }
  }

  @State(Benchmark)
  public static class Reader extends CommonChroncileMap {

    @Setup(Trial)
    @Override
    public void setup(final BenchmarkParams b) throws IOException {
      super.setup(b);
      super.write();
    }

    @TearDown(Trial)
    @Override
    public void teardown() throws IOException {
      super.teardown();
    }
  }

  @SuppressWarnings("checkstyle:javadoctype")
  @State(Benchmark)
  public static class Writer extends CommonChroncileMap {

    @Setup(Invocation)
    @Override
    public final void setup(final BenchmarkParams b) throws IOException {
      super.setup(b);
    }

    @TearDown(Invocation)
    @Override
    public final void teardown() throws IOException {
      super.teardown();
    }
  }

}
