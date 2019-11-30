## Building

The tool is written in ClojureScript.
To build, install Clojure 1.9 or later (e.g. `brew install clojure`) and run

```shell
$ clj -m figwheel.main -O advanced -bo build
$ chmod +x bin/cache-clean.js
```

Or, install Clojure and simply run `make`.
