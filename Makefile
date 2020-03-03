
all: build lib

build:
	clj -m figwheel.main -O advanced -bo build
	chmod +x bin/cache-clean.js

lib:
	clj -m figwheel.main -O advanced -bo lib

debug-build:
	clj -m figwheel.main -O simple -bo build
	chmod +x bin/cache-clean.js

repl:
	clj -Abuild-dev
