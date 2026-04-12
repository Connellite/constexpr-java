# constexpr-java-17

This is a fork of [constexpr-java](https://github.com/junkdog/constexpr-java) by junkdog, updated to Java 17 and ASM 9.x.

Maven coordinates are `io.github.connellite` with runtime API in package `io.github.connellite.constexpr`. See [NOTICE](NOTICE) for attribution.

## `@ConstExpr`

Simulates `constexpr` from C++11, build-time code-execution, from
[cppreference.com][cppref]:

> The constexpr specifier declares that it is possible
> to evaluate the value of the function or variable at
> compile time.


#### But Why?
- embed build-time variables directly into class files
- naive string obfuscation
- maven's resource filtering can be clunky for certain use-cases


## Usage

The API is restricted to the `@ConstExpr` annotation. The maven plugin does
its thing using [ASM](https://asm.ow2.io/).


#### On Fields
Fields must satisfy the following prerequisiites to be eligible:
- `static final` modifiers
- primitive value or string.

The final value is written to the transformed class.


#### On Methods
In contrast to C++1x, `@ConstExpr` on static methods turn them into
build-time only methods. Consequently, do not annotate any methods required
during runtime.


#### Sample

```java
import io.github.connellite.constexpr.ConstExpr;

public class Exhibit {
    @ConstExpr // recording the time at build
    public static final long timestamp = System.currentTimeMillis();

    @ConstExpr
    public static final long seed = generateSeed();

    @ConstExpr // this method will be completely removed
    private static int generateSeed() {
        String s = "hellooooo";
        int sum = 0;
        for (char c : s.toCharArray())
            sum += c;
        return new Random(sum).nextInt();
    }
}
```

#### Usage: Maven

```xml
<dependencies>
    <dependency>
        <groupId>io.github.connellite</groupId>
        <artifactId>constexpr-api</artifactId>
        <version>0.2.0</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>io.github.connellite</groupId>
            <artifactId>constexpr-maven-plugin</artifactId>
            <version>0.2.0</version>
            <executions>
                <execution>
                    <id>transform</id>
                    <goals>
                        <goal>constexpr</goal>
                    </goals>
                </execution>
            </executions>
            <!-- default configuration -->
            <configuration>
                <!-- property: constexpr.skip -->
                <skip>false</skip>
                <!-- property: constexpr.verbose -->
                <verbose>false</verbose>
            </configuration>
        </plugin>
    </plugins>
</build>
```

Running the plugin should result in output similar to:

```
[INFO] --- constexpr-maven-plugin:0.2.0:constexpr (transform) @ map ---
[INFO] Scanned 499 classes ............................................... 86ms
[INFO] Transformed 1 classes ............................................. 46ms
[INFO]
[INFO] @ConstExpr Log
[INFO] ------------------------------------------------------------------------
[INFO] s.f.m.u.BuildProperties ....................................... fields:1
[INFO] ------------------------------------------------------------------------
[INFO]
```

#### Publishing / releasing

- **Maven Central**: `mvn -Prelease clean deploy` with `~/.m2/settings.xml` server `central` (Sonatype token) and GPG, or the **Publish Maven Central** workflow (`.github/workflows/publish-ossrh.yml`) on release.
- **GitHub Packages**: `mvn -Pgithub-packages clean deploy` or workflow **Publish GitHub Packages**.

#### The Details
- scan class files for `@ConstExpr` and validate
- for each annotated type:
  - remove annotation
  - resolve the value of each `@ConstExpr` field via reflection
  - write the result to the field signature
  - remove old field initialization bytecode from the static initializer
  - remove any methods annotated with `@ConstExpr`
- if static initializer is left empty, remove it completely
- write transformed `byte[]` to source class file
  - (entries no longer required by the _constant pool_ are cleared)

#### Bytecode: before and after
- Bytecode disassembly of [PlainString.java][ps-java], [diff view (upstream)][diff].


 [cppref]: http://en.cppreference.com/w/cpp/language/constexpr
 [ps-java]: https://github.com/connellite/constexpr-java/blob/master/core/src/test/java/io/github/connellite/constexpr/model/PlainString.java
 [diff]: https://github.com/junkdog/constexpr-java/compare/reference-before...reference-after#diff-f98d296c19afdc978656a8813c42be81
