#!/usr/bin/env perl
#
# lists results in a nice Dokuwiki table format
#

use strict;
use warnings;
use utf8;
use autodie;
use List::Util qw(max);
use Date::Format;

my (@result_files) = ( 'sum-nofeatsel-stats.txt', 'sum-best-stats.txt' );
my $measure = 'accuracy';

if ( !@ARGV ) {
    die("Usage: ./results-table.pl experiment1 experiment2");
}

foreach my $experiment (@ARGV) {

    my $date = 0;
    my @results;

    foreach my $result (@result_files) {

        open( my $fh, "$experiment/$result" );
        $date = max( (stat($fh))[9], $date );    # file modification time
        my ($fig) = grep { $_ =~ /^$measure:/ } <$fh>;
        close($fh);
        $fig =~ s/^$measure://;
        $fig =~ s/\r?\n$//;
        push @results, sprintf("%0.3f", $fig * 100);
    }

    print "| $experiment | " . time2str( '%Y-%m-%d %H:%M', $date ) . " | ";
    print join " | ", @results;
    print " |\n";
}
