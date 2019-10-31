
build:
	clj -m figwheel.main -O advanced -bo build
	chmod +x bin/cache-clean.js

debug-build:
	clj -m figwheel.main -O simple -bo build
	chmod +x bin/cache-clean.js

repl:
	clj -Abuild-dev
