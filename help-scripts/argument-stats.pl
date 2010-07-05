#!/usr/bin/perl
#
# Statistics about arguments (POS, DEPREL) to be used for pruning
#

use strict;
use warnings;

if (@ARGV != 1){
    die("Usage: ./argument-arg_stats.pl st-file");
}

my %arg_stats;
my %field_stats;
my $field_no = 4; # field for which the arg_stats are made
my $ctr = 0;

open(IN, $ARGV[0]);
while (my $line = <IN>){

    $line =~ s/\r?\n$//;
    if ($line eq ""){
        next;
    }

    my @fields = split(/\t/, $line);
    my @args = @fields[14 .. scalar(@fields)];

    my $key = $fields[$field_no];
    if (!$field_stats{$key}){
        $field_stats{$key} = 1;
    }
    else {
        $field_stats{$key}++;
    }

    foreach my $arg (@args){
        if ($arg && $arg ne "_"){

            if (!$arg_stats{$key}){
                $arg_stats{$key} = 1;
            }
            else {
                $arg_stats{$key}++;
            }
        }
    }

    $ctr++;
    if ($ctr % 10000 == 0){
        print(STDERR ".");
    }
}
print(STDERR "\n\n");
close(IN);

foreach my $key (sort { $a cmp $b } keys(%field_stats)){
    if (!$arg_stats{$key}){
        $arg_stats{$key} = 0;
    }
    print($key . ": " . $arg_stats{$key} . " / " . $field_stats{$key} . "\n");
}
