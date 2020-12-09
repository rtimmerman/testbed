#!/usr/bin/perl
$shard_name = pop @ARGV;

while(<>) {
	$_ =~ s/SHARD_NAME/$shard_name/g;
	print $_;
}

