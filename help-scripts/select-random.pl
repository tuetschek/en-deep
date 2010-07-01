#!/usr/bin/perl

use strict;
use warnings;
use File::Copy;

my $pre_train = "train-";
my $pre_devel = "devel-";
my $ext = ".arff";

if (@ARGV != 3){
    die("Usage: ./select-random.pl N dir1 dir2");
}
my ($sample_size, $source_dir, $target_dir) = @ARGV;

opendir(DIR, $source_dir);
my @files = readdir(DIR);
closedir(DIR);
my %samples;

@files = sort {$a cmp $b} @files;

for my $file (@files){
    if ($file =~ m/^$pre_train(.*)$ext$/){
        
        my $match = $1;
        
        if (bsearch($pre_devel.$match.$ext, \@files) != -1){
            $samples{$match} = 1;
        }
    }
}

my @sample_keys = sort {$a cmp $b} keys(%samples);

if (@sample_keys < $sample_size){
    die("Not enough samples");
}
for (my $i = 0; $i < $sample_size; ++$i){

    my $selected = int(rand(@sample_keys));
    my $file = splice(@sample_keys, $selected, 1);

    print("Copying $pre_train$file$ext and $pre_devel$file$ext ...\n");
    copy($source_dir."/".$pre_train.$file.$ext, $target_dir."/".$pre_train.$file.$ext);
    copy($source_dir."/".$pre_devel.$file.$ext, $target_dir."/".$pre_devel.$file.$ext);
}

sub bsearch {
    my ($x, $a) = @_;            # search for x in array a
    my ($l, $u) = (0, @$a - 1);  # lower, upper end of search interval
    my $i;                       # index of probe
    while ($l <= $u) {
        $i = int(($l + $u)/2);
        
        if ($a->[$i] lt $x) {
            $l = $i+1;
        }
        elsif ($a->[$i] gt $x) {
            $u = $i-1;
        }
        else {
            return $i; # found
        }
    }
    return -1;         # not found
}




