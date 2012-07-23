#!/usr/bin/env perl
#
# greps the specified measure and number of instances for the given partial results
#

use strict;
use warnings;
use utf8;
use autodie;

my $measure = 'accuracy';

if (@ARGV && $ARGV[0] eq '-m'){
    $measure = $ARGV[1];
    shift;
    shift;
}

if ( !@ARGV ) {
    die("Usage: ./results-grep.pl [-m measure] resfile1 [resfile2 ...]");
}

my $total;
my $avg;

foreach my $result (@ARGV) {

    open( my $fh, "$result" );
    my @text = <$fh>;
    my ($fig) = grep { $_ =~ /^$measure:/ } @text;
    my $inst = $text[0];
    $inst =~ s/^N:([0-9]+).*\r?\n$/$1/;
    close($fh);
    $fig =~ s/^$measure://;
    $fig =~ s/\r?\n$//;
    
    $avg += ($fig * $inst);
    $total += $inst;
    print $result . "\t" . $inst . "\t" . sprintf("%0.3f", $fig * 100) . "\n";
}

$avg /= $total;
print "TOTAL:\t$total\t" . sprintf("%0.3f", $avg * 100) . "\n";
