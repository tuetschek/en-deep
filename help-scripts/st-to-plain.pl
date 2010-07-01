#!/usr/bin/perl

use strict;
use warnings;
use utf8;

binmode(STDIN, ":utf8");
binmode(STDOUT, ":utf8");

my $line_beg = 1;

while (my $line = <STDIN>){

    $line =~ s/\s+$//;

    if ($line =~ m/^$/){
        print("\n");
        $line_beg = 1;
        next;
    }
    my ($id, $word, $lemma, $plemma, $pos, $ppos);
    ($id, $word, $lemma, $plemma, $pos, $ppos) = split(/\s+/, $line);

    if ($line_beg){
        $line_beg = 0;
    }
    else {
        print(" ");
    }

    print($word . "^" . $ppos);
}
