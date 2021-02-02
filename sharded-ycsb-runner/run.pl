#!/usr/bin/perl

package main;

sub clear_db
{
}

sub run_experiment
{
}

$repeats = 1;
$wait = 1;

for $arg (@ARGV) {
	$repeats = $1 if ($arg =~ /--repeats=(\d+)$/);
	$wait = $1 if ($arg =~ /--wait=(\d+)$/);
}

for ($i = 0; $i < $repeats; ++$i) {
	&::clear_db();
	&::run_experiment();
	sleep $wait;
}
1;
