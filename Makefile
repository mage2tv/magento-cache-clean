
build:
	clj -m figwheel.main -O advanced -bo build
	chmod +x bin/cache-clean.js

repl:
	clj -Abuild-dev
