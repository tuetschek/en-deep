#!/usr/bin/perl
#
# Count number of senses for each predicate


use strict;
use warnings;

if (@ARGV < 1){
    die("Usage: ./num-senses.pl file...");
}

my @senses;

while (@ARGV){
    my $file = shift;
    open(IN, $file);
    while (my $line = <IN>){
        if ($line =~ m/^\@attribute pred/i){
            my $count = ($line =~ tr/,//) + 1;
            if (!$senses[$count]){
                $senses[$count] = [];
            }
            push(@{$senses[$count]}, $file);
            last;
        }
    }
    close(IN);
}

for (my $i = 0;$i < @senses; ++$i){
    if ($senses[$i]){
        print($i ." -- \t".scalar(@{$senses[$i]})." (".join(" ", @{$senses[$i]}).")\n");
    }
}
