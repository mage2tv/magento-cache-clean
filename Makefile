
all: build lib

build:
	clj -m figwheel.main -O advanced -bo build
	chmod +x bin/cache-clean.js

lib:
	clj -m figwheel.main -O advanced -bo lib

debug-build:
	clj -m figwheel.main -O simple -bo build
	chmod +x bin/cache-clean.js

install-dev-dir:
	rsync -a ./ $(HOME)/.composer/vendor/mage2tv/magento-cache-clean/

install-debug: debug-build install-dev-dir

repl:
	clj -Abuild-dev
