#!/usr/bin/perl

use strict;
use warnings;

my @fields = (9, 11);
my $move = -1;


while (my $line = <STDIN>){
    if ($line =~ m/^$/){
        print($line);
        next;
    }
    my @line_f = split(/\t/, $line);
    for my $field (@fields){
        $line_f[$field + $move] = $line_f[$field];
    }
    print(join("\t", @line_f));
}
