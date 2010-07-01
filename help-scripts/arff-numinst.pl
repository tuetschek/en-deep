#!/usr/bin/perl

use warnings;
use strict;


# process all files from the args
while (@ARGV > 0){
    my $file = shift;
    stats($file);
}


# number of instances in one ARFF file
sub stats {
    my ($file) = @_;

    open(IN, $file);
    binmode(IN, ":utf8");
    
    # relation name
    print($file. ": ");

    # skip all attributes
    my $line;
    my %sent_ids;
    while (defined($line = <IN>) && $line !~ m/^\@data.*/i ){
    }
    my $inst = 0;
    while (defined($line = <IN>)){
        my ($sent_id) = split(/,/, $line);
        $sent_ids{$sent_id} = 1;
        $inst++;
    }
    print("$inst instances, " . scalar(keys(%sent_ids)) . " sentences.\n");
    close(IN);
}
