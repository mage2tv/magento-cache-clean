#!/usr/bin/env php
<?php declare(strict_types=1);

use function array_values as values;
use function array_map as map;

$magento_basedir = realpath($argv[1] ?? '.');

$magento_env = $magento_basedir . '/app/etc/env.php';
$composer_autoload = $magento_basedir . '/vendor/autoload.php';
$output_file = $magento_basedir . '/var/cache-clean-config.json';

if (in_array($magento_basedir, ['-h', '--help'], true)) {
    echo "Usage: {$argv[0]} [path/to/magento]\n";
    exit(1);
}

if (! file_exists($magento_env)) {
    fwrite(STDERR, "[ERROR] Unable to find configuration \"$magento_env\"\n");
    exit(1);
}

if (! file_exists($composer_autoload)) {
    fwrite(STDERR, "[ERROR] Unable to find composer autoload file \"$composer_autoload\"\n");
    exit(1);
}

require $composer_autoload;

$registrar = new \Magento\Framework\Component\ComponentRegistrar();

$relativize = function (string $path) use ($magento_basedir) {
    return substr($path, strlen($magento_basedir) + 1);
};

$config = [
   'app' => require $magento_env,
   'modules' => map($relativize, values($registrar->getPaths('module'))),
   'themes' => map($relativize, values($registrar->getPaths('theme'))),
];

if (! is_dir(dirname($output_file))) {
    mkdir(dirname($output_file), 0777, true);
}

file_put_contents(
    $output_file,
    json_encode($config, JSON_PRETTY_PRINT|JSON_UNESCAPED_SLASHES) . PHP_EOL
);

echo "Wrote configuration to $output_file\n";
