#!/usr/bin/perl
$shard_name = pop @ARGV;

package main;

sub readfile {
	$filename = shift;

	open IN, "<$filename";
	while (<IN>) {
		print " " x 4 . $_;
	}

	close IN;

	return $content;
}

while(<>) {
	$_ =~ s/SHARD_NAME/$shard_name/g;
	$_ =~ s/%extract%(.+?)%/&::readfile($1)/eg;
	print $_;
}

1;