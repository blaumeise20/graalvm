# How to trigger the segfault

(`mx` is required to be installed)

First, compile `Main.java` in this directory:
```
graalvm$ cd segfault-example
graalvm/segfault-example$ javac Main.java
```

Then, go into the `espresso` directory and build Espresso using these flags:
```
graalvm/segfault-example$ cd ../espresso
graalvm/espresso$ mx --env jvm-ce-llvm --native-images= build
```

Finally, run the `Main` class using Espresso:
```
graalvm/espresso$ mx --env jvm-ce-llvm espresso --experimental-options --engine.CompileImmediately -cp ../segfault-example Main
```

Enjoy your free segmentation fault :)