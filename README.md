[![License](https://img.shields.io/hexpm/l/plug.svg?maxAge=2592000)](http://www.apache.org/licenses/LICENSE-2.0.txt)
![Size](https://reposs.herokuapp.com/?path=lmdbjava/benchmarks)

# LmdbJava Benchmarks

This is a [JMH](http://openjdk.java.net/projects/code-tools/jmh/) benchmark
of open source embedded key-value stores available for use from Java.

## Usage

1. Install `liblmdb` for your platform (eg Arch Linux: `pacman -S lmdb`)
2. Clone this repository and `mvn clean package`
3. Run the benchmark with `java -jar target/benchmarks.jar -foe true`

## Support

Please [open a GitHub issue](https://github.com/lmdbjava/benchmarks/issues)
if you have any questions.

## Contributing

Contributions are welcome! Please see the LmdbJava project's
[Contributing Guidelines](https://github.com/lmdbjava/lmdbjava/blob/master/CONTRIBUTING.md)

## License

This project is licensed under the
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).
