## Building

The tool is written in ClojureScript.
To build, run install Clojure 1.9 or later (e.g. `brew install clojure`) and run

```
$ make
```

This will compile the `bin/cache-clean.js` script (intended to be run as a node script) and the `export/watcher.js` file (intended to be consumed from node in custom dev task runner).

Check the `Makefile` for more details.
