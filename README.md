[![License](https://img.shields.io/hexpm/l/plug.svg?maxAge=2592000)](http://www.apache.org/licenses/LICENSE-2.0.txt)
![Size](https://reposs.herokuapp.com/?path=lmdbjava/benchmarks)

# LmdbJava Benchmarks

This is a [JMH](http://openjdk.java.net/projects/code-tools/jmh/) benchmark
of open source, embedded, memory-mapped, key-value stores available from Java:

* [LmdbJava](https://github.com/lmdbjava/lmdbjava) (with fast `ByteBuffer`, safe
  `ByteBuffer` and an [Agrona](https://github.com/real-logic/Agrona) buffer)
* [LMDBJNI](https://github.com/deephacks/lmdbjni)
* [LevelDBJNI](https://github.com/fusesource/leveldbjni)
* [RocksDB](http://rocksdb.org/)
* [MVStore](http://h2database.com/html/mvstore.html) (pure Java)
* [MapDB](http://www.mapdb.org/) (pure Java)
* [Chroncile Map](https://github.com/OpenHFT/Chronicle-Map) (pure Java) (**)

(**) does not support ordered keys, so iteration benchmarks not performed

The benchmark itself is adapted from LMDB's
[db_bench_mdb.cc](http://lmdb.tech/bench/microbench/db_bench_mdb.cc), which in
turn is adapted from
[LevelDB's benchmark](https://github.com/google/leveldb/blob/master/db/db_bench.cc).

The benchmark includes:

* Writing data
* Reading all data via each key
* Reading all data via a reverse iterator
* Reading all data via a forward iterator
* Reading all data via a forward iterator and computing a CRC32

Byte arrays (`byte[]`) are always used for the keys and values, avoiding any
serialization library overhead. For those libraries that support compression,
it is disabled in the benchmark. In general any special library features that
decrease latency (eg batch modes, disable auto-commit, disable journals,
hint at expected data sizes etc) were used. While we have tried to be fair and
consistent, some libraries offer non-obvious tuning settings or usage patterns
that might further reduce their latency. We do not claim we have exhausted
every tuning option every library exposes, but pull requests are most welcome.

## Usage

1. Install `liblmdb` for your platform (eg Arch Linux: `pacman -S lmdb`)
2. Clone this repository and `mvn clean package`
3. Run the benchmark with `java -jar target/benchmarks.jar`

You can append ``-h`` to the ``java -jar`` line for JMH help. For example, use:

  * ``-wi 0`` to run zero warm-ups (not recommended)
  * ``-i 1`` to run one iteration only (not recommended)
  * ``-f 3`` to run three forks for smaller error ranges (recommended)
  * ``-p num=100,1000`` to test maps with only 100 and 1000 elements
  * ``-p intKey=true`` to test with only integer-based keys
  * ``-lp`` to list all available parameters
  * ``-rf csv`` to emit CSV output
  * ``-foe true`` to stop on any error (recommended)

Collectively the various parameters (available from `-lp`) allow you to create
workloads of different iteration counts (`num`), key sizes and layout (`intKey`),
value sizes (`valSize`), mechanical sympathy (`sequential`, `valRandom`) and
library feature fine-tuning (eg `forceSafe`, `writeMap` etc).

If you're mainly interested in a quick run that compares the mechanically
sympathetic performance of the libraries, the following is suggested:

    java -jar target/benchmarks.jar -p intKey=true \
                                    -p sequential=true \
                                    -p valRandom=false \
                                    -p forceSafe=false \
                                    -foe true -rf csv

Keep an eye on your temporary file system's `jmh-bench-*` directory. This will
reveal the key-value on-disk size. You can change the `num` and/or `valSize`
parameters if you'd like different on-disk sizes. If you wish to test with an
another file system, just use `-Djava.io.tmpdir=/somewhere/you/like`.

## Support

Please [open a GitHub issue](https://github.com/lmdbjava/benchmarks/issues)
if you have any questions.

## Contributing

Contributions are welcome! Please see the LmdbJava project's
[Contributing Guidelines](https://github.com/lmdbjava/lmdbjava/blob/master/CONTRIBUTING.md).
We have tried to be consistent and reasonable in the use of third-party
libraries, but we do not claim to be experts in them. Any enhancements which
better use those libraries are particularly welcome.

## License

This project is licensed under the
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).
