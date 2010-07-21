#!/usr/bin/perl
#

use warnings;
use strict;

if (@ARGV != 2){
    die("Usage: ./data-stats.pl train.txt devel.txt");
}


my %train = %{read_stats($ARGV[0])};
my %test = %{read_stats($ARGV[1])};


my $sum = 0;
map { $sum += $train{$_}; } keys(%train);
print("TRAIN: " . scalar(keys(%train)) . " predicates, " . $sum . " occurrences.\n");
$sum = 0;
map { $sum += $test{$_}; } keys(%test);
print("TEST:  " . scalar(keys(%test)) . " predicates, " . $sum . " occurences.\n");

my $not_cv = 0; $sum = 0;
map { $not_cv++ if (!$train{$_}) } keys(%test);
map { $sum += $test{$_} if (!$train{$_}) } keys(%test);
print("NOT COVERED: " . $not_cv . " predicates, " . $sum . " occurrences.\n");

sub read_stats {
    my ($file) = @_;
    my %stats;
    open(IN, $file);
    my $cnt = 0;
    my $sent_cnt = 0;
    while(my $line = <IN>){
        $line =~ s/\r?\n$//;
        if ($line =~ /^$/){
            $sent_cnt++;
            next;
        }
        my @fields = split(/\t/, $line);

        if ($fields[13] ne "_"){ # there is a predicate
            my $pred = $fields[13] . lc(substr($fields[4],0,1));
            if ($stats{$pred}){
                $stats{$pred}++;
            }
            else {
                $stats{$pred} = 1;
            }
        }
        $cnt++;
    }
    print($file.": " . $cnt . " words in " . $sent_cnt . " sentences.\n");
    close(IN);
    return \%stats;
}
