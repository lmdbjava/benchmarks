/*
 * Copyright © 2016-2025 The LmdbJava Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lmdbjava.bench;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.ByteBuffer;

import static java.nio.ByteBuffer.allocateDirect;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.lmdbjava.ByteBufferProxy.PROXY_OPTIMAL;
import static org.lmdbjava.ByteBufferProxy.PROXY_SAFE;
import static org.openjdk.jmh.annotations.Level.Trial;
import static org.openjdk.jmh.annotations.Mode.SampleTime;
import static org.openjdk.jmh.annotations.Scope.Benchmark;

@OutputTimeUnit(MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = "-Dlmdbjava.disable.checks=true")
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@BenchmarkMode(SampleTime)

public class LmdbJavaCursorIterable {

    @Benchmark
    public void all(final Reader r, final Blackhole bh) {
        test(r, bh, KeyRange.all());
    }

    @Benchmark
    public void allBackward(final Reader r, final Blackhole bh) {
        test(r, bh, KeyRange.allBackward());
    }

    @Benchmark
    public void atLeast(final Reader r, final Blackhole bh) {
        test(r, bh, KeyRange.atLeast(r.minKey));
    }

    @Benchmark
    public void atLeastBackward(final Reader r, final Blackhole bh) {
        test(r, bh, KeyRange.atLeastBackward(r.maxKey));
    }

    @Benchmark
    public void atMost(final Reader r, final Blackhole bh) {
        test(r, bh, KeyRange.atMost(r.maxKey));
    }

    @Benchmark
    public void atMostBackward(final Reader r, final Blackhole bh) {
        test(r, bh, KeyRange.atMostBackward(r.minKey));
    }

    @Benchmark
    public void closed(final Reader r, final Blackhole bh) {
        test(r, bh, KeyRange.closed(r.minKey, r.maxKey));
    }

    @Benchmark
    public void closedBackward(final Reader r, final Blackhole bh) {
        test(r, bh, KeyRange.closedBackward(r.maxKey, r.minKey));
    }

    @Benchmark
    public void closedOpen(final Reader r, final Blackhole bh) {
        test(r, bh, KeyRange.closedOpen(r.minKey, r.maxKey));
    }

    @Benchmark
    public void closedOpenBackward(final Reader r, final Blackhole bh) {
        test(r, bh, KeyRange.closedOpenBackward(r.maxKey, r.minKey));
    }

    @Benchmark
    public void greaterThan(final Reader r, final Blackhole bh) {
        test(r, bh, KeyRange.greaterThan(r.minKey));
    }

    @Benchmark
    public void greaterThanBackward(final Reader r, final Blackhole bh) {
        test(r, bh, KeyRange.greaterThanBackward(r.maxKey));
    }

    @Benchmark
    public void lessThan(final Reader r, final Blackhole bh) {
        test(r, bh, KeyRange.lessThan(r.maxKey));
    }

    @Benchmark
    public void lessThanBackward(final Reader r, final Blackhole bh) {
        test(r, bh, KeyRange.lessThanBackward(r.minKey));
    }

    @Benchmark
    public void open(final Reader r, final Blackhole bh) {
        test(r, bh, KeyRange.open(r.minKey, r.maxKey));
    }

    @Benchmark
    public void openBackward(final Reader r, final Blackhole bh) {
        test(r, bh, KeyRange.openBackward(r.maxKey, r.minKey));
    }

    @Benchmark
    public void openClosed(final Reader r, final Blackhole bh) {
        test(r, bh, KeyRange.openClosed(r.minKey, r.maxKey));
    }

    @Benchmark
    public void openClosedBackward(final Reader r, final Blackhole bh) {
        test(r, bh, KeyRange.openClosedBackward(r.maxKey, r.minKey));
    }

    private void test(final Reader r, final Blackhole bh, final KeyRange<ByteBuffer> keyRange) {
        try (final CursorIterable<ByteBuffer> cursorIterable = r.db.iterate(r.txn, keyRange)) {
            for (final KeyVal<ByteBuffer> kv : cursorIterable) {
                bh.consume(kv.key());
                bh.consume(kv.val());
            }
        }
    }

    @State(Benchmark)
    public static class LmdbJava extends CommonLmdbJava<ByteBuffer> {

        ByteBuffer rwKey;
        ByteBuffer rwVal;

        @Override
        public void setup(final BenchmarkParams b, final boolean sync) throws
                IOException {
            super.setup(b, sync);
            rwKey = allocateDirect(Integer.BYTES * 2);
            rwVal = allocateDirect(Long.BYTES);
        }

        void write() {
            try (Txn<ByteBuffer> tx = env.txnWrite()) {
                for (int i = 1; i <= num; i++) {
                    rwKey.putInt(i);
                    rwKey.flip();
                    rwVal.putInt(i);
                    rwVal.flip();
                    db.put(tx, rwKey, rwVal);
                }
                tx.commit();
            }
        }
    }

    @State(Benchmark)
    public static class Reader extends LmdbJava {

        /**
         * Whether the byte buffer accessor is safe or not.
         */
        @Param("false")
        boolean forceSafe;
        Txn<ByteBuffer> txn;

        ByteBuffer minKey;
        ByteBuffer maxKey;

        @Setup(Trial)
        @Override
        public void setup(final BenchmarkParams b) throws IOException {
            bufferProxy = forceSafe
                    ? PROXY_SAFE
                    : PROXY_OPTIMAL;
            super.setup(b, false);

            minKey = ByteBuffer.allocateDirect(Integer.BYTES);
            minKey.putInt(1);
            minKey.flip();
            maxKey = ByteBuffer.allocateDirect(Integer.BYTES);
            maxKey.putInt(num);
            maxKey.flip();

            super.write();
            txn = env.txnRead();

        }

        @TearDown(Trial)
        @Override
        public void teardown() throws IOException {
            txn.abort();
            super.teardown();
        }
    }
}
